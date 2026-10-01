package org.cssnr.parking.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.cssnr.parking.data.db.History
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Writes the parking event, then attaches a position to it.
 *
 * Split in two because the halves have opposite failure modes. The event is the
 * part that must never be lost: a disconnect cannot be recreated, because if the
 * car is towed the timestamp is gone. A position is best effort and the platform is
 * free to refuse it, so writing the event first means a refusal costs the
 * coordinates and nothing else.
 */
class ParkingRecorder(
    private val automaticRepository: AutomaticRepository,
    private val historyRepository: HistoryRepository,
    private val reverseGeocoder: ReverseGeocoder,
    private val context: Context,
) {

    /**
     * Writes the event with no position, or returns null if this is not a
     * disconnect ParKing tracks.
     *
     * The returned id is what [attachFix] updates. Each rejection is logged
     * distinctly, because "nothing was recorded" has four unrelated causes here.
     */
    suspend fun recordDisconnect(address: String, name: String?): History? {
        Log.d(TAG, "recordDisconnect - address: $address name: $name")
        if (address.isBlank()) {
            Log.d(TAG, "no address, not a device we can match")
            return null
        }
        if (!automaticRepository.automaticEnabled.first()) {
            Log.d(TAG, "automatic tracking is off")
            return null
        }
        if (!LocationProvider.hasLocationPermission(context)) {
            Log.w(TAG, "no location permission, cannot ever place $address")
            return null
        }
        val selected = automaticRepository.selectedBluetoothDevices.first()
        if (address !in selected) {
            Log.d(TAG, "$address is not selected (selected: $selected)")
            return null
        }
        val record = History(
            timestamp = Instant.now().toEpochMilli(),
            latitude = null,
            longitude = null,
            bluetoothAddress = address,
            bluetoothName = name,
        )
        val id = historyRepository.add(record)
        Log.d(TAG, "recorded $id without a position, waiting on a fix")
        return record.copy(id = id)
    }

    /**
     * Attaches a position to an already-written event and geocodes it.
     *
     * The two updates chain from `located`, so the geocode write cannot roll the
     * coordinates back to null. A failed geocode is not fatal: the position is
     * stored either way and the record keeps coordinates only.
     */
    suspend fun attachFix(record: History, fix: LocationFix) {
        Log.d(TAG, "fix: $fix")
        val located = record.copy(
            latitude = fix.latitude,
            longitude = fix.longitude,
            fixAgeMillis = fix.fixAgeMillis,
            accuracy = fix.accuracy,
            altitude = fix.altitude,
            verticalAccuracy = fix.verticalAccuracy,
        )
        historyRepository.update(located)
        attachAddress(located, fix)
    }

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
