package org.cssnr.parking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.cssnr.parking.data.AutomaticRepository

class AutomaticViewModel(application: Application) : AndroidViewModel(application) {

    private val automaticRepository = AutomaticRepository(application)

    val automaticEnabled: StateFlow<Boolean> = automaticRepository.automaticEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    val backgroundPermissionRequested: StateFlow<Boolean> =
        automaticRepository.backgroundPermissionRequested
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    val selectedBluetoothDevices: StateFlow<Set<String>> =
        automaticRepository.selectedBluetoothDevices
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptySet(),
            )

    fun setAutomaticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            automaticRepository.setAutomaticEnabled(enabled)
        }
    }

    fun markBackgroundPermissionRequested() {
        viewModelScope.launch {
            automaticRepository.markBackgroundPermissionRequested()
        }
    }

    fun setSelectedBluetoothDevices(addresses: Set<String>) {
        viewModelScope.launch {
            automaticRepository.setSelectedBluetoothDevices(addresses)
        }
    }
}