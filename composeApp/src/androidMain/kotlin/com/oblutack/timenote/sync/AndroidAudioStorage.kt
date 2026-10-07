package com.oblutack.timenote.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.oblutack.timenote.core.audioFileName
import com.oblutack.timenote.data.repository.SettingsRepository
import com.oblutack.timenote.drive.ByteSink
import com.oblutack.timenote.drive.ByteSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * The voice memo folder of this device. Downloads are written to "<name>.part" and renamed when complete, so a memo
 * that is only half downloaded can never be played as if it were whole.
 */
class AndroidAudioStorage(private val directory: File) : AudioStorage {

    /** Names come from synced data, so they are checked: a plain file name only, never a path. */
    private fun fileFor(name: String): File {
        require(name.isNotEmpty() && name == audioFileName(name) && name != "." && name != "..") { "Not a plain file name: $name" }
        return File(directory, name)
    }

    override suspend fun size(name: String): Long? = withContext(Dispatchers.IO) {
        fileFor(name).takeIf { it.isFile }?.length()
    }

    override suspend fun source(name: String): ByteSource? = withContext(Dispatchers.IO) {
        fileFor(name).takeIf { it.isFile }?.let { FileByteSource(it) }
    }

    override suspend fun sink(name: String): ByteSink = withContext(Dispatchers.IO) {
        directory.mkdirs()
        FileByteSink(target = fileFor(name), partial = File(directory, "$name.part"))
    }
}

private class FileByteSource(private val file: File) : ByteSource {
    override val size: Long = file.length()

    override suspend fun read(offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val buffer = ByteArray(length)
            var filled = 0
            while (filled < length) {
                val n = raf.read(buffer, filled, length - filled)
                if (n < 0) break
                filled += n
            }
            if (filled == length) buffer else buffer.copyOf(filled)
        }
    }
}

private class FileByteSink(private val target: File, private val partial: File) : ByteSink {
    private var out: FileOutputStream? = null

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val stream = out ?: FileOutputStream(partial, false).also { out = it }
        stream.write(bytes)
    }

    override suspend fun finish() = withContext(Dispatchers.IO) {
        (out ?: FileOutputStream(partial, false)).use { it.fd.sync() } // also makes an empty file
        out = null
        if (target.exists()) target.delete()
        check(partial.renameTo(target)) { "Could not move the downloaded memo into place" }
    }

    override suspend fun abort() = withContext(Dispatchers.IO) {
        runCatching { out?.close() }
        out = null
        partial.delete()
        Unit
    }
}

/**
 * Voice memos can be large, so uploads only use a connection the user accepts: by default only one that is not
 * metered (normally Wi-Fi); with the setting off, any working connection.
 */
class AndroidAudioNetworkPolicy(private val context: Context, private val settings: SettingsRepository) : AudioNetworkPolicy {
    override suspend fun mayUpload(): Boolean {
        val wifiOnly = settings.voiceWifiOnly()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return try {
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (!wifiOnly || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
        } catch (e: SecurityException) {
            false // no permission to look at the network (release builds have none yet): do not upload
        }
    }
}
