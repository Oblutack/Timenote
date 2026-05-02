package com.oblutack.timenote.feature_settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oblutack.timenote.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    val useMonochromeNodes = SettingsRepository.useMonochromeNodesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val customColors = SettingsRepository.customColorsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleMonochromeNodes(enabled: Boolean) {
        viewModelScope.launch {
            SettingsRepository.setMonochromeNodes(enabled)
        }
    }

    fun addCustomColor(hexString: String) {
        viewModelScope.launch {
            val cleanHex = hexString.removePrefix("#").uppercase()
            // Ensure it's a valid 6-character hex code
            if (cleanHex.length == 6) {
                // Add "FF" for 100% alpha (opacity)
                val colorLong = "FF$cleanHex".toLongOrNull(16)
                if (colorLong != null) {
                    SettingsRepository.addCustomColor(colorLong)
                }
            }
        }
    }

    fun deleteCustomColor(colorLong: Long) {
        viewModelScope.launch {
            SettingsRepository.removeCustomColor(colorLong)
        }
    }
}