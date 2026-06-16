package com.raven.veto.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.raven.veto.ui.screens.AppSelectorScreen
import com.raven.veto.ui.screens.DeckSettingsScreen
import com.raven.veto.ui.screens.MainScreen
import com.raven.veto.ui.screens.PermissionsScreen
import com.raven.veto.ui.screens.Screen
import com.raven.veto.ui.screens.SettingsScreen

@Composable
fun VetoApp() {
    val backstack = remember { mutableStateListOf<Screen>(Screen.Main) }

    val top = backstack.lastOrNull() ?: Screen.Main

    val contentBackstack = remember(backstack.toList()) {
        backstack.toList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = contentBackstack,
            onBack = {
                backstack.removeLastOrNull()
            },
            entryProvider = entryProvider {
                entry<Screen.Main> {
                    MainScreen(
                        onNavigateTo = { screen -> backstack.add(screen) }
                    )
                }
                entry<Screen.Settings> {
                    SettingsScreen(
                        onNavigateBack = { backstack.removeLastOrNull() },
                        onNavigateToDeckSettings = { backstack.add(Screen.DeckSettings) }
                    )
                }
                entry<Screen.AppSelector> {
                    AppSelectorScreen(
                        onNavigateBack = { backstack.removeLastOrNull() }
                    )
                }
                entry<Screen.Permissions> {
                    PermissionsScreen(
                        onNavigateBack = { backstack.removeLastOrNull() }
                    )
                }
                entry<Screen.DeckSettings> {
                    DeckSettingsScreen(
                        onNavigateBack = { backstack.removeLastOrNull() }
                    )
                }
            }
        )
    }
}
