package com.example.data.model

/**
 * Model representing live battery status and diagnostics.
 */
data class BatteryStats(
    val percentage: Int = 0,
    val isCharging: Boolean = false,
    val temperatureCelsius: Float = 0f,
    val voltageVolts: Float = 0f,
    val technology: String = "Li-ion",
    val healthStatus: String = "Good"
)
