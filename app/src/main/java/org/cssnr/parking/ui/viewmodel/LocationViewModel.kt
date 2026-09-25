package org.cssnr.parking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.cssnr.parking.data.HistoryRepository
import org.cssnr.parking.data.LocationFix
import org.cssnr.parking.data.LocationRepository
import org.cssnr.parking.data.ReverseGeocoder
import org.cssnr.parking.data.db.AppDatabase
import org.cssnr.parking.data.db.History
import kotlin.time.Duration.Companion.seconds

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
    private val reverseGeocoder = ReverseGeocoder(application)

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

    /**
     * Records the current position as the parking spot.
     *
     * The row is written before the reverse geocode runs, for the same reason
     * [org.cssnr.parking.data.ParkingRecorder] does it in that order: a
     * geocoding failure must not lose the position.
     */
    fun addInitialLocation(fix: LocationFix) {
        viewModelScope.launch {
            val id = historyRepository.add(
                History(
                    timestamp = System.currentTimeMillis(),
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    bluetoothAddress = "",
                    bluetoothName = "Manually Parked",
                    fixTimestamp = fix.fixTimestamp,
                    fixAgeMillis = fix.fixAgeMillis,
                    accuracy = fix.accuracy,
                    altitude = fix.altitude,
                    verticalAccuracy = fix.verticalAccuracy,
                )
            )
            val address = withTimeoutOrNull(GEOCODE_TIMEOUT) {
                runCatching { reverseGeocoder.reverseGeocode(fix.latitude, fix.longitude) }
                    .getOrNull()
            }
            if (address != null) {
                historyRepository.update(
                    historyRepository.getById(id)?.copy(address = address)
                        ?: return@launch
                )
            }
            locationRepository.dismissInitialLocationPrompt()
        }
    }

    companion object {
        private val GEOCODE_TIMEOUT = 5.seconds
    }
}