package org.cssnr.parking.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.cssnr.parking.data.db.History
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Coordinates the disconnect -> check automatic prefs -> best location -> Room history add.
 *
 * The coordinate record is written first and the reverse geocode is applied
 * afterwards as a separate update, so a slow or unreachable geocoding backend can
 * never cost you a parking spot.
 */
class ParkingRecorder(
    private val automaticRepository: AutomaticRepository,
    private val historyRepository: HistoryRepository,
    private val locationProvider: LocationProvider,
    private val reverseGeocoder: ReverseGeocoder,
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
        val fix = location.toLocationFix()
        val history = History(
            timestamp = Instant.now().toEpochMilli(),
            latitude = fix.latitude,
            longitude = fix.longitude,
            bluetoothAddress = address,
            bluetoothName = name,
            fixTimestamp = fix.fixTimestamp,
            fixAgeMillis = fix.fixAgeMillis,
            accuracy = fix.accuracy,
            altitude = fix.altitude,
            verticalAccuracy = fix.verticalAccuracy,
        )
        Log.d("ParkingRecorder", "historyRepository.add - history: $history")
        val id = historyRepository.add(history)
        attachAddress(history.copy(id = id), fix)
        return true
    }

    /**
     * Reverse geocodes the record and writes the address components back.
     *
     * Bounded by [GEOCODE_TIMEOUT] because this runs inside the broadcast
     * receiver's goAsync window. Any failure, including a timeout, is logged and
     * dropped: the coordinates are already stored.
     */
    private suspend fun attachAddress(record: History, fix: LocationFix) {
        val address = withTimeoutOrNull(GEOCODE_TIMEOUT) {
            runCatching { reverseGeocoder.reverseGeocode(fix.latitude, fix.longitude) }
                .onFailure { Log.w(TAG, "reverseGeocode threw: ${it.message}") }
                .getOrNull()
        }
        if (address == null) {
            Log.w(TAG, "no address for record ${record.id}, keeping coordinates only")
            return
        }
        historyRepository.update(record.copy(address = address))
        Log.d(TAG, "record ${record.id} address: $address")
    }

    companion object {
        private const val TAG = "ParkingRecorder"
        private val GEOCODE_TIMEOUT = 5.seconds
    }
}
