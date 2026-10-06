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
    /** The timer notification buttons emit here; TimerViewModel collects. */
    val serviceCommands: MutableSharedFlow<TimerServiceCommand> = MutableSharedFlow(extraBufferCapacity = 1)
)

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided. Wrap the UI in CompositionLocalProvider(LocalAppContainer provides ...).")
}
