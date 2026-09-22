package com.example.data.model

enum class AppSortOption(val label: String) {
    LARGEST("Largest Size"),
    LARGEST_CACHE("Largest Cache"),
    SMALLEST("Smallest Size"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    RECENTLY_INSTALLED("Recently Installed"),
    RECENTLY_USED("Recently Used")
}
