package com.ranit.thingcount.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ranit.thingcount.data.model.CounterHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterHistoryDao {

    @Query("SELECT * FROM counter_history WHERE counterId = :counterId ORDER BY timestamp DESC")
    fun getHistoryForCounter(counterId: String): Flow<List<CounterHistory>>

    @Query("SELECT * FROM counter_history ORDER BY timestamp DESC")
    suspend fun getAllHistoryRaw(): List<CounterHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: CounterHistory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(histories: List<CounterHistory>)

    @Query("DELETE FROM counter_history WHERE counterId = :counterId")
    suspend fun clearHistoryForCounter(counterId: String)
}
