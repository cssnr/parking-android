package org.cssnr.parking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.cssnr.parking.data.AutomationRepository

class AutomationViewModel(application: Application) : AndroidViewModel(application) {

    private val automationRepository = AutomationRepository(application)

    val automationEnabled: StateFlow<Boolean> = automationRepository.automationEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    val locationPermissionRequested: StateFlow<Boolean> = automationRepository.locationPermissionRequested
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    val backgroundPermissionRequested: StateFlow<Boolean> =
        automationRepository.backgroundPermissionRequested
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    val selectedBluetoothDevices: StateFlow<Set<String>> =
        automationRepository.selectedBluetoothDevices
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptySet(),
            )

    fun setAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            automationRepository.setAutomationEnabled(enabled)
        }
    }

    fun markLocationPermissionRequested() {
        viewModelScope.launch {
            automationRepository.markLocationPermissionRequested()
        }
    }

    fun markBackgroundPermissionRequested() {
        viewModelScope.launch {
            automationRepository.markBackgroundPermissionRequested()
        }
    }

    fun setSelectedBluetoothDevices(addresses: Set<String>) {
        viewModelScope.launch {
            automationRepository.setSelectedBluetoothDevices(addresses)
        }
    }
}