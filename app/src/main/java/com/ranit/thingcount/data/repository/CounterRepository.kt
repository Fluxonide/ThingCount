package com.ranit.thingcount.data.repository

import com.ranit.thingcount.data.local.AppDatabase
import com.ranit.thingcount.data.model.Counter
import com.ranit.thingcount.data.model.CounterHistory
import com.ranit.thingcount.data.model.ResetPeriod
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class CounterRepository(private val database: AppDatabase) {

    val activeCounters: Flow<List<Counter>> = database.counterDao().getAllActiveCounters()
    val allTags: Flow<List<String>> = database.counterDao().getAllTags()

    fun getCounter(id: String): Flow<Counter?> = database.counterDao().getCounterById(id)

    fun getHistory(counterId: String): Flow<List<CounterHistory>> =
        database.counterHistoryDao().getHistoryForCounter(counterId)

    suspend fun increment(counterId: String) {
        val counter = database.counterDao().getCounterByIdSync(counterId) ?: return
        if (counter.isLocked) return

        val newValue = counter.currentValue + counter.step
        val now = System.currentTimeMillis()

        database.counterDao().upsert(
            counter.copy(
                currentValue = newValue,
                updatedAt = now
            )
        )

        database.counterHistoryDao().upsert(
            CounterHistory(
                counterId = counterId,
                change = counter.step,
                resultingValue = newValue,
                timestamp = now
            )
        )
    }

    suspend fun decrement(counterId: String) {
        val counter = database.counterDao().getCounterByIdSync(counterId) ?: return
        if (counter.isLocked) return

        val newValue = counter.currentValue - counter.step
        val now = System.currentTimeMillis()

        database.counterDao().upsert(
            counter.copy(
                currentValue = newValue,
                updatedAt = now
            )
        )

        database.counterHistoryDao().upsert(
            CounterHistory(
                counterId = counterId,
                change = -counter.step,
                resultingValue = newValue,
                timestamp = now
            )
        )
    }

    suspend fun reset(counterId: String) {
        val counter = database.counterDao().getCounterByIdSync(counterId) ?: return
        val change = counter.startValue - counter.currentValue
        val now = System.currentTimeMillis()

        database.counterDao().upsert(
            counter.copy(
                currentValue = counter.startValue,
                lastResetTimestamp = now,
                updatedAt = now
            )
        )

        database.counterHistoryDao().upsert(
            CounterHistory(
                counterId = counterId,
                change = change,
                resultingValue = counter.startValue,
                timestamp = now,
                note = "Manual Reset"
            )
        )
    }

    suspend fun toggleLock(counterId: String) {
        val counter = database.counterDao().getCounterByIdSync(counterId) ?: return
        val now = System.currentTimeMillis()
        database.counterDao().upsert(
            counter.copy(
                isLocked = !counter.isLocked,
                updatedAt = now
            )
        )
    }

    suspend fun saveCounter(counter: Counter) {
        database.counterDao().upsert(
            counter.copy(updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteCounter(counterId: String) {
        database.counterDao().softDelete(counterId)
    }

    suspend fun checkAutoResets() {
        val counters = database.counterDao().getAllCountersRaw()
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        counters.filter { !it.isDeleted && it.autoResetPeriod != ResetPeriod.NONE }.forEach { counter ->
            val lastReset = counter.lastResetTimestamp ?: counter.updatedAt
            calendar.timeInMillis = lastReset
            val lastDay = calendar.get(Calendar.DAY_OF_YEAR)
            val lastWeek = calendar.get(Calendar.WEEK_OF_YEAR)
            val lastMonth = calendar.get(Calendar.MONTH)
            val lastYear = calendar.get(Calendar.YEAR)

            val currentCal = Calendar.getInstance().apply { timeInMillis = now }
            val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
            val currentWeek = currentCal.get(Calendar.WEEK_OF_YEAR)
            val currentMonth = currentCal.get(Calendar.MONTH)
            val currentYear = currentCal.get(Calendar.YEAR)

            val shouldReset = when (counter.autoResetPeriod) {
                ResetPeriod.DAILY -> currentDay != lastDay || currentYear != lastYear
                ResetPeriod.WEEKLY -> currentWeek != lastWeek || currentYear != lastYear
                ResetPeriod.MONTHLY -> currentMonth != lastMonth || currentYear != lastYear
                ResetPeriod.NONE -> false
            }

            if (shouldReset && counter.currentValue != counter.startValue) {
                val change = counter.startValue - counter.currentValue
                database.counterDao().upsert(
                    counter.copy(
                        currentValue = counter.startValue,
                        lastResetTimestamp = now,
                        updatedAt = now
                    )
                )
                database.counterHistoryDao().upsert(
                    CounterHistory(
                        counterId = counter.id,
                        change = change,
                        resultingValue = counter.startValue,
                        timestamp = now,
                        note = "Auto Reset (${counter.autoResetPeriod.name})"
                    )
                )
            }
        }
    }
}
