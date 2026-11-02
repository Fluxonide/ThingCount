package com.ranit.thingcount.data.drive

sealed class SyncResult {
    data class Success(val countersSynced: Int, val timestamp: Long) : SyncResult()
    data class Error(val message: String, val throwable: Throwable? = null) : SyncResult()
    object InProgress : SyncResult()
}
