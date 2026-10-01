package org.cssnr.parking

import org.cssnr.parking.data.LocationFix
import org.cssnr.parking.data.isFreshEnough
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the gate that decides whether a fix that arrived is allowed to be
 * recorded as the parking position.
 *
 * This gate is the real defence, not the request it sits behind. Asking the
 * provider for a freshly derived location is not a promise: its own documentation
 * for `CurrentLocationRequest.setMaxUpdateAgeMillis` notes that "it is possible
 * under unlikely conditions for location derivation to take longer than expected,
 * in which case freshly derived locations may have slightly older timestamps". On
 * device that is not theoretical: a request made with the age limit at zero came
 * back with a fix 181268ms old, which is a position from before the drive.
 *
 * The ages below are the ones that were actually observed on device, not invented
 * boundary values, because the whole justification for the gate is those
 * measurements.
 */
class FixFreshnessTest {

    private fun fix(ageMillis: Long?) = LocationFix(
        latitude = 47.7289373,
        longitude = -122.3484452,
        fixAgeMillis = ageMillis,
        accuracy = 11.4f,
        altitude = null,
        verticalAccuracy = null,
    )

    @Test
    fun `a fix measured at the moment of the disconnect is accepted`() {
        assertTrue(fix(0L).isFreshEnough())
    }

    @Test
    fun `the three minute fix observed on device is rejected`() {
        assertFalse(fix(181_268L).isFreshEnough())
    }

    @Test
    fun `a fix of unknown age is rejected`() {
        // A missing age means an unrecognised provider, which cannot support a
        // claim about when the position was measured. The manual save path also
        // produces a fix with no age, and it never goes through this gate.
        assertFalse(fix(null).isFreshEnough())
    }

    @Test
    fun `the boundary between accepted and rejected`() {
        assertTrue(fix(29_999L).isFreshEnough())
        assertFalse(fix(30_001L).isFreshEnough())
    }
}
