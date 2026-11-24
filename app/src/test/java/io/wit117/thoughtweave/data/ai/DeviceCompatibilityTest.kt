package io.wit117.thoughtweave.data.ai

import io.wit117.thoughtweave.data.ai.model.meetsAiRequirements
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCompatibilityTest {
    private val gib = 1024L * 1024 * 1024

    @Test
    fun phonesSoldAs6GbOrMoreQualify() {
        // What Android reports: about 90% of the installed RAM (Galaxy S23 Ultra, 12 GB: 11,311,232 kB).
        assertTrue(meetsAiRequirements(true, false, 11_311_232L * 1024))
        assertTrue(meetsAiRequirements(true, false, (5.4 * gib).toLong()))
    }

    @Test
    fun smallerOrLimitedDevicesDoNot() {
        assertFalse(meetsAiRequirements(true, false, (3.6 * gib).toLong()))
        assertFalse(meetsAiRequirements(true, false, 2 * gib))
        assertFalse(meetsAiRequirements(false, false, 8 * gib))
        assertFalse(meetsAiRequirements(true, true, 8 * gib))
    }
}
