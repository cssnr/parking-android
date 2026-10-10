package org.cssnr.parking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.cssnr.parking.data.SettingsRepository

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    val historyDuration: StateFlow<Int> = settingsRepository.historyDuration
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsRepository.DEFAULT_HISTORY_DURATION,
        )

    fun setHistoryDuration(index: Int) {
        viewModelScope.launch {
            settingsRepository.setHistoryDuration(index)
        }
    }

    val crashReporting: StateFlow<Boolean> = settingsRepository.crashReporting
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    fun setCrashReporting(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCrashReporting(enabled)
        }
    }
}