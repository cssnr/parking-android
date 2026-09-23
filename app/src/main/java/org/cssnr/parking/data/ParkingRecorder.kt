package org.cssnr.parking.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import org.cssnr.parking.data.db.History
import java.time.Instant

/**
 * Coordinates the disconnect -> check automatic prefs -> best location -> Room history add.
 */
class ParkingRecorder(
    private val automaticRepository: AutomaticRepository,
    private val historyRepository: HistoryRepository,
    private val locationProvider: LocationProvider,
    private val context: Context,
) {

    /**
     * Attempts to record a parking event for the given Bluetooth device address.
     * Returns true if a history record was added.
     * TODO: IDE says this function is never used
     */
    suspend fun recordDisconnect(address: String, name: String?): Boolean {
        Log.d("ParkingRecorder", "recordDisconnect - address: $address name: $name")
        if (address.isBlank()) return false
        if (!automaticRepository.automaticEnabled.first()) return false
        if (!LocationProvider.hasLocationPermission(context)) return false
        Log.d(
            "ParkingRecorder",
            "selectedBluetoothDevices: ${automaticRepository.selectedBluetoothDevices}"
        )
        val selected = automaticRepository.selectedBluetoothDevices.first()
        Log.d("ParkingRecorder", "selected: $selected")
        if (address !in selected) return false
        val location = locationProvider.getBestLocation() ?: return false
        Log.d("ParkingRecorder", "location: $location")
        val history = History(
            timestamp = Instant.now().toEpochMilli(),
            latitude = location.latitude,
            longitude = location.longitude,
            bluetoothAddress = address,
            bluetoothName = name,
        )
        Log.d("ParkingRecorder", "historyRepository.add - history: $history")
        historyRepository.add(history)
        return true
    }
}
