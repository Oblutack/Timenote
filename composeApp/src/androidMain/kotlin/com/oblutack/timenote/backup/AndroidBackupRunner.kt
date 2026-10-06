package com.oblutack.timenote.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.oblutack.timenote.core.logError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

class AndroidBackupRunner(
    private val context: Context,
    private val service: BackupService,
    private val scope: CoroutineScope,
    private val audioDir: File
) : BackupRunner {

    private val _status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    override val status: StateFlow<BackupStatus> = _status.asStateFlow()

    override fun dismiss() {
        if (_status.value !is BackupStatus.Working) _status.value = BackupStatus.Idle
    }

    override fun exportTo(destination: String) {
        if (_status.value is BackupStatus.Working) return
        _status.value = BackupStatus.Working("Saving your backup…")
        scope.launch(Dispatchers.IO) {
            val uri = Uri.parse(destination)
            try {
                val output = context.contentResolver.openOutputStream(uri, "w") ?: error("Cannot open the destination")
                val result = ZipBackupSink(output, audioDir).use { service.export(it) }
                _status.value = BackupStatus.Finished("Backup saved", describeExport(result), success = true)
            } catch (e: Exception) {
                logError("Backup", "Export failed", e)
                // A half-written file would look like a real backup, so remove it if the destination allows that
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                _status.value = BackupStatus.Finished(
                    "Backup failed",
                    "The backup could not be saved. Your timenotes were not changed. Check that there is enough free space and try again.",
                    success = false
                )
            }
        }
    }

    override fun importFrom(source: String) {
        if (_status.value is BackupStatus.Working) return
        _status.value = BackupStatus.Working("Reading the backup…")
        scope.launch(Dispatchers.IO) {
            // ZipFile needs a real file to read from, so the chosen document is copied to the cache first
            val copy = File(context.cacheDir, "import-backup.zip")
            try {
                val input = context.contentResolver.openInputStream(Uri.parse(source)) ?: error("Cannot open the file")
                input.use { stream -> copy.outputStream().use { stream.copyTo(it) } }
                val result = ZipBackupSource(ZipFile(copy), audioDir).use { service.import(it) }
                val ok = result is ImportResult.Done
                _status.value = BackupStatus.Finished(if (ok) "Backup imported" else "Cannot import", describeImport(result), ok)
            } catch (e: ZipException) {
                logError("Backup", "Not a zip file", e)
                _status.value = BackupStatus.Finished("Cannot import", describeImport(ImportResult.NotABackup), success = false)
            } catch (e: Exception) {
                logError("Backup", "Import failed", e)
                _status.value = BackupStatus.Finished(
                    "Import failed",
                    "The backup could not be read. Everything already on this device is unchanged. You can safely try again.",
                    success = false
                )
            } finally {
                copy.delete()
            }
        }
    }
}
