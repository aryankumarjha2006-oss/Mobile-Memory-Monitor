package com.example.util

/**
 * Pure stateful component responsible for detecting RAM usage percentage
 * threshold crossings with hysteresis re-arming to prevent notification spam.
 */
class RamThresholdDetector(
    val thresholdPercent: Int = 60
) {
    private var hasAlerted: Boolean = false

    /**
     * Calculates RAM usage percentage given total and available memory bytes.
     * Validates inputs to prevent division by zero or out-of-bounds results.
     */
    fun calculateRamPercent(totalBytes: Long, availBytes: Long): Int {
        if (totalBytes <= 0L || availBytes < 0L) return 0
        val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)
        return ((usedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Evaluates a new RAM percentage reading.
     * Returns true if an alert should be triggered immediately upon crossing
     * the threshold for the first time or after re-arming.
     */
    fun processReading(usedPercent: Int): Boolean {
        return if (usedPercent >= thresholdPercent) {
            if (!hasAlerted) {
                hasAlerted = true
                true
            } else {
                false
            }
        } else {
            hasAlerted = false
            false
        }
    }

    /**
     * Resets the internal alert state (re-arms detector).
     */
    fun reset() {
        hasAlerted = false
    }

    /**
     * Returns whether the alert is currently armed/triggered.
     */
    fun isAlerted(): Boolean = hasAlerted
}
