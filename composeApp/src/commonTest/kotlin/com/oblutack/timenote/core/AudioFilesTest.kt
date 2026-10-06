package com.oblutack.timenote.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioFilesTest {
    private val dir = "/data/app/files/voice_memos"

    @Test fun toRefKeepsOnlyTheFileName() {
        val files = DirectoryAudioFiles(dir)
        assertEquals("a.m4a", files.toRef("/data/app/files/voice_memos/a.m4a"))
        assertEquals("a.m4a", files.toRef("a.m4a"))
        assertEquals("a.m4a", files.toRef("C:\\Users\\x\\a.m4a"))
    }

    @Test fun aNameResolvesIntoTheVoiceMemoFolder() {
        assertEquals("$dir/a.m4a", DirectoryAudioFiles(dir).resolve("a.m4a"))
    }

    @Test fun trailingSlashOnTheFolderIsIgnored() {
        assertEquals("$dir/a.m4a", DirectoryAudioFiles("$dir/").resolve("a.m4a"))
    }

    @Test fun aLegacyAbsolutePathMovesToTheCurrentFolder() {
        // e.g. a path recorded by an older version, or on another device
        assertEquals("$dir/a.m4a", DirectoryAudioFiles(dir).resolve("/some/other/place/a.m4a"))
    }

    @Test fun aLegacyPathThatStillExistsWhereItWasIsKeptAsAFallback() {
        val legacy = "/data/app/cache/a.m4a"
        val files = DirectoryAudioFiles(dir, exists = { it == legacy })
        assertEquals(legacy, files.resolve(legacy))
    }

    @Test fun theCurrentFolderWinsWhenBothCopiesExist() {
        val legacy = "/data/app/cache/a.m4a"
        val files = DirectoryAudioFiles(dir, exists = { true })
        assertEquals("$dir/a.m4a", files.resolve(legacy))
    }

    @Test fun storingThenResolvingRoundTripsWithinOneDevice() {
        val files = DirectoryAudioFiles(dir)
        val recorded = "$dir/SessionMemo_1.m4a"
        assertEquals(recorded, files.resolve(files.toRef(recorded)))
    }
}
