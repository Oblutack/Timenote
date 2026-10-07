package com.oblutack.timenote.di

import androidx.compose.runtime.staticCompositionLocalOf
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.data.repository.SettingsRepository
import com.oblutack.timenote.feature_timer.domain.AudioPlayer
import com.oblutack.timenote.feature_timer.domain.AudioRecorder
import com.oblutack.timenote.feature_timer.domain.TimerServiceCommand
import com.oblutack.timenote.feature_timer.domain.TimerServiceManager
import kotlinx.coroutines.flow.MutableSharedFlow
import com.oblutack.timenote.core.AudioFiles
import com.oblutack.timenote.backup.BackupRunner
import com.oblutack.timenote.drive.DriveSession
import com.oblutack.timenote.sync.SyncEngine
import com.oblutack.timenote.sync.AudioFetcher
import com.oblutack.timenote.sync.SyncManager

/**
 * Manual dependency injection: the platform entry point builds one of these and hands it to App().
 * ViewModels receive what they need through their constructors, so tests can pass fakes.
 */
class AppContainer(
    val sessionRepository: SessionRepository,
    val settingsRepository: SettingsRepository,
    val timerServiceManager: TimerServiceManager,
    val audioRecorder: AudioRecorder,
    val audioPlayer: AudioPlayer,
    /** Turns stored voice-memo references (file names) into real files on this device and back. */
    val audioFiles: AudioFiles,
    /** Export and import of a backup file (Settings). */
    val backupRunner: BackupRunner,
    /** Google Drive connection (Android only for now; null where there is none). Used by the debug panel until sync ships. */
    val driveSession: DriveSession? = null,
    /** One sync run with Google Drive (null until sync is available on the platform). */
    val syncEngine: SyncEngine? = null,
    /** What the settings screen, app start and background worker use to run and report syncs. */
    val syncManager: SyncManager? = null,
    /** Downloads voice memos recorded elsewhere when they are played (null when there is no sync). */
    val audioFetcher: AudioFetcher? = null,
    /** The timer notification buttons emit here; TimerViewModel collects. */
    val serviceCommands: MutableSharedFlow<TimerServiceCommand> = MutableSharedFlow(extraBufferCapacity = 1)
)

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided. Wrap the UI in CompositionLocalProvider(LocalAppContainer provides ...).")
}
