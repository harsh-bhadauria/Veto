package com.raven.veto.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raven.veto.data.AppRepository
import com.raven.veto.data.AppInfo
import com.raven.veto.domain.GetBlockedAppsUseCase
import com.raven.veto.domain.ToggleAppBlockUseCase
import com.raven.veto.data.local.VetoDao
import com.raven.veto.data.local.AppProfileEntity
import com.raven.veto.data.AnkiRepository
import com.raven.veto.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import com.raven.veto.ui.uistates.AppSelectorUiState

@HiltViewModel
class AppSelectorViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val vetoDao: VetoDao,
    private val toggleAppBlockUseCase: ToggleAppBlockUseCase,
    private val ankiRepository: AnkiRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _rawApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<AppSelectorUiState> = combine(
        _rawApps,
        vetoDao.getAllAppProfilesFlow(),
        preferencesManager.strictModeFlow,
        ankiRepository.getAnkiStats(),
        _isLoading
    ) { apps, profiles, strictMode, stats, isLoading ->
        val profileMap = profiles.associateBy { it.packageName }
        
        val mappedApps = apps.map { app ->
            val profile = profileMap[app.packageName]
            app.copy(
                isBlocked = profile?.isBlocked == true,
                costMultiplier = profile?.costMultiplier ?: 1.0f
            )
        }

        AppSelectorUiState(
            apps = mappedApps,
            isLoading = isLoading,
            strictModeEnabled = strictMode,
            totalDueCards = stats.totalDue
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSelectorUiState(isLoading = true)
    )

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = appRepository.getInstalledApps()
            _rawApps.value = apps
            _isLoading.value = false
        }
    }

    fun toggleAppBlock(packageName: String) {
        viewModelScope.launch {
            toggleAppBlockUseCase(packageName)
        }
    }

    fun updateAppMultiplier(packageName: String, multiplier: Float) {
        viewModelScope.launch {
            val profile = vetoDao.getAppProfile(packageName) ?: AppProfileEntity(packageName)
            vetoDao.upsertAppProfile(profile.copy(costMultiplier = multiplier))
        }
    }
}

