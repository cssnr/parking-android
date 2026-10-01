package org.cssnr.parking

import org.cssnr.parking.data.LocationFix
import org.cssnr.parking.data.isFreshEnough
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The age comparison itself needs no test, it is a comparison against a constant.
 * This covers the one branch that is not obvious from reading it: a fix whose age
 * is unknown is rejected.
 */
class FixFreshnessTest {

    @Test
    fun `a fix of unknown age is rejected`() {
        // A missing age means an unrecognised provider, which cannot support a claim
        // about when the position was measured. Dropping this check in a refactor
        // would silently start accepting such fixes, and nothing else would say so.
        val fix = LocationFix(
            latitude = 47.7289373,
            longitude = -122.3484452,
            fixAgeMillis = null,
            accuracy = 11.4f,
            altitude = null,
            verticalAccuracy = null,
        )
        assertFalse(fix.isFreshEnough())
    }
}
