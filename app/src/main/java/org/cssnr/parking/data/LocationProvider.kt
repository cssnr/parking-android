package org.cssnr.parking.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.compose.location.LocationMeasurement
import org.maplibre.spatialk.units.International.Meters
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * The subset of [Location] that ParKing persists.
 *
 * Optional fields are null rather than zero when the platform did not supply
 * them, so a missing fix is never mistaken for a real measurement. [altitude] is
 * meters above the WGS84 reference ellipsoid, not above mean sea level.
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val fixAgeMillis: Long?,
    val accuracy: Float?,
    val altitude: Double?,
    val verticalAccuracy: Float?,
)

/**
 * Wraps FusedLocationProviderClient to expose the best available last-known location.
 */
@SuppressLint("MissingPermission")
class LocationProvider(context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Returns the best available location: try a high-accuracy current fix first,
     * then fall back to the most recent known location.
     *
     * The current fix is bounded by [CURRENT_LOCATION_TIMEOUT] so that the fallback
     * is actually reachable. Left unbounded, GMS sits on the request for roughly
     * 30 seconds before returning null, so the fallback would only ever run when
     * the request failed outright, and this runs inside a broadcast window that is
     * shorter than that. Timing out here hands over to the last known fix, which
     * is a few seconds stale at worst and is what [Location.getElapsedRealtimeNanos]
     * plus the stored fix age are there to report.
     */
    suspend fun getBestLocation(): Location? {
        withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT) { getCurrentLocation() }
            ?.let { return it }
        return getLastLocation()
    }

    private suspend fun getCurrentLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }

    private suspend fun getLastLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }

    companion object {
        /**
         * How long to wait for a fresh fix before settling for the last known one.
         *
         * Generous, because a fresh fix is worth having and GMS usually returns
         * one well inside this. It only matters when the device cannot produce a
         * fix, where the fallback is a far better outcome than no record at all.
         */
        private val CURRENT_LOCATION_TIMEOUT = 10.seconds

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Reads a platform [Location] into a [LocationFix], guarding every optional
 * accessor with its `has*()` check.
 *
 * The fix age is measured on the monotonic elapsed-realtime clock rather than
 * derived from [Location.getTime], because wall clock time "can jump forwards or
 * backwards unpredictably". A location that never had elapsed realtime stamped
 * on it reports a null age instead of a nonsense one.
 */
fun Location.toLocationFix(): LocationFix = LocationFix(
    latitude = latitude,
    longitude = longitude,
    fixAgeMillis = if (elapsedRealtimeNanos > 0L) {
        ((SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / NANOS_PER_MILLI)
            .coerceAtLeast(0L)
    } else {
        null
    },
    accuracy = if (hasAccuracy()) accuracy else null,
    altitude = if (hasAltitude()) altitude else null,
    verticalAccuracy = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
)

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * Reads a MapLibre [LocationMeasurement] into a [LocationFix], so the manual save
 * path records the same detail as the background path.
 *
 * [LocationMeasurement] deliberately drops the platform fix age once it has
 * converted the fix, so [LocationFix.fixAgeMillis] is left null here rather than
 * being guessed from the wall clock. The fix itself is live, so the true age is
 * effectively zero.
 */
fun LocationMeasurement.toLocationFix(): LocationFix = LocationFix(
    latitude = position.latitude,
    longitude = position.longitude,
    fixAgeMillis = null,
    accuracy = horizontalAccuracy?.toFloat(Meters),
    altitude = position.altitude,
    verticalAccuracy = altitudeAccuracy?.toFloat(Meters),
)
