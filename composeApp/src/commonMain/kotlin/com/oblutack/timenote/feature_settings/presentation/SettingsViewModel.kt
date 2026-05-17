package com.oblutack.timenote.feature_settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oblutack.timenote.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

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
                try {
                    // 1. Add FF for 100% opacity
                    val fullHex = "FF$cleanHex"
                    // 2. Parse the raw ARGB value safely
                    val rawArgb = fullHex.toLongOrNull(16)

                    if (rawArgb != null) {
                        // 3. Create a native Compose Color, then extract its encoded internal ULong value!
                        val composeEncodedLong = Color(rawArgb).value.toLong()

                        // 4. Save the safe, encoded Compose value to DataStore
                        SettingsRepository.addCustomColor(composeEncodedLong)
                    }
                } catch (e: Exception) {
                    // Ignore invalid inputs
                }
            }
        }
    }

    fun addPickedColor(color: androidx.compose.ui.graphics.Color) {
        androidx.lifecycle.viewModelScope.launch {
            // Extracts the raw ULong and saves it to DataStore
            com.oblutack.timenote.data.repository.SettingsRepository.addCustomColor(color.value.toLong())
        }
    }
    fun deleteCustomColor(colorLong: Long) {
        viewModelScope.launch {
            SettingsRepository.removeCustomColor(colorLong)
        }
    }
}