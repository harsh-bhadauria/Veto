package com.raven.veto.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raven.veto.data.AppRepository
import com.raven.veto.data.AppInfo
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
    private val appRepository: AppRepository
) : ViewModel() {

    private val _rawApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _searchQuery = MutableStateFlow("")

    val searchQuery: StateFlow<String> = _searchQuery

    val uiState: StateFlow<AppSelectorUiState> = combine(
        _rawApps,
        appRepository.blockedAppsFlow,
        _searchQuery,
        _isLoading
    ) { apps, blockedSet, query, isLoading ->
        val filteredApps = if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.name.contains(query, ignoreCase = true) }
        }

        val mappedApps = filteredApps.map { app ->
            app.copy(isBlocked = blockedSet.contains(app.packageName))
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
        val currentBlocked = appRepository.blockedAppsFlow
        // We just toggle in repo, flow will update UI
        appRepository.toggleAppBlock(packageName)
    }
}

data class AppSelectorUiState(
    val apps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = false
)

