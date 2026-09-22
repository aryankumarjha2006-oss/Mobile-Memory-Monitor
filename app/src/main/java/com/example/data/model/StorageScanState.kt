package com.example.data.model

/**
 * Represents the logical state of a storage scanning/refresh operation.
 */
sealed class StorageScanState {
    object Idle : StorageScanState()
    object Scanning : StorageScanState()
    object Success : StorageScanState()
    data class Error(val message: String) : StorageScanState()
}
