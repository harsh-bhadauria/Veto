package com.raven.veto.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raven.veto.data.AnkiRepository
import com.raven.veto.data.AnkiStats
import com.raven.veto.data.local.VetoDao
import com.raven.veto.data.local.DailyUsageEntity
import com.raven.veto.domain.GetAvailableTimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import com.raven.veto.ui.uistates.MainUiState
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val ankiRepository: AnkiRepository,
    private val getAvailableTimeUseCase: GetAvailableTimeUseCase,
    private val vetoDao: VetoDao
) : ViewModel() {

    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MainUiState> = combine(
        refreshTrigger
            .onStart { emit(Unit) }
            .flatMapLatest { ankiRepository.getAnkiStats() },
        getAvailableTimeUseCase().map { (it / 60_000).toLong() },
        vetoDao.getRecentUsageFlow(7),
        refreshTrigger.map { java.time.Instant.now().toEpochMilli() }.onStart { emit(0L) }
    ) { stats, minutes, usage, updated ->
        MainUiState(
            availableMinutes = minutes,
            ankiStats = stats,
            lastUpdated = updated,
            recentUsage = usage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun fetchAnkiData() {
        Log.d("VetoAnki", "fetchAnkiData called")
        viewModelScope.launch {
            refreshTrigger.emit(Unit)
        }
    }
}
