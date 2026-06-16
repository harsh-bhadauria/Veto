package com.raven.veto.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "veto_settings")

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        val DEFAULT_EXCHANGE_RATE = floatPreferencesKey("default_exchange_rate")
        val CURRENT_BALANCE_MILLIS = longPreferencesKey("current_balance_millis")
    }

    val defaultExchangeRateFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_EXCHANGE_RATE] ?: 1.0f // 1 card = 1 minute
    }

    suspend fun setDefaultExchangeRate(rate: Float) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_EXCHANGE_RATE] = rate
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
}
