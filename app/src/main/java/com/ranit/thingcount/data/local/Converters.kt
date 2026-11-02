package com.ranit.thingcount.data.local

import androidx.room.TypeConverter
import com.ranit.thingcount.data.model.ResetPeriod

class Converters {
    @TypeConverter
    fun fromResetPeriod(period: ResetPeriod): String = period.name

    @TypeConverter
    fun toResetPeriod(value: String?): ResetPeriod {
        return value?.let {
            try {
                ResetPeriod.valueOf(it)
            } catch (e: Exception) {
                ResetPeriod.NONE
            }
        } ?: ResetPeriod.NONE
    }
}
