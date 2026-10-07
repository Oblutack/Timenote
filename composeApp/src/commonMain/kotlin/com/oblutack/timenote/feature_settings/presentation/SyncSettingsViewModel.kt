package com.oblutack.timenote.feature_settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oblutack.timenote.sync.LinkPreview
import com.oblutack.timenote.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The summary shown BEFORE a device is connected. */
sealed interface LinkDialog {
    data object Hidden : LinkDialog
    data object Loading : LinkDialog
    data class Ready(val preview: LinkPreview) : LinkDialog
}

class SyncSettingsViewModel(private val manager: SyncManager) : ViewModel() {
    val state = manager.state

    private val _linkDialog = MutableStateFlow<LinkDialog>(LinkDialog.Hidden)
    val linkDialog: StateFlow<LinkDialog> = _linkDialog.asStateFlow()

    /** Called when the screen opens and after the consent screen: looks at the connection quietly. */
    fun refresh() {
        viewModelScope.launch { manager.refreshConnection() }
    }

    /** Google access was granted: show what connecting will do, and only then turn sync on. */
    fun showLinkPreview() {
        _linkDialog.value = LinkDialog.Loading
        viewModelScope.launch {
            val preview = manager.linkPreview()
            _linkDialog.value = if (preview != null) LinkDialog.Ready(preview) else LinkDialog.Hidden // the reason is in state.problem
        }
    }

    fun confirmLink() {
        _linkDialog.value = LinkDialog.Hidden
        manager.enable()
    }

    fun cancelLink() { _linkDialog.value = LinkDialog.Hidden }

    fun syncNow() = manager.syncNow()
    fun turnOff() = manager.disable()
    fun deleteCloudData() = manager.deleteCloudDataAndDisconnect()
    fun setVoiceWifiOnly(enabled: Boolean) = manager.setVoiceWifiOnly(enabled)
    fun useNewAccount() = manager.useNewAccount()
    fun keepOldAccount() = manager.dismissAccountChange()
}
