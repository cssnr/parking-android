package org.cssnr.parking.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.cssnr.parking.data.db.History
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Coordinates the disconnect -> check automatic prefs -> record -> best location -> Room history add.
 *
 * Recording is deliberately split in two. [recordDisconnect] writes the event and
 * returns immediately; [attachFix] attaches a position to it afterwards. The
 * split exists because the two halves have opposite failure modes, and the event
 * is the part that must never be lost.
 *
 * A disconnect is a fact the user cannot recreate: if the car is towed or the spot
 * is taken, the timestamp is gone forever. A position is a best-effort reading, and
 * for a background app "the location system service computes a new location for
 * your app only a few times each hour", so the request can legitimately come back
 * with nothing. Writing the event first means that outcome costs the coordinates
 * and nothing else.
 */
class ParkingRecorder(
    private val automaticRepository: AutomaticRepository,
    private val historyRepository: HistoryRepository,
    private val reverseGeocoder: ReverseGeocoder,
    private val context: Context,
) {

    /**
     * Records that a parking event happened, without a position.
     *
     * Returns the stored record, or null if this disconnect is not one ParKing is
     * tracking. The record comes back so [attachFix] can update the same row.
     *
     * Every check here is a preference or permission read, so this is fast enough
     * to run first and unconditionally. It is also the only place the tracking
     * rules live, so a caller cannot get them subtly different.
     *
     * Each rejection is logged distinctly. "Nothing was recorded" has four very
     * different causes here and a single message for all of them is what makes this
     * kind of bug hard to see from a bug report.
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
     * Attaches a position to an already-recorded event and geocodes it.
     *
     * The two updates are chained from `located` rather than from the original
     * record, so the geocode write cannot roll the coordinates back to null.
     *
     * The geocode is bounded by [GEOCODE_TIMEOUT] and its failure is not fatal: the
     * position is already stored either way, and a record left with
     * [History.geocoded] unset is what the backfill in HistoryViewModel keys off to
     * retry it later.
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
        historyRepository.update(record.copy(address = address, geocoded = true))
        Log.d(TAG, "record ${record.id} address: $address")
    }

    companion object {
        private const val TAG = "ParkingRecorder"
        private val GEOCODE_TIMEOUT = 5.seconds
    }
}
