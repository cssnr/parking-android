package org.cssnr.parking.data.db

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
 *
 * No property carries [androidx.room3.ColumnInfo]: Room applies the
 * [Embedded] prefix to sub properties unconditionally, so a column name here
 * could not change the resulting schema.
 */
data class LocationAddress(
    val line: String? = null,
    val featureName: String? = null,
    val thoroughfare: String? = null,
    val subThoroughfare: String? = null,
    val premises: String? = null,
    val adminArea: String? = null,
    val subAdminArea: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
    val postalCode: String? = null,
    val countryName: String? = null,
    val countryCode: String? = null,
) {
    /**
     * Best available human readable label, or null when the geocoder had no data.
     *
     * [subThoroughfare] carries the street number and [subLocality] the district, so
     * both belong in the fallback: leaving them out would silently truncate an
     * address the provider did return.
     */
    val displayLabel: String?
        get() = line
            ?: listOfNotNull(
                featureName,
                thoroughfare,
                subThoroughfare,
                premises,
                subLocality,
                locality,
                adminArea,
                countryName,
            ).joinToString(", ")
                .ifBlank { null }
}

@Entity(tableName = "history")
data class History(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,

    // Null until a position is attached. The disconnect is recorded the instant it
    // fires so the event can never be lost, and the coordinates are filled in
    // separately; asking for a location is throttled for a background app and
    // sometimes produces nothing, which must not cost the record itself.
    // [Migrations.MIGRATION_2_3] is what made these columns nullable.
    val latitude: Double?,
    val longitude: Double?,

    val bluetoothAddress: String,
    val bluetoothName: String? = null,

    // Location fix metadata. All nullable: the platform guarantees only latitude,
    // longitude, timestamp and accuracy on provider generated locations, and a fix
    // of unknown age is rejected by LocationProvider.isFreshEnough before it gets
    // this far.
    // [fixAgeMillis] is how stale the fix was at the moment it was recorded.
    val fixAgeMillis: Long? = null,
    val accuracy: Float? = null,

    // Altitude is meters above the WGS84 reference ellipsoid, NOT above mean sea
    // level. Geoid undulation runs roughly +75 m to -100 m worldwide, so this
    // value will not match published elevations for the same spot.
    val altitude: Double? = null,
    val verticalAccuracy: Float? = null,

    @Embedded(prefix = "addr_")
    val address: LocationAddress = LocationAddress(),

    // Set once a reverse geocode has succeeded, and that is the only thing the
    // backfill keys off, so it must be set even when the provider returns an
    // address with a null [LocationAddress.line]. Keying off the address columns
    // instead would re-look-up those records on every launch forever.
    //
    // A lookup that returns nothing leaves this null so the record is retried. Kept
    // nullable so MIGRATION_1_2 can add the column without a DEFAULT clause.
    val geocoded: Boolean? = null,
) {
    /**
     * Whether this record has a position yet.
     *
     * False for a disconnect whose location request produced nothing. The record
     * is still a real parking event, so it is still listed and still deletable;
     * there is just nothing to centre a map on.
     */
    val hasFix: Boolean
        get() = latitude != null && longitude != null

    /**
     * Best available label for this record: the reverse geocoded address when the
     * geocoder produced one, otherwise the raw coordinates.
     *
     * Null when the record has no position, because there is nothing to label and
     * this class holds no resources to name the gap in the user's language. Callers
     * that can be handed a positionless record supply their own text.
     */
    val displayName: String?
        get() = address.displayLabel
            ?: if (hasFix) {
                String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
            } else {
                null
            }
}
