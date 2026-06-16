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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
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
    val ankiStats: StateFlow<AnkiStats> = refreshTrigger
        .onStart { emit(Unit) }
        .flatMapLatest {
            ankiRepository.getAnkiStats()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AnkiStats()
        )

    val availableMinutes: StateFlow<Int> = getAvailableTimeUseCase()
        .map { (it / 60_000).toInt() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val recentUsage: StateFlow<List<DailyUsageEntity>> = vetoDao.getRecentUsageFlow(7)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val lastUpdated: StateFlow<Long> = refreshTrigger
        .map { java.time.Instant.now().toEpochMilli() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    fun fetchAnkiData() {
        Log.d("VetoAnki", "fetchAnkiData called")
        viewModelScope.launch {
            refreshTrigger.emit(Unit)
        }
    }
}
