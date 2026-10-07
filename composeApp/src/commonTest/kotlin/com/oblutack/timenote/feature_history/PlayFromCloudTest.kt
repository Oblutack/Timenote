package com.oblutack.timenote.feature_history

import com.oblutack.timenote.core.AudioFiles
import com.oblutack.timenote.core.audioFileName
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.drive.RemoteException
import com.oblutack.timenote.feature_history.presentation.HistoryViewModel
import com.oblutack.timenote.sync.AudioFetcher
import com.oblutack.timenote.sync.FetchResult
import com.oblutack.timenote.testutil.FakeAudioPlayer
import com.oblutack.timenote.testutil.FakeAudioRecorder
import com.oblutack.timenote.testutil.FakeDefaultTagsState
import com.oblutack.timenote.testutil.FakeDeviceId
import com.oblutack.timenote.testutil.FakeTimenoteDao
import com.oblutack.timenote.testutil.runAppTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Files "on this device": a set of names. */
private class FakeFiles(val present: MutableSet<String> = mutableSetOf()) : AudioFiles {
    override fun toRef(path: String) = audioFileName(path)
    override fun resolve(ref: String) = "/fake/${audioFileName(ref)}"
    override fun isPresent(ref: String) = audioFileName(ref) in present
}

private class FakeFetcher(var result: FetchResult = FetchResult.Ready, private val files: FakeFiles) : AudioFetcher {
    val fetched = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun fetch(name: String, onProgress: (Long, Long) -> Unit): FetchResult {
        fetched += name
        gate?.await()
        if (result == FetchResult.Ready) files.present += name
        return result
    }
}

class PlayFromCloudTest {
    private class Env(scope: TestScope, withFetcher: Boolean = true) {
        val sessions = SessionRepository(FakeTimenoteDao(), scope.backgroundScope, FakeDefaultTagsState(), FakeDeviceId(), now = { 1L })
        val player = FakeAudioPlayer()
        val files = FakeFiles()
        val fetcher = FakeFetcher(files = files)
        val vm = HistoryViewModel(sessions, player, FakeAudioRecorder(), files, if (withFetcher) fetcher else null, now = { 1L })
    }

    @Test fun aMemoThatIsAlreadyHereJustPlaysWithoutAnyDownload() = runAppTest {
        val env = Env(this)
        env.files.present += "memo.m4a"

        env.vm.playAudio("memo.m4a")

        assertEquals("/fake/memo.m4a", env.player.lastPlayed)
        assertTrue(env.fetcher.fetched.isEmpty())
    }

    @Test fun aMemoFromAnotherDeviceIsDownloadedAndThenPlayed() = runAppTest {
        val env = Env(this)

        env.vm.playAudio("memo.m4a")

        assertEquals(listOf("memo.m4a"), env.fetcher.fetched)
        assertEquals("/fake/memo.m4a", env.player.lastPlayed)
        assertEquals("memo.m4a", env.vm.playingAudioPath.value)
        assertTrue(env.vm.downloadingAudio.value.isEmpty())
        assertNull(env.vm.audioMessage.value)
    }

    @Test fun whileDownloadingTheNoteShowsProgressAndTapsAreIgnored() = runAppTest {
        val env = Env(this)
        env.fetcher.gate = CompletableDeferred()

        env.vm.playAudio("memo.m4a")
        assertEquals(setOf("memo.m4a"), env.vm.downloadingAudio.value)
        env.vm.playAudio("memo.m4a") // an impatient second tap
        assertEquals(1, env.fetcher.fetched.size)
        assertNull(env.player.lastPlayed)

        env.fetcher.gate!!.complete(Unit)
        assertTrue(env.vm.downloadingAudio.value.isEmpty())
        assertEquals("/fake/memo.m4a", env.player.lastPlayed)
    }

    @Test fun aMemoThatIsNotInTheCloudExplainsWhyNothingPlays() = runAppTest {
        val env = Env(this)
        env.fetcher.result = FetchResult.NotInCloud

        env.vm.playAudio("memo.m4a")

        assertNull(env.player.lastPlayed)
        assertTrue(env.vm.audioMessage.value!!.contains("not on this device"))
    }

    @Test fun beingOfflineGivesAPlainMessageAndTryingAgainLaterWorks() = runAppTest {
        val env = Env(this)
        env.fetcher.result = FetchResult.Failed(RemoteException.Network(RuntimeException("offline")))
        env.vm.playAudio("memo.m4a")
        assertTrue(env.vm.audioMessage.value!!.contains("Check your connection"))
        assertNull(env.player.lastPlayed)

        env.fetcher.result = FetchResult.Ready
        env.vm.playAudio("memo.m4a")
        assertNull(env.vm.audioMessage.value, "the old message is cleared by the new attempt")
        assertEquals("/fake/memo.m4a", env.player.lastPlayed)
    }

    @Test fun lostAccessToTheCloudIsSaidAsSuch() = runAppTest {
        val env = Env(this)
        env.fetcher.result = FetchResult.Failed(RemoteException.NeedsSignIn())
        env.vm.playAudio("memo.m4a")
        assertTrue(env.vm.audioMessage.value!!.contains("Connect Google Drive"))
    }

    @Test fun theMessageCanBeDismissed() = runAppTest {
        val env = Env(this)
        env.fetcher.result = FetchResult.NotInCloud
        env.vm.playAudio("memo.m4a")
        env.vm.dismissAudioMessage()
        assertNull(env.vm.audioMessage.value)
    }

    @Test fun withoutCloudSyncAMissingFileBehavesAsBefore() = runAppTest {
        val env = Env(this, withFetcher = false)
        env.vm.playAudio("memo.m4a")
        assertEquals("/fake/memo.m4a", env.player.lastPlayed, "no download is attempted; playback is tried as usual")
        assertNull(env.vm.audioMessage.value)
    }

    @Test fun aFileThatCannotBePlayedDoesNotLeaveTheButtonOnPause() = runAppTest {
        val env = Env(this)
        env.files.present += "broken.m4a"
        env.player.failImmediately = true

        env.vm.playAudio("broken.m4a")

        assertNull(env.vm.playingAudioPath.value)
    }

    @Test fun legacyPathsAreRecognisedAsTheSameMemo() = runAppTest {
        val env = Env(this)
        env.vm.playAudio("/data/user/0/old/files/voice_memos/memo.m4a")
        assertEquals(listOf("memo.m4a"), env.fetcher.fetched, "the cloud is asked for the file name, not the path")
    }
}
