package com.oblutack.timenote.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oblutack.timenote.feature_history.presentation.HistoryViewModel
import com.oblutack.timenote.feature_history.presentation.TrashViewModel
import com.oblutack.timenote.feature_settings.presentation.SettingsViewModel
import com.oblutack.timenote.feature_timer.presentation.TimerViewModel

// Compose helpers that build ViewModels from the AppContainer. Used as default arguments of the
// screens, so a screen can still be given a different ViewModel (e.g. one built with fakes).

@Composable
fun timerViewModel(): TimerViewModel {
    val container = LocalAppContainer.current
    return viewModel {
        TimerViewModel(
            sessionRepository = container.sessionRepository,
            settingsRepository = container.settingsRepository,
            timerServiceManager = container.timerServiceManager,
            audioRecorder = container.audioRecorder,
            audioFiles = container.audioFiles,
            serviceCommands = container.serviceCommands
        )
    }
}

@Composable
fun historyViewModel(): HistoryViewModel {
    val container = LocalAppContainer.current
    return viewModel {
        HistoryViewModel(
            sessionRepository = container.sessionRepository,
            audioPlayer = container.audioPlayer,
            audioRecorder = container.audioRecorder,
            audioFiles = container.audioFiles,
            audioFetcher = container.audioFetcher
        )
    }
}

@Composable
fun trashViewModel(): TrashViewModel {
    val container = LocalAppContainer.current
    return viewModel { TrashViewModel(container.sessionRepository) }
}

@Composable
fun settingsViewModel(): SettingsViewModel {
    val container = LocalAppContainer.current
    return viewModel { SettingsViewModel(container.settingsRepository) }
}
