package com.ranit.thingcount.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "counters")
data class Counter(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val currentValue: Long = 0L,
    val step: Long = 1L,
    val startValue: Long = 0L,
    val colorHex: Long = 0xFF1976D2, // Default Blue
    val emoji: String = "🎯",
    val tag: String = "General",
    val isLocked: Boolean = false,
    val autoResetPeriod: ResetPeriod = ResetPeriod.NONE,
    val lastResetTimestamp: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val sortOrder: Int = 0
)
