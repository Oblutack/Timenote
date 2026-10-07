package com.oblutack.timenote.sync

import com.oblutack.timenote.drive.DriveSession
import com.oblutack.timenote.drive.DriveStatus
import com.oblutack.timenote.drive.RemoteException
import com.oblutack.timenote.drive.deleteEverythingInAppFolder
import com.oblutack.timenote.getCurrentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** What the user has chosen about syncing; survives restarts. */
interface SyncPrefs {
    val syncEnabledFlow: Flow<Boolean>
    suspend fun setSyncEnabled(enabled: Boolean)
    val lastSyncAtFlow: Flow<Long?>
    suspend fun setLastSyncAt(time: Long?)
    val voiceWifiOnlyFlow: Flow<Boolean>
    suspend fun setVoiceWifiOnly(enabled: Boolean)
}

/** Runs syncs without the app being open (WorkManager on Android). Only schedules; the work itself is [SyncManager.runBackgroundSync]. */
interface SyncScheduler {
    /** Every few hours while there is a connection. */
    fun schedulePeriodic()
    /** Once, [delayMs] from now; asking again moves it back, so a burst of edits causes one sync. */
    fun scheduleAfterEdit(delayMs: Long)
    fun cancelAll()
}

/** Something wrong that the user should know about, in words. [needsUser] means only they can fix it. */
data class SyncProblem(val text: String, val needsUser: Boolean)

/** A different Google account is signed in than the one this device syncs with. */
data class AccountChange(val linked: String, val current: String)

data class SyncUiState(
    /** Sync with Google Drive is available at all on this device. */
    val available: Boolean = false,
    val enabled: Boolean = false,
    val connection: DriveStatus = DriveStatus.Unknown,
    /** Readable account name (an email address), when known. */
    val accountLabel: String? = null,
    val syncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val problem: SyncProblem? = null,
    val voiceWifiOnly: Boolean = true,
    val accountChange: AccountChange? = null
)

/**
 * Everything the user can do about cloud sync, and what they see about it. The settings screen, the app start and the
 * background worker all go through here, so there is one place that decides when a sync runs and what its outcome means.
 */
class SyncManager(
    private val engine: SyncEngine,
    private val session: DriveSession,
    private val prefs: SyncPrefs,
    private val scheduler: SyncScheduler,
    private val scope: CoroutineScope,
    private val now: () -> Long = ::getCurrentTimeMillis
) {
    private data class Transient(
        val syncing: Boolean = false,
        val problem: SyncProblem? = null,
        val accountChange: AccountChange? = null,
        val accountLabel: String? = null
    )

    private val transient = MutableStateFlow(Transient())
    private val running = Mutex()

    val state: StateFlow<SyncUiState> = combine(
        prefs.syncEnabledFlow, prefs.lastSyncAtFlow, prefs.voiceWifiOnlyFlow, session.status, transient
    ) { enabled, last, wifiOnly, connection, t ->
        SyncUiState(
            available = true, enabled = enabled, connection = connection, accountLabel = t.accountLabel,
            syncing = t.syncing, lastSyncAt = last, problem = t.problem, voiceWifiOnly = wifiOnly, accountChange = t.accountChange
        )
    }.stateIn(scope, SharingStarted.Eagerly, SyncUiState(available = true))

    /** Looks at the connection (silently) and, when connected, fetches the account's readable name. */
    suspend fun refreshConnection() {
        session.check()
        val label = if (session.status.value == DriveStatus.Connected) session.accountLabel() else null
        transient.update { Transient(it.syncing, it.problem, it.accountChange, label) }
    }

    /** What linking would do, to show the user BEFORE anything is changed. Null (with the reason shown) if it cannot be found out. */
    suspend fun linkPreview(): LinkPreview? = try {
        engine.previewLinking()
    } catch (e: RemoteException) {
        val problem = problemOf(SyncResult.Failed(e)) ?: SyncProblem("Could not look at your Google Drive: ${e.message}", false)
        transient.update { it.copy(problem = problem) }
        null
    }

    fun setVoiceWifiOnly(enabled: Boolean) {
        scope.launch { prefs.setVoiceWifiOnly(enabled) }
    }

    /** The user confirmed: turn sync on and run the first sync (which merges, never replaces). */
    fun enable() {
        scope.launch {
            prefs.setSyncEnabled(true)
            scheduler.schedulePeriodic()
            runSync()
        }
    }

    /** Stops syncing and forgets the link. Everything on this device stays as it is; the cloud copy is not touched. */
    fun disable() {
        scope.launch {
            scheduler.cancelAll()
            prefs.setSyncEnabled(false)
            engine.unlink()
            transient.update { Transient(accountLabel = it.accountLabel) }
        }
    }

    /** Stops syncing and removes everything the app stored in the user's Google Drive. Local data stays. */
    fun deleteCloudDataAndDisconnect() {
        scope.launch {
            try {
                scheduler.cancelAll()
                deleteEverythingInAppFolder(session.store)
                prefs.setSyncEnabled(false)
                engine.unlink()
                transient.update { Transient(accountLabel = it.accountLabel) }
            } catch (e: RemoteException) {
                transient.update { it.copy(problem = SyncProblem("Your data could not be removed from Google Drive: ${e.message}. Nothing was changed.", false)) }
            }
        }
    }

    fun syncNow() {
        scope.launch { if (isEnabled()) runSync() }
    }

    /** The app came to the foreground: sync unless it just did. */
    fun onAppForeground(minGapMs: Long = FOREGROUND_GAP_MS) {
        scope.launch {
            if (!isEnabled()) return@launch
            val last = state.value.lastSyncAt
            if (last == null || now() - last >= minGapMs) runSync()
        }
    }

    /** The user changed something: sync soon, but only once for a burst of edits. */
    fun onLocalEdit() {
        scope.launch { if (isEnabled()) scheduler.scheduleAfterEdit(AFTER_EDIT_DELAY_MS) }
    }

    /** The user chose to sync with the signed-in account instead of the linked one. */
    fun useNewAccount() {
        scope.launch {
            engine.switchAccount()
            transient.update { it.copy(accountChange = null, problem = null) }
            runSync()
        }
    }

    fun dismissAccountChange() {
        transient.update { it.copy(accountChange = null) }
    }

    /** The sync a background worker runs. Returns the outcome so the worker can decide whether to retry. */
    suspend fun runBackgroundSync(): SyncResult = if (isEnabled()) runSync() else SyncResult.Done(SyncStats())

    private suspend fun isEnabled(): Boolean = state.value.enabled

    private suspend fun runSync(): SyncResult {
        if (!running.tryLock()) return SyncResult.AlreadyRunning
        transient.update { it.copy(syncing = true) }
        try {
            val result = engine.sync()
            when (result) {
                is SyncResult.Done -> {
                    prefs.setLastSyncAt(now())
                    transient.update { it.copy(problem = problemAfterSuccess(result.stats), accountChange = null) }
                }
                is SyncResult.AccountChanged -> transient.update { it.copy(accountChange = AccountChange(result.linked, result.current)) }
                SyncResult.AlreadyRunning -> {}
                else -> transient.update { it.copy(problem = problemOf(result)) }
            }
            return result
        } finally {
            transient.update { it.copy(syncing = false) }
            running.unlock()
        }
    }

    companion object {
        const val FOREGROUND_GAP_MS = 60_000L
        const val AFTER_EDIT_DELAY_MS = 30_000L
    }
}

/** What the user is told when a sync did not finish. */
fun problemOf(result: SyncResult): SyncProblem? = when (result) {
    SyncResult.NeedsSignIn ->
        SyncProblem("Access to Google Drive was removed. Connect again to continue syncing.", needsUser = true)
    SyncResult.StorageFull ->
        SyncProblem("Your Google Drive is full, so changes cannot be saved there. Free up space in Google Drive, then sync again.", needsUser = true)
    is SyncResult.Failed -> when (result.error) {
        is RemoteException.Network ->
            SyncProblem("Could not reach Google Drive. Your notes are safe on this device and syncing will try again automatically.", needsUser = false)
        else ->
            SyncProblem("Syncing did not finish (${result.error.message}). Your notes are safe on this device and it will try again automatically.", needsUser = false)
    }
    else -> null
}

/** A finished sync can still leave something undone: memos that could not be uploaded yet. */
fun problemAfterSuccess(stats: SyncStats): SyncProblem? =
    if (stats.audioFailed > 0) SyncProblem(
        "${stats.audioFailed} voice memo${if (stats.audioFailed == 1) "" else "s"} could not be uploaded yet. They will be tried again.",
        needsUser = false
    ) else null

/** A background sync that failed for a temporary reason is tried again later; everything else needs the user or is done. */
fun SyncResult.shouldRetryLater(): Boolean = this is SyncResult.Failed && error.retryable
