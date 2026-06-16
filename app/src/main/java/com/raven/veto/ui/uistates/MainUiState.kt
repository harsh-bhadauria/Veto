package com.raven.veto.ui.uistates

import com.raven.veto.data.AnkiStats
import com.raven.veto.data.local.DailyUsageEntity

data class MainUiState(
    val availableMinutes: Long = 0L,
    val ankiStats: AnkiStats = AnkiStats(0, 0, 0, 0),
    val lastUpdated: Long = 0L,
    val recentUsage: List<DailyUsageEntity> = emptyList()
)
