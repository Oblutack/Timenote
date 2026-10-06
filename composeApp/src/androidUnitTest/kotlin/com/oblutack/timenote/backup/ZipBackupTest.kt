package com.oblutack.timenote.backup

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZipBackupTest {
    private val root: File = Files.createTempDirectory("zipbackup").toFile()
    private val audioDir = File(root, "voice_memos").apply { mkdirs() }

    @AfterTest fun cleanUp() { root.deleteRecursively() }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ZipFile {
        val file = File(root, "test.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
        return ZipFile(file)
    }

    @Test fun whatTheSinkWritesTheSourceReadsBack() {
        File(audioDir, "memo.m4a").writeBytes(byteArrayOf(1, 2, 3, 4))
        val file = File(root, "backup.zip")
        ZipBackupSink(file.outputStream(), audioDir).use {
            it.putText("notes/a.json", """{"title":"Ünïcode ✓"}""")
            assertTrue(it.putAudio("memo.m4a"))
            assertFalse(it.putAudio("missing.m4a"), "a missing file is reported, not fatal")
        }

        val restoreDir = File(root, "restored").apply { mkdirs() }
        ZipBackupSource(ZipFile(file), restoreDir).use { source ->
            assertEquals("""{"title":"Ünïcode ✓"}""", source.readText("notes/a.json"))
            assertEquals(listOf("notes/a.json"), source.paths("notes/"))
            assertEquals(listOf("audio/memo.m4a"), source.paths("audio/"))
            assertTrue(source.restoreAudio("memo.m4a"))
        }
        assertEquals(listOf<Byte>(1, 2, 3, 4), File(restoreDir, "memo.m4a").readBytes().toList())
        assertFalse(File(restoreDir, "memo.m4a.part").exists(), "no half-written leftovers")
    }

    @Test fun anExistingMemoIsNeverOverwritten() {
        File(audioDir, "memo.m4a").writeBytes(byteArrayOf(9, 9))
        ZipBackupSource(zipOf("audio/memo.m4a" to byteArrayOf(1, 2, 3)), audioDir).use {
            assertFalse(it.restoreAudio("memo.m4a"))
        }
        assertEquals(listOf<Byte>(9, 9), File(audioDir, "memo.m4a").readBytes().toList())
    }

    @Test fun entryNamesCannotWriteOutsideTheMemoFolder() {
        val zip = zipOf(
            "audio/../../evil.m4a" to byteArrayOf(1),
            "audio/sub/dir.m4a" to byteArrayOf(1),
            "audio/good.m4a" to byteArrayOf(1)
        )
        ZipBackupSource(zip, audioDir).use {
            assertFalse(it.restoreAudio("../../evil.m4a"))
            assertFalse(it.restoreAudio("..\\evil.m4a"))
            assertFalse(it.restoreAudio("sub/dir.m4a"))
            assertFalse(it.restoreAudio(".."))
            assertFalse(it.restoreAudio(""))
            assertTrue(it.restoreAudio("good.m4a"))
        }
        assertEquals(listOf("good.m4a"), audioDir.list()!!.toList())
        assertFalse(File(root, "evil.m4a").exists())
    }

    @Test fun missingEntriesAreNull() {
        ZipBackupSource(zipOf("manifest.json" to "{}".toByteArray()), audioDir).use {
            assertNull(it.readText("notes/none.json"))
            assertFalse(it.restoreAudio("none.m4a"))
        }
    }

    @Test fun anAbsurdlyLargeTextEntryIsTreatedAsDamaged() {
        val huge = ByteArray(17 * 1024 * 1024) { 'a'.code.toByte() }
        ZipBackupSource(zipOf("notes/big.json" to huge), audioDir).use {
            assertNull(it.readText("notes/big.json"))
        }
    }
}
