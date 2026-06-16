package com.raven.veto.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raven.veto.data.AnkiRepository
import com.raven.veto.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val ankiRepository: AnkiRepository
) : ViewModel() {

    val exchangeRate: StateFlow<Float> = preferencesManager.defaultExchangeRateFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 1.0f
        )

    val strictModeEnabled: StateFlow<Boolean> = preferencesManager.strictModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun updateExchangeRate(rate: Float) {
        viewModelScope.launch {
            preferencesManager.setDefaultExchangeRate(rate)
        }
    }

    fun updateStrictMode(enabled: Boolean, context: Context, onDenied: () -> Unit) {
        viewModelScope.launch {
            if (!enabled) {
                val stats = ankiRepository.forceCheck()
                if (stats.totalDue > 0) {
                    onDenied()
                    return@launch
                }
            }
            preferencesManager.setStrictMode(enabled)
            if (!enabled) {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val component = android.content.ComponentName(context, com.raven.veto.services.VetoDeviceAdminReceiver::class.java)
                dpm.removeActiveAdmin(component)
            }
        }
    }

    fun setTestTime() {
        viewModelScope.launch {
            // Set to exactly 5.1 minutes
            preferencesManager.setCurrentBalanceMillis(306_000L)
        }
    }
}
