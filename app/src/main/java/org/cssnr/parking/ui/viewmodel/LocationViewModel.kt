package org.cssnr.parking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.cssnr.parking.data.HistoryRepository
import org.cssnr.parking.data.LocationRepository
import org.cssnr.parking.data.db.AppDatabase
import org.cssnr.parking.data.db.History

data class LocationUiState(
    val historyLoaded: Boolean = false,
    val latestRecord: History? = null,
    val promptDismissedLoaded: Boolean = false,
    val initialPromptDismissed: Boolean = false,
) {
    val ready: Boolean
        get() = historyLoaded && promptDismissedLoaded

    val shouldShowInitialPrompt: Boolean
        get() = ready && latestRecord == null && !initialPromptDismissed
}

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepository = HistoryRepository(AppDatabase.getDatabase(application))
    private val locationRepository = LocationRepository(application)

    val locationUiState: StateFlow<LocationUiState> =
        combine(
            historyRepository.history,
            locationRepository.initialLocationPromptDismissed,
        ) { history, dismissed ->
            LocationUiState(
                historyLoaded = true,
                latestRecord = history.firstOrNull(),
                promptDismissedLoaded = true,
                initialPromptDismissed = dismissed,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LocationUiState(),
        )

    fun dismissInitialLocationPrompt() {
        viewModelScope.launch {
            locationRepository.dismissInitialLocationPrompt()
        }
    }

    fun addInitialLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            historyRepository.add(
                History(
                    timestamp = System.currentTimeMillis(),
                    latitude = latitude,
                    longitude = longitude,
                    bluetoothAddress = "",
                    bluetoothName = "Manually Parked",
                )
            )
            locationRepository.dismissInitialLocationPrompt()
        }
    }
}