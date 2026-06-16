package com.raven.veto.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_usage")
data class DailyUsageEntity(
    @PrimaryKey val date: String, // format: YYYY-MM-DD
    val timeEarnedMillis: Long = 0L,
    val timeSpentMillis: Long = 0L,
    val cardsReviewed: Int = 0
)
