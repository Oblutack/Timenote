package com.oblutack.timenote.sync

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidAudioStorageTest {
    private val root: File = Files.createTempDirectory("audio").toFile()
    private val dir = File(root, "voice_memos")
    private val storage = AndroidAudioStorage(dir)

    @AfterTest fun cleanUp() { root.deleteRecursively() }

    @Test fun aMissingFileHasNoSizeAndNoSource() = runBlocking {
        assertNull(storage.size("none.m4a"))
        assertNull(storage.source("none.m4a"))
    }

    @Test fun aFileIsReadBackPieceByPiece() = runBlocking {
        dir.mkdirs()
        val data = ByteArray(10_000) { (it % 200).toByte() }
        File(dir, "memo.m4a").writeBytes(data)

        assertEquals(10_000L, storage.size("memo.m4a"))
        val source = storage.source("memo.m4a")!!
        assertEquals(10_000L, source.size)
        assertContentEquals(data.copyOfRange(0, 4_000), source.read(0, 4_000))
        assertContentEquals(data.copyOfRange(4_000, 8_000), source.read(4_000, 4_000))
        assertContentEquals(data.copyOfRange(8_000, 10_000), source.read(8_000, 4_000), "the last piece is short")
        assertContentEquals(data.copyOfRange(4_000, 8_000), source.read(4_000, 4_000), "a piece can be read again")
    }

    @Test fun aDownloadOnlyAppearsUnderItsNameWhenComplete() = runBlocking {
        val sink = storage.sink("memo.m4a")
        sink.write(byteArrayOf(1, 2, 3))
        sink.write(byteArrayOf(4, 5))

        assertNull(storage.size("memo.m4a"), "half a download is not a memo")
        assertTrue(File(dir, "memo.m4a.part").exists())

        sink.finish()

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), File(dir, "memo.m4a").readBytes())
        assertFalse(File(dir, "memo.m4a.part").exists())
    }

    @Test fun anAbortedDownloadLeavesNothing() = runBlocking {
        val sink = storage.sink("memo.m4a")
        sink.write(byteArrayOf(1, 2, 3))
        sink.abort()
        assertNull(storage.size("memo.m4a"))
        assertEquals(emptyList(), dir.list()!!.toList())
    }

    @Test fun aFailedDownloadNeverReplacesAGoodFile() = runBlocking {
        dir.mkdirs()
        File(dir, "memo.m4a").writeBytes(byteArrayOf(9, 9, 9))
        val sink = storage.sink("memo.m4a")
        sink.write(byteArrayOf(1))
        sink.abort()
        assertContentEquals(byteArrayOf(9, 9, 9), File(dir, "memo.m4a").readBytes())
    }

    @Test fun anEmptyDownloadStillProducesAFile() = runBlocking {
        val sink = storage.sink("empty.m4a")
        sink.finish()
        assertEquals(0L, storage.size("empty.m4a"))
    }

    @Test fun namesThatAreNotPlainFileNamesAreRejected() = runBlocking {
        listOf("../evil.m4a", "sub/dir.m4a", "..", ".", "", "C:\\x\\a.m4a").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { storage.size(bad) }
            assertFailsWith<IllegalArgumentException>(bad) { storage.sink(bad) }
        }
        assertFalse(File(root, "evil.m4a").exists())
    }
}
