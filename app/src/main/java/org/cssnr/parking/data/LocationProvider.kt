package org.cssnr.parking.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.compose.location.LocationMeasurement
import org.maplibre.spatialk.units.International.Meters
import kotlin.coroutines.resume
import kotlin.time.Duration
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
 * Wraps FusedLocationProviderClient to obtain a freshly derived location fix.
 */
@SuppressLint("MissingPermission")
class LocationProvider(context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * The fix to record for the parking spot, or null if nothing usable arrived
     * within [budget].
     *
     * `lastLocation` is deliberately not consulted. It is cached per app, and
     * ParKing asks for nothing while the car is being driven, so its value is
     * whatever the platform last computed for ParKing specifically, which is
     * typically wherever the phone was before the trip. That is the stale location
     * this pipeline exists to avoid.
     *
     * A null return is a normal outcome, not a failure. ParKing is a background app
     * at this point and "the location system service computes a new location for
     * your app only a few times each hour", so the request is throttled and is
     * allowed to come back with nothing. The caller records the event regardless and
     * simply has no position for it yet.
     */
    suspend fun getBestFix(budget: Duration): LocationFix? {
        val started = SystemClock.elapsedRealtime()
        val location = withTimeoutOrNull(budget) { getCurrentLocation(budget) }
        val elapsed = SystemClock.elapsedRealtime() - started
        if (location == null) {
            Log.d(TAG, "no fix after ${elapsed}ms of $budget")
            return null
        }
        val fix = location.toLocationFix()
        Log.d(TAG, "fix aged ${fix.fixAgeMillis}ms accuracy ${fix.accuracy}m in ${elapsed}ms")
        if (!fix.isFreshEnough()) {
            Log.w(
                TAG,
                "discarding fix aged " +
                    "${fix.fixAgeMillis?.let { "${it}ms" } ?: "unknown"}: " +
                    "older than $MAX_ACCEPTABLE_FIX_AGE",
            )
            return null
        }
        return fix
    }

    /**
     * One-shot request for the current location.
     *
     * [CurrentLocationRequest] rather than a streaming
     * [com.google.android.gms.location.LocationRequest] because this is a single
     * fix, not a stream. It carries the two things that matter here, a duration and
     * a maximum acceptable age, as first class parameters. The streaming request
     * has neither, so its update *interval* would have had to be abused as a
     * deadline.
     *
     * The returned task resolves to null when the duration expires with no location,
     * so the [withTimeoutOrNull] in [getBestFix] is a backstop rather than the
     * mechanism the deadline relies on.
     */
    private suspend fun getCurrentLocation(budget: Duration): Location? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(budget.inWholeMilliseconds)
            // "A value of 0 indicates that only freshly derived locations will be
            // returned, and no historical locations will ever be returned." The
            // default is 10 seconds, which would happily hand back a position from
            // ten seconds before the car stopped.
            .setMaxUpdateAgeMillis(CURRENT_LOCATION_MAX_AGE)
            .build()
        val cancellation = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancellation.cancel() }
            fusedLocationClient.getCurrentLocation(request, cancellation.token)
                .addOnSuccessListener { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
                .addOnFailureListener {
                    Log.w(TAG, "request failed: ${it.message}")
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }

    companion object {
        private const val TAG = "LocationProvider"

        /**
         * Age limit passed to the request itself, in milliseconds.
         *
         * Zero, which the request documents as "only freshly derived locations
         * will be returned, and no historical locations will ever be returned".
         */
        private const val CURRENT_LOCATION_MAX_AGE = 0L

        /**
         * Oldest fix that is still accepted as describing where the car is parked.
         *
         * The car has already stopped by the time the disconnect fires, so the fix
         * only has to be newer than the moment the car came to rest. Thirty seconds
         * absorbs GMS derivation latency and the delay between disconnect and
         * delivery. Anything looser and a fix taken before a drive would be
         * accepted as the parking position; anything tighter and ordinary fixes
         * would be rejected, leaving events with no location.
         */
        internal val MAX_ACCEPTABLE_FIX_AGE = 30.seconds

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

private const val NANOS_PER_MILLI = 1_000_000L

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

/**
 * How old a fix may be and still be accepted as the parking position.
 *
 * A fix of unknown age fails: the point of this check is to be able to say the
 * location was measured at the time of the disconnect, and a fix that never had
 * elapsed realtime stamped on it cannot support that claim. GMS always stamps it,
 * so this is a guard against an unrecognised provider rather than a common case.
 */
internal fun LocationFix.isFreshEnough(): Boolean =
    fixAgeMillis != null && fixAgeMillis <= LocationProvider.MAX_ACCEPTABLE_FIX_AGE.inWholeMilliseconds

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
