package com.motebaya.vaulten.data.local

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Type converters for Room database.
 */
class Converters {
    
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }
    
    @TypeConverter
    fun toInstant(epochMilli: Long?): Instant? {
        return epochMilli?.let { Instant.ofEpochMilli(it) }
    }
}
