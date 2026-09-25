package org.cssnr.parking

import org.cssnr.parking.data.db.History
import org.cssnr.parking.data.db.LocationAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the label ParKing shows for a record, which is the only part of the
 * reverse geocode that is user visible.
 */
class LocationAddressTest {

    private val record = History(
        timestamp = 0L,
        latitude = 47.7289295,
        longitude = -122.3484597,
        bluetoothAddress = "20:21:A7:E0:02:D5",
    )

    @Test
    fun `displayLabel prefers the provider line`() {
        val address = LocationAddress(
            line = "1600 Amphitheatre Pkwy, Mountain View, CA 94043",
            thoroughfare = "Amphitheatre Pkwy",
            countryName = "United States",
        )
        assertEquals("1600 Amphitheatre Pkwy, Mountain View, CA 94043", address.displayLabel)
    }

    @Test
    fun `displayLabel falls back to components when the provider gave no line`() {
        val address = LocationAddress(
            thoroughfare = "Amphitheatre Pkwy",
            subThoroughfare = "1600",
            locality = "Mountain View",
            adminArea = "CA",
            countryName = "United States",
        )
        assertEquals(
            "Amphitheatre Pkwy, 1600, Mountain View, CA, United States",
            address.displayLabel,
        )
    }

    @Test
    fun `displayLabel keeps the street number and district in the fallback`() {
        // A provider that populates subThoroughfare and subLocality but not line is
        // exactly the case that must not silently lose the number and the district.
        val address = LocationAddress(
            subThoroughfare = "1600",
            subLocality = "Shoreline West",
            countryName = "United States",
        )
        assertEquals("1600, Shoreline West, United States", address.displayLabel)
    }

    @Test
    fun `displayLabel is null when the geocoder returned nothing usable`() {
        assertNull(LocationAddress().displayLabel)
        assertNull(LocationAddress(countryCode = "US").displayLabel)
    }

    @Test
    fun `displayName uses the address when there is one`() {
        val located = record.copy(address = LocationAddress(locality = "Mountain View"))
        assertEquals("Mountain View", located.displayName)
    }

    @Test
    fun `displayName falls back to coordinates and is never blank`() {
        assertEquals("47.72893, -122.34846", record.displayName)
    }
}
