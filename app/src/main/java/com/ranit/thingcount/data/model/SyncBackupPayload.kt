package com.ranit.thingcount.data.model

data class SyncBackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val counters: List<Counter> = emptyList(),
    val history: List<CounterHistory> = emptyList()
)
