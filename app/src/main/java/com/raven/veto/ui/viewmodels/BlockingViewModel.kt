package com.raven.veto.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.raven.veto.data.AnkiRepository
import com.raven.veto.data.local.PreferencesManager
import com.raven.veto.domain.GetAvailableTimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockingViewModel @Inject constructor(
    private val ankiRepository: AnkiRepository,
    private val getAvailableTimeUseCase: GetAvailableTimeUseCase,
    private val preferencesManager: PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val packageName: String? = savedStateHandle.get<String>("extra_package_name")

    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1)

    // We combine Anki stats with local usage stats
    @OptIn(ExperimentalCoroutinesApi::class)
    val state = refreshTrigger
        .onStart { emit(Unit) }
        .flatMapLatest {
            combine(
                ankiRepository.getAnkiStats(),
                getAvailableTimeUseCase()
            ) { ankiStats, availableMillis ->
                val canUseBypass = packageName != null && !preferencesManager.hasUsedEmergencyBypassToday(packageName)
                BlockingState(
                    isLoading = false,
                    dueCards = ankiStats.totalDue,
                    availableMinutes = (availableMillis / 60_000).toInt(),
                    canUseEmergencyBypass = canUseBypass
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BlockingState(isLoading = true)
        )

    fun refreshUsage() {
        viewModelScope.launch {
            refreshTrigger.emit(Unit)
        }
    }

    fun useEmergencyBypass() {
        viewModelScope.launch {
            packageName?.let { pkg ->
                if (!preferencesManager.hasUsedEmergencyBypassToday(pkg)) {
                    preferencesManager.markEmergencyBypassUsed(pkg)
                    val currentBalance = getAvailableTimeUseCase().first()
                    // Add 2 minutes (120_000 ms)
                    preferencesManager.setCurrentBalanceMillis(currentBalance + 120_000L)
                    // Trigger a refresh so the UI updates and closes
                    refreshTrigger.emit(Unit)
                }
            }
        }
    }
}

data class BlockingState(
    val isLoading: Boolean = true,
    val dueCards: Int = 0,
    val availableMinutes: Int = 0,
    val canUseEmergencyBypass: Boolean = true
)
