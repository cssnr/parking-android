package org.cssnr.parking.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.cssnr.parking.data.db.LocationAddress
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Reverse geocodes a coordinate into [LocationAddress] using the platform
 * [Geocoder], which talks to the device's geocode provider rather than a hosted
 * service, so there is no API key, quota or third party dependency.
 *
 * The backend may be absent (devices with no geocode provider) or unreachable
 * (no network), in which case every method here returns null instead of
 * throwing. Callers must treat geocoding as optional enrichment.
 */
class ReverseGeocoder(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Returns the geocoded components for the given position, or null if the
     * geocoder is unavailable, the lookup failed, or it had no data.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): LocationAddress? {
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocodeAsync(latitude, longitude)
        } else {
            geocodeBlocking(latitude, longitude)
        } ?: return null
        return address.toLocationAddress()
    }

    /**
     * API 33+ non blocking overload.
     *
     * The listener may fire on a binder thread, so this only resumes the
     * continuation; the caller decides which dispatcher the work runs on.
     *
     * Annotated rather than inlined into the version check so lint can see that
     * [Geocoder.getFromLocation] with a listener is only reached above API 33.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocodeAsync(latitude: Double, longitude: Double): Address? {
        val geocoder = Geocoder(appContext, Locale.getDefault())
        return suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                if (continuation.isActive) {
                    continuation.resume(addresses.firstOrNull())
                }
            }
        }
    }

    /**
     * API 26 to 32, where the only available overload is the deprecated blocking
     * one. It may hit the network, so it must never run on the main thread.
     */
    @Suppress("DEPRECATION")
    private suspend fun geocodeBlocking(latitude: Double, longitude: Double): Address? =
        withContext(Dispatchers.IO) {
            runCatching {
                Geocoder(appContext, Locale.getDefault())
                    .getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull()
            }.onFailure {
                Log.w(TAG, "reverseGeocode blocking lookup failed: ${it.message}")
            }.getOrNull()
        }

    companion object {
        private const val TAG = "ReverseGeocoder"
    }
}

/**
 * Flattens an [Address] into the persisted [LocationAddress].
 *
 * [Address.getAddressLine] is indexed from zero and the highest populated index
 * is [Address.getMaxAddressLineIndex], so the first line is only read when at
 * least one exists.
 */
private fun Address.toLocationAddress(): LocationAddress = LocationAddress(
    line = if (maxAddressLineIndex >= 0) getAddressLine(0) else null,
    featureName = featureName,
    thoroughfare = thoroughfare,
    subThoroughfare = subThoroughfare,
    premises = premises,
    adminArea = adminArea,
    subAdminArea = subAdminArea,
    locality = locality,
    subLocality = subLocality,
    postalCode = postalCode,
    countryName = countryName,
    countryCode = countryCode,
)
