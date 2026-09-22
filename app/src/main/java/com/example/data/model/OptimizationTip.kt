package com.example.data.model

/**
 * Type of memory optimization recommendation.
 */
enum class OptimizationType {
    CLOSE_BACKGROUND,
    DELETE_UNUSED_FILES,
    CLEAR_CACHE,
    RESTART_PHONE
}

/**
 * Model representing a memory optimization action item.
 */
data class OptimizationTip(
    val type: OptimizationType,
    val title: String,
    val description: String,
    val impactText: String,
    val iconName: String
)
