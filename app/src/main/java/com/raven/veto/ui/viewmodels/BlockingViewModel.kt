package com.raven.veto.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raven.veto.data.AnkiRepository
import com.raven.veto.data.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockingViewModel @Inject constructor(
    private val ankiRepository: AnkiRepository,
    private val appRepository: AppRepository
) : ViewModel() {

    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1)

    // We combine Anki stats with local usage stats
    @OptIn(ExperimentalCoroutinesApi::class)
    val state = refreshTrigger
        .onStart { emit(Unit) }
        .flatMapLatest {
            combine(
                ankiRepository.getAnkiStats(),
                appRepository.availableTimeFlow
            ) { ankiStats, availableMillis ->
                BlockingState(
                    dueCards = ankiStats.totalDue,
                    availableMinutes = (availableMillis / 60_000).toInt()
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BlockingState()
        )

    fun refreshUsage() {
        viewModelScope.launch {
            refreshTrigger.emit(Unit)
        }
    }
}

data class BlockingState(
    val dueCards: Int = 0,
    val availableMinutes: Int = 0
)
