package com.raven.veto.domain

import com.raven.veto.data.local.DailyUsageEntity
import com.raven.veto.data.local.PreferencesManager
import com.raven.veto.data.local.VetoDao
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class AddEarnedTimeUseCase @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val vetoDao: VetoDao
) {
    private fun getTodayKey(): String {
        val now = LocalDateTime.now()
        val adjustedDate = if (now.hour < 4) now.minusDays(1) else now
        return DateTimeFormatter.ISO_LOCAL_DATE.format(adjustedDate)
    }

    suspend operator fun invoke(deckDiffs: Map<Long, Int>) {
        if (deckDiffs.isEmpty()) return

        var totalEarnedMinutes = 0f
        var totalCardsReviewed = 0

        val defaultExchangeRate = preferencesManager.defaultExchangeRateFlow.first()

        for ((deckId, cardsReviewed) in deckDiffs) {
            if (cardsReviewed <= 0) continue

            val deckProfile = vetoDao.getDeckProfile(deckId)
            val rate = deckProfile?.minutesPerCard ?: defaultExchangeRate
            
            totalEarnedMinutes += (cardsReviewed * rate)
            totalCardsReviewed += cardsReviewed
        }

        if (totalCardsReviewed <= 0) return

        val toAddMillis = (totalEarnedMinutes * 60_000L).toLong()

        val currentBalance = preferencesManager.currentBalanceMillisFlow.first()
        val newBalance = currentBalance + toAddMillis
        
        preferencesManager.setCurrentBalanceMillis(newBalance)

        val today = getTodayKey()
        val dailyUsage = vetoDao.getDailyUsage(today) ?: DailyUsageEntity(date = today)
        vetoDao.upsertDailyUsage(
            dailyUsage.copy(
                timeEarnedMillis = dailyUsage.timeEarnedMillis + toAddMillis,
                cardsReviewed = dailyUsage.cardsReviewed + totalCardsReviewed
            )
        )
    }
}
