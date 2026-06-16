package com.raven.veto.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raven.veto.data.AnkiRepository
import com.raven.veto.data.local.DeckProfileEntity
import com.raven.veto.data.local.PreferencesManager
import com.raven.veto.data.local.VetoDao
import com.raven.veto.ui.uistates.DeckItemUI
import com.raven.veto.ui.uistates.DeckSettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeckSettingsViewModel @Inject constructor(
    private val ankiRepository: AnkiRepository,
    private val vetoDao: VetoDao,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _availableDecks = MutableStateFlow<List<AnkiRepository.DeckInfo>>(emptyList())

    init {
        viewModelScope.launch {
            _availableDecks.value = ankiRepository.getAvailableDecks()
        }
    }

    val uiState = combine(
        _availableDecks,
        vetoDao.getAllDeckProfilesFlow(),
        preferencesManager.defaultExchangeRateFlow
    ) { decks, profiles, defaultRate ->
        val mappedDecks = decks.map { deck ->
            val profile = profiles.find { it.deckId == deck.id }
            DeckItemUI(
                deckId = deck.id,
                name = deck.name,
                specificRate = profile?.minutesPerCard,
                effectiveRate = profile?.minutesPerCard ?: defaultRate
            )
        }
        DeckSettingsUiState(decks = mappedDecks, defaultExchangeRate = defaultRate)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeckSettingsUiState())

    fun updateDeckRate(deckId: Long, name: String, rate: Float?) {
        viewModelScope.launch {
            vetoDao.insertDeckProfile(DeckProfileEntity(deckId, name, rate))
        }
    }
}
