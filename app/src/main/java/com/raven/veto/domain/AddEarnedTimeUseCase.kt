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

    suspend operator fun invoke(cardsReviewed: Int) {
        if (cardsReviewed <= 0) return

        val exchangeRate = preferencesManager.defaultExchangeRateFlow.first()
        val earnedMinutes = (cardsReviewed * exchangeRate).toLong()
        val toAddMillis = earnedMinutes * 60_000L

        val currentBalance = preferencesManager.currentBalanceMillisFlow.first()
        val newBalance = currentBalance + toAddMillis
        
        preferencesManager.setCurrentBalanceMillis(newBalance)

        val today = getTodayKey()
        val dailyUsage = vetoDao.getDailyUsage(today) ?: DailyUsageEntity(date = today)
        vetoDao.upsertDailyUsage(
            dailyUsage.copy(
                timeEarnedMillis = dailyUsage.timeEarnedMillis + toAddMillis,
                cardsReviewed = dailyUsage.cardsReviewed + cardsReviewed
            )
        )
    }
}
