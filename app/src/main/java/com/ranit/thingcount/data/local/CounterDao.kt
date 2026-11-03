package com.ranit.thingcount.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ranit.thingcount.data.model.Counter
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterDao {

    @Query("SELECT * FROM counters WHERE isDeleted = 0 ORDER BY sortOrder ASC, rowid ASC")
    fun getAllActiveCounters(): Flow<List<Counter>>

    @Query("SELECT * FROM counters")
    suspend fun getAllCountersRaw(): List<Counter>

    @Query("SELECT * FROM counters WHERE id = :id LIMIT 1")
    fun getCounterById(id: String): Flow<Counter?>

    @Query("SELECT * FROM counters WHERE id = :id LIMIT 1")
    suspend fun getCounterByIdSync(id: String): Counter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(counter: Counter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(counters: List<Counter>)

    @Query("UPDATE counters SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM counters WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT DISTINCT tag FROM counters WHERE isDeleted = 0")
    fun getAllTags(): Flow<List<String>>
}
