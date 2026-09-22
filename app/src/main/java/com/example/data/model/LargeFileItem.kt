package com.example.data.model

import com.example.util.MemoryFormatter

enum class FileCategory {
    VIDEOS,
    IMAGES,
    AUDIO,
    DOCUMENTS,
    APKS,
    OTHER
}

enum class LargeFileSortOption(val label: String) {
    LARGEST("Largest Size"),
    SMALLEST("Smallest Size"),
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    NAME("Name (A to Z)")
}

/**
 * Unified model representing a real accessible file.
 */
data class LargeFileItem(
    val id: String, // Unique content URI or path string
    val fileName: String,
    val uriString: String,
    val sizeBytes: Long,
    val mimeType: String = "",
    val category: FileCategory = FileCategory.OTHER,
    val modifiedTimeMs: Long = 0L,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val path: String = "",
    val isDeletable: Boolean = true,
    val isSelected: Boolean = false
) {
    val dateFormatted: String
        get() = if (modifiedTimeMs > 0L) MemoryFormatter.formatDate(modifiedTimeMs) else "Date unavailable"
}
