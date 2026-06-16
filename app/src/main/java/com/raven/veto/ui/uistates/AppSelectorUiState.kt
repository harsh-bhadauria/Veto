package com.raven.veto.ui.uistates

import com.raven.veto.data.AppInfo

data class AppSelectorUiState(
    val apps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = true,
    val strictModeEnabled: Boolean = false,
    val totalDueCards: Int = 0
)
