package com.raven.veto.ui.uistates

// No import needed

data class DeckItemUI(
    val deckId: Long,
    val name: String,
    val specificRate: Float?,
    val effectiveRate: Float
)

data class DeckSettingsUiState(
    val decks: List<DeckItemUI> = emptyList(),
    val defaultExchangeRate: Float = 1.0f
)
