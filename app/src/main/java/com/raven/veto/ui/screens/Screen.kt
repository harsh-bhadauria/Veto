package com.raven.veto.ui.screens

sealed class Screen {
    object Main : Screen()
    object Settings : Screen()
    object AppSelector : Screen()
    object Permissions : Screen()
    object DeckSettings : Screen()
}
