package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RamThresholdDetectorTest {

    private lateinit var detector: RamThresholdDetector

    @Before
    fun setUp() {
        detector = RamThresholdDetector(thresholdPercent = 60)
    }

    @Test
    fun `calculateRamPercent calculates percentage correctly`() {
        val total = 10_000_000_000L // 10 GB
        val avail = 4_000_000_000L  // 4 GB free -> 60% used
        val percent = detector.calculateRamPercent(total, avail)
        assertEquals(60, percent)
    }

    @Test
    fun `calculateRamPercent handles zero or negative total memory safely`() {
        assertEquals(0, detector.calculateRamPercent(0L, 0L))
        assertEquals(0, detector.calculateRamPercent(-100L, 50L))
    }

    @Test
    fun `calculateRamPercent coerces out of bounds percentages`() {
        val total = 1_000L
        val availNegative = -500L
        assertEquals(0, detector.calculateRamPercent(0L, availNegative))

        assertEquals(100, detector.calculateRamPercent(total, 0L))
    }

    @Test
    fun `transition from 59 percent to 60 percent triggers alert immediately`() {
        assertFalse("59% should not trigger alert", detector.processReading(59))
        assertTrue("Crossing to 60% must trigger alert immediately", detector.processReading(60))
    }

    @Test
    fun `staying above 60 percent on subsequent readings does not re-trigger alert`() {
        assertTrue("Initial crossing to 62% triggers alert", detector.processReading(62))
        assertFalse("Staying at 65% must NOT trigger alert again", detector.processReading(65))
        assertFalse("Staying at 68% must NOT trigger alert again", detector.processReading(68))
    }

    @Test
    fun `falling below 60 percent re-arms alert for subsequent crossing`() {
        assertTrue("First crossing to 62% triggers alert", detector.processReading(62))
        assertFalse("Staying at 64% no alert", detector.processReading(64))

        // RAM usage drops below 60%
        assertFalse("Dropping to 55% re-arms detector", detector.processReading(55))

        // RAM usage crosses 60% again
        assertTrue("Re-crossing to 61% triggers alert again", detector.processReading(61))
    }

    @Test
    fun `initial reading at or above 60 percent triggers alert once`() {
        assertTrue("Initial reading at 65% triggers alert once", detector.processReading(65))
        assertFalse("Second reading at 66% does not repeat alert", detector.processReading(66))
    }
}
