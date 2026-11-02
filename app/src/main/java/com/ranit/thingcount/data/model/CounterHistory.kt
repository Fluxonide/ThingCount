package com.ranit.thingcount.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "counter_history",
    foreignKeys = [
        ForeignKey(
            entity = Counter::class,
            parentColumns = ["id"],
            childColumns = ["counterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["counterId"])]
)
data class CounterHistory(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val counterId: String,
    val change: Long, // e.g. +1, -1, or difference on reset
    val resultingValue: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null
)
