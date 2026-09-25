package org.cssnr.parking.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.util.Locale

/**
 * Reverse geocoded components for a parking position.
 *
 * Every field is nullable because the platform [android.location.Geocoder] returns
 * whatever its backend provider populates and guarantees nothing. [line] is the
 * provider's preformatted single line and is the best value for display; the
 * remaining components exist so the individual parts are available later.
 *
 * Note there is no street-number field: [android.location.Address] has no
 * thoroughfare number accessor, the number arrives in [subThoroughfare] or [line].
 */
data class LocationAddress(
    @ColumnInfo(name = "line")
    val line: String? = null,
    @ColumnInfo(name = "featureName")
    val featureName: String? = null,
    @ColumnInfo(name = "thoroughfare")
    val thoroughfare: String? = null,
    @ColumnInfo(name = "subThoroughfare")
    val subThoroughfare: String? = null,
    @ColumnInfo(name = "premises")
    val premises: String? = null,
    @ColumnInfo(name = "adminArea")
    val adminArea: String? = null,
    @ColumnInfo(name = "subAdminArea")
    val subAdminArea: String? = null,
    @ColumnInfo(name = "locality")
    val locality: String? = null,
    @ColumnInfo(name = "subLocality")
    val subLocality: String? = null,
    @ColumnInfo(name = "postalCode")
    val postalCode: String? = null,
    @ColumnInfo(name = "countryName")
    val countryName: String? = null,
    @ColumnInfo(name = "countryCode")
    val countryCode: String? = null,
) {
    /**
     * Best available human readable label, or null when the geocoder had no data.
     */
    val displayLabel: String?
        get() = line
            ?: listOfNotNull(featureName, thoroughfare, locality, adminArea, countryName)
                .joinToString(", ")
                .ifBlank { null }
}

@Entity(tableName = "history")
data class History(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val bluetoothAddress: String,
    val bluetoothName: String? = null,

    // Location fix metadata. All nullable: the platform guarantees only latitude,
    // longitude, timestamp and accuracy on provider generated locations, and
    // LocationProvider.getBestLocation can fall back to a lastLocation of unknown age.
    // [timestamp] is when the record was saved; [fixTimestamp] is when the position
    // was actually measured. Their difference is the fix age.
    val fixTimestamp: Long? = null,
    val fixAgeMillis: Long? = null,
    val accuracy: Float? = null,

    // Altitude is meters above the WGS84 reference ellipsoid, NOT above mean sea
    // level. Geoid undulation runs roughly +75 m to -100 m worldwide, so this
    // value will not match published elevations for the same spot.
    val altitude: Double? = null,
    val verticalAccuracy: Float? = null,

    @Embedded(prefix = "addr_")
    val address: LocationAddress = LocationAddress(),
) {
    /**
     * Best available label for this record: the reverse geocoded address when the
     * geocoder produced one, otherwise the raw coordinates. Never blank, so it is
     * safe to use directly as a title.
     */
    val displayName: String
        get() = address.displayLabel
            ?: String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}
