package com.raven.veto.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_profiles")
data class AppProfileEntity(
    @PrimaryKey val packageName: String,
    val isBlocked: Boolean = false,
    val customExchangeRate: Float? = null // null means use global default
)
