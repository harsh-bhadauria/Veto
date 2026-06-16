package com.raven.veto.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deck_profiles")
data class DeckProfileEntity(
    @PrimaryKey
    val deckId: Long,
    val name: String,
    val minutesPerCard: Float? = null
)
