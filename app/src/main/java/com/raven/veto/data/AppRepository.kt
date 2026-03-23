package com.raven.veto.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("veto_filters", Context.MODE_PRIVATE)
    private val BLOCKED_APPS_KEY = "blocked_apps_set"
    private val USAGE_DATE_KEY = "usage_date"
    private val AVAILABLE_MILLIS_KEY = "available_millis_balance"

    private val _blockedAppsFlow = MutableStateFlow<Set<String>>(getBlockedAppsFromPrefs())
    val blockedAppsFlow: StateFlow<Set<String>> = _blockedAppsFlow.asStateFlow()

    private val _availableTimeFlow = MutableStateFlow(getAvailableTimeMillis())
    val availableTimeFlow: StateFlow<Long> = _availableTimeFlow.asStateFlow()

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        // Query for launchable apps
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)

        val apps = pm.queryIntentActivities(intent, 0)

        val blocked = _blockedAppsFlow.value

        apps.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)

            AppInfo(
                name = label,
                packageName = packageName,
                icon = icon,
                isBlocked = blocked.contains(packageName)
            )
        }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
    }

    fun toggleAppBlock(packageName: String) {
        val current = _blockedAppsFlow.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }

        // Save to prefs
        prefs.edit { putStringSet(BLOCKED_APPS_KEY, current) }

        // Update flow
        _blockedAppsFlow.update { current }
    }

    // --- TIME BALANCE LOGIC ---

    fun getAvailableTimeMillis(): Long {
        synchronized(this) {
            val today = getTodayKey()
            val lastDate = prefs.getString(USAGE_DATE_KEY, "")

            // If new day (after 4 AM), reset balance to 0 (or keep? User said simple tracking).
            // Logic: "Doing cards increases that, using blocked apps decreases that."
            // If we reset to 0, earned time yesterday is lost. Usually desired.
            if (lastDate != today) {
                prefs.edit {
                    putString(USAGE_DATE_KEY, today)
                    putLong(AVAILABLE_MILLIS_KEY, 0L)
                }
                return 0L
            }
            return prefs.getLong(AVAILABLE_MILLIS_KEY, 0L)
        }
    }

    fun addEarnedTime(minutes: Int) {
        if (minutes <= 0) return
        synchronized(this) {
            val current = getAvailableTimeMillis()
            val toAdd = minutes * 60_000L
            val newBalance = current + toAdd
            prefs.edit { putLong(AVAILABLE_MILLIS_KEY, newBalance) }
            _availableTimeFlow.update { newBalance }
        }
    }

    fun consumeTime(millis: Long) {
        if (millis <= 0) return
        synchronized(this) {
            val current = getAvailableTimeMillis()
            val newBalance = (current - millis).coerceAtLeast(0L)
            prefs.edit { putLong(AVAILABLE_MILLIS_KEY, newBalance) }
            _availableTimeFlow.update { newBalance }
        }
    }

    private fun getBlockedAppsFromPrefs(): Set<String> {
        return prefs.getStringSet(BLOCKED_APPS_KEY, emptySet()) ?: emptySet()
    }

    private fun getTodayKey(): String {
        val now = java.time.LocalDateTime.now()
        val adjustedDate = if (now.hour < 4) now.minusDays(1) else now
        return java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.format(adjustedDate)
    }
}
