package com.example.data.model

/**
 * Snapshot model representing real Android BatteryManager telemetry.
 */
data class BatterySnapshot(
    val percentage: Int = -1,
    val isCharging: Boolean = false,
    val temperatureCelsius: Float = -1.0f,
    val voltageVolts: Float = -1.0f,
    val currentMicroAmperes: Int = 0,
    val healthStatus: String = "Good",
    val technology: String = "Li-ion",
    val statusLabel: String = "Discharging",
    val plugSource: String = "Battery"
)
