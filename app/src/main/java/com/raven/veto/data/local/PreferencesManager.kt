package com.raven.veto.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "veto_settings")

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private val CUSTOM_EXCHANGE_RATE = floatPreferencesKey("custom_exchange_rate")
        private val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val CURRENT_BALANCE_MILLIS = longPreferencesKey("current_balance_millis")
        private val LAST_DAILY_ALLOWANCE_DATE = androidx.datastore.preferences.core.stringPreferencesKey("last_daily_allowance_date")
    }

    val defaultExchangeRateFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_EXCHANGE_RATE] ?: 1.0f
    }

    suspend fun setDefaultExchangeRate(rate: Float) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_EXCHANGE_RATE] = rate
        }
    }

    val strictModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[STRICT_MODE] ?: false
    }

    suspend fun setStrictMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[STRICT_MODE] = enabled
        }
    }

    val currentBalanceMillisFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[CURRENT_BALANCE_MILLIS] ?: 0L
    }

    suspend fun setCurrentBalanceMillis(balance: Long) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_BALANCE_MILLIS] = balance
        }
    }

    suspend fun applyDailyAllowanceIfNeeded() {
        val now = LocalDateTime.now()
        val adjustedDate = if (now.hour < 4) now.minusDays(1) else now
        val todayStr = DateTimeFormatter.ISO_LOCAL_DATE.format(adjustedDate)

        val prefs = context.dataStore.data.first()
        val lastDate = prefs[LAST_DAILY_ALLOWANCE_DATE] ?: ""

        if (lastDate != todayStr) {
            val currentBalance = prefs[CURRENT_BALANCE_MILLIS] ?: 0L
            // convert to minutes to make math easier, avoiding negative issues if balance < 0
            val currentBalanceMins = Math.max(0L, currentBalance / 60000L)
            
            // Cap rollover at 10 minutes
            val rolloverMins = minOf(10L, currentBalanceMins)
            // Add 10 new daily minutes
            val newBalanceMins = rolloverMins + 10L
            
            context.dataStore.edit { preferences ->
                preferences[CURRENT_BALANCE_MILLIS] = newBalanceMins * 60000L
                preferences[LAST_DAILY_ALLOWANCE_DATE] = todayStr
            }
        }
    }
}
