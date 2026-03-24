package com.raven.veto.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raven.veto.ui.theme.VetoMint20
import com.raven.veto.ui.theme.VetoMint40
import com.raven.veto.ui.theme.VetoTheme
import com.raven.veto.ui.theme.AnkiReviewGreen
import com.raven.veto.ui.viewmodels.BlockingViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlockingActivity : ComponentActivity() {

    private val viewModel: BlockingViewModel by viewModels()

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_DUE_CARDS = "extra_due_cards"

        fun createIntent(
            context: Context,
            packageName: String,
            dueCards: Int
        ): Intent {
            return Intent(context, BlockingActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_DUE_CARDS, dueCards)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VetoTheme {
                val state by viewModel.state.collectAsState()

                // Automatically close if unblocked (available > 0)
                LaunchedEffect(state.availableMinutes) {
                    if (state.availableMinutes > 0) {
                        finish()
                    }
                }

                // Handle back press to go home instead of back
                BackHandler {
                    goHome()
                }

                BlockingScreen(
                    dueCards = state.dueCards,
                    availableMinutes = state.availableMinutes,
                    onOpenAnki = {
                        val launchIntent =
                            packageManager.getLaunchIntentForPackage("com.ichi2.anki")
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        }
                    },
                    onClose = {
                        goHome()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshUsage()
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
fun BlockingScreen(
    dueCards: Int,
    availableMinutes: Int,
    onOpenAnki: () -> Unit,
    onClose: () -> Unit
) {
    val isUnlocked = availableMinutes > 0
    val isDarkMode = isSystemInDarkTheme()

    // Use dark mint background for blocked state, keep surface for unlocked
    val scaffoldBackgroundColor = if (isUnlocked) {
        MaterialTheme.colorScheme.surface
    } else {
        if (isDarkMode) VetoMint20 else MaterialTheme.colorScheme.surface
    }

    // Mint colors for unlocked state
    val unlockedMintColor = if (isDarkMode) AnkiReviewGreen else VetoMint40

    Scaffold(
        containerColor = scaffoldBackgroundColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (isUnlocked) {
                // Unlocked state - Happy message
                Text(
                    text = "🎉",
                    style = MaterialTheme.typography.displayLarge
                )

                Text(
                    text = "You're Free!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = unlockedMintColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$availableMinutes minutes",
                        style = MaterialTheme.typography.displaySmall,
                        color = unlockedMintColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "available now",
                        style = MaterialTheme.typography.bodyMedium,
                        color = unlockedMintColor
                    )
                }
            } else {
                // Blocked state - Veto is watching
                Text(
                    text = "🐱",
                    style = MaterialTheme.typography.displayLarge
                )

                Text(
                    text = "Hold on!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isDarkMode) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isDarkMode) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$dueCards cards",
                        style = MaterialTheme.typography.displaySmall,
                        color = if (isDarkMode) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "due in AnkiDroid",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkMode) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Finish your reviews to unlock this app.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = if (isDarkMode) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onOpenAnki,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isUnlocked)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(
                        if (isUnlocked) "Open AnkiDroid Anyway" else "Open AnkiDroid",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                    )
                }

                if (isUnlocked) {
                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
