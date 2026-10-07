package com.oblutack.timenote.sync

import com.oblutack.timenote.drive.RemoteException
import com.oblutack.timenote.testutil.FakeRemoteStore
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testFolder
import com.oblutack.timenote.testutil.testNote
import com.oblutack.timenote.testutil.testTag
import kotlinx.coroutines.test.TestScope
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The property that matters for sync: whatever the devices do, in whatever order they sync, and even when some syncs
 * fail halfway, once everyone has synced enough all devices show exactly the same data.
 */
class SyncConvergenceTest {

    private fun TestScope.replica(name: String, cloud: FakeRemoteStore, clock: Clock) = Replica(name, backgroundScope, cloud, clock)

    /** Same data on every device; list orders that carry no meaning (voice memo order) are compared sorted. */
    private suspend fun Replica.comparableView(): List<String> = view().map { line ->
        Regex("voice=\\[(.*?)]").replace(line) { m -> "voice=" + m.groupValues[1].split(", ").filter { it.isNotEmpty() }.sorted() }
    }

    private suspend fun settle(replicas: List<Replica>) {
        repeat(12) {
            val quiet = replicas.map { it.sync() }.all { it is SyncResult.Done && it.stats == SyncStats() }
            if (quiet) return
        }
    }

    private suspend fun randomStep(r: Replica, rnd: Random, counter: IntArray) {
        val tags = listOf("t1", "t2", "t3")
        val notes = r.dao.getAllTimenotesOnce()
        val live = notes.filter { !it.isDeleted }
        val trashed = notes.filter { it.isDeleted }
        fun pickLive() = live[rnd.nextInt(live.size)].id
        when (rnd.nextInt(14)) {
            0, 1 -> { counter[0]++; r.repo.saveTimenote(testNote("n${counter[0]}", title = "t${counter[0]}", createdAt = r.dao.hashCode().toLong())) }
            2 -> if (live.isNotEmpty()) r.repo.updateTimenoteTitle(pickLive(), "title-${rnd.nextInt(1000)}")
            3 -> if (live.isNotEmpty()) r.repo.updateTimenoteDescription(pickLive(), "text-${rnd.nextInt(1000)}")
            4 -> if (live.isNotEmpty()) r.repo.toggleTimenotePin(pickLive())
            5, 6 -> if (live.isNotEmpty()) {
                val id = pickLive()
                val current = r.repo.getTimenoteById(id)?.tags?.map { it.id }.orEmpty().toMutableSet()
                val tag = tags[rnd.nextInt(tags.size)]
                if (!current.add(tag)) current.remove(tag)
                r.repo.updateTimenoteTags(id, current.map { testTag(it) })
            }
            7 -> if (live.isNotEmpty()) r.repo.deleteTimenote(pickLive())
            8 -> if (trashed.isNotEmpty()) r.repo.restoreTimenote(trashed[rnd.nextInt(trashed.size)].id)
            9 -> if (trashed.isNotEmpty()) r.repo.hardDeleteTimenote(trashed[rnd.nextInt(trashed.size)].id)
            10 -> if (live.isNotEmpty()) {
                val id = pickLive()
                val name = "memo${rnd.nextInt(4)}.m4a"
                if (r.repo.getTimenoteById(id)?.voiceNotes?.contains(name) == true) r.repo.removeVoiceNote(id, name) else r.repo.addVoiceNote(id, name)
            }
            11 -> { val f = "f${rnd.nextInt(3)}"; if (r.repo.getFolderById(f) == null) r.repo.saveFolder(testFolder(f, "Folder $f")) else r.repo.saveFolder(r.repo.getFolderById(f)!!.copy(name = "Renamed ${rnd.nextInt(100)}")) }
            12 -> if (live.isNotEmpty()) r.repo.assignFolderToTimenote(pickLive(), if (rnd.nextBoolean()) "f${rnd.nextInt(3)}" else null)
            13 -> r.repo.deleteTag(tags[rnd.nextInt(tags.size)])
        }
    }

    @Test fun threeDevicesWithRandomEditsAndSyncOrdersAlwaysEndUpIdentical() = runAppTest {
        for (seed in 1..40) {
            val rnd = Random(seed)
            val cloud = FakeRemoteStore(pageSize = 7)
            val clock = Clock()
            val devices = listOf("a", "b", "c").map { replica(it, cloud, clock) }
            val counter = IntArray(1)
            devices[0].let { d -> listOf("t1", "t2", "t3").forEach { d.repo.saveTag(testTag(it, "Tag $it")) } }

            repeat(60) {
                clock.tick()
                val device = devices[rnd.nextInt(devices.size)]
                if (rnd.nextInt(100) < 35) {
                    if (rnd.nextInt(100) < 10) cloud.failNext(RemoteException.Server(503))
                    device.sync()
                } else {
                    randomStep(device, rnd, counter)
                }
            }
            settle(devices)

            val reference = devices[0].comparableView()
            devices.drop(1).forEach { assertEquals(reference, it.comparableView(), "devices differ after seed $seed") }
            assertTrue(devices.all { it.dao.getPendingRemoteDeletes().isEmpty() }, "unfinished deletions after seed $seed")
        }
    }

    @Test fun anOverwriteByAnotherDeviceMidSyncIsHealedOnTheNextRun() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1", title = "start"))
        a.sync(); b.sync()

        clock.tick(); a.repo.updateTimenoteTitle("n1", "title from a")
        clock.tick(); b.repo.updateTimenoteDescription("n1", "text from b")

        // While a is in the middle of its run (just before its upload), b uploads its change to the same file
        var interfered = false
        cloud.beforeCall = { call ->
            if (!interfered && cloud.snapshot().isNotEmpty() && call >= 4) { interfered = true; cloud.beforeCall = null; b.sync() }
        }
        a.sync() // a overwrites what b just uploaded: b's text is only in b's database now
        cloud.beforeCall = null

        settle(listOf(a, b))

        listOf(a, b).forEach {
            val n = it.dao.getAllTimenotesOnce().single()
            assertEquals("title from a", n.title)
            assertEquals("text from b", n.description)
        }
    }

    @Test fun syncingOverAFlakyConnectionEventuallyGetsEverythingThrough() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        repeat(8) { a.repo.saveTimenote(testNote("n$it", title = "note $it")) }

        // every second call fails
        var n = 0
        cloud.beforeCall = { if (++n % 2 == 0) throw RemoteException.Network(RuntimeException("flaky")) }
        repeat(40) { a.sync(); b.sync() }
        cloud.beforeCall = null
        settle(listOf(a, b))

        assertEquals(8, b.dao.getAllTimenotesOnce().size)
        assertEquals(a.comparableView(), b.comparableView())
    }
}
