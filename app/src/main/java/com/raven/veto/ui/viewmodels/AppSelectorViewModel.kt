package com.raven.veto.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raven.veto.data.AppRepository
import com.raven.veto.data.AppInfo
import com.raven.veto.domain.GetBlockedAppsUseCase
import com.raven.veto.domain.ToggleAppBlockUseCase
import com.raven.veto.data.local.VetoDao
import com.raven.veto.data.local.AppProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSelectorViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val vetoDao: VetoDao,
    private val toggleAppBlockUseCase: ToggleAppBlockUseCase
) : ViewModel() {

    private val _rawApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _searchQuery = MutableStateFlow("")

    val searchQuery: StateFlow<String> = _searchQuery

    val uiState: StateFlow<AppSelectorUiState> = combine(
        _rawApps,
        vetoDao.getAllAppProfilesFlow(),
        _searchQuery,
        _isLoading
    ) { apps, profiles, query, isLoading ->
        val profileMap = profiles.associateBy { it.packageName }
        val filteredApps = if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.name.contains(query, ignoreCase = true) }
        }

        val mappedApps = filteredApps.map { app ->
            val profile = profileMap[app.packageName]
            app.copy(
                isBlocked = profile?.isBlocked == true,
                costMultiplier = profile?.costMultiplier ?: 1.0f
            )
        }

        AppSelectorUiState(
            apps = mappedApps,
            isLoading = isLoading
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
            // Load apps without caring about blocked status initially
            val apps = appRepository.getInstalledApps()
            _rawApps.value = apps
            _isLoading.value = false
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
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

data class AppSelectorUiState(
    val apps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = false
)

