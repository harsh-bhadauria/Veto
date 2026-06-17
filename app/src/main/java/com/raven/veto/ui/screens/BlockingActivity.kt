package com.raven.veto.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.sp
import com.raven.veto.ui.theme.AnkiReviewGreen
import com.raven.veto.ui.theme.VetoMint20
import com.raven.veto.ui.theme.VetoMint40
import com.raven.veto.ui.theme.VetoTheme
import com.raven.veto.ui.viewmodels.BlockingViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlockingActivity : ComponentActivity() {

    private val viewModel: BlockingViewModel by viewModels()

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_DUE_CARDS = "extra_due_cards"
        private const val EXTRA_HIDE_EMERGENCY_BYPASS = "extra_hide_emergency_bypass"

        fun createIntent(
            context: Context,
            packageName: String,
            dueCards: Int,
            hideEmergencyBypass: Boolean = false
        ): Intent {
            return Intent(context, BlockingActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_DUE_CARDS, dueCards)
                putExtra(EXTRA_HIDE_EMERGENCY_BYPASS, hideEmergencyBypass)
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

                val hideEmergencyBypass = intent.getBooleanExtra(EXTRA_HIDE_EMERGENCY_BYPASS, false)

                // Automatically close if unblocked based on block type
                LaunchedEffect(state.availableMinutes, state.dueCards, state.isLoading) {
                    if (state.isLoading) return@LaunchedEffect
                    
                    if (hideEmergencyBypass) {
                        // Strict mode block: only close if due cards hit 0
                        if (state.dueCards == 0) {
                            finish()
                        }
                    } else {
                        // Normal block: close if available minutes > 0
                        if (state.availableMinutes > 0) {
                            finish()
                        }
                    }
                }

                // Handle back press to go home instead of back
                BackHandler {
                    goHome()
                }



                BlockingScreen(
                    dueCards = state.dueCards,
                    availableMinutes = state.availableMinutes,
                    hideEmergencyBypass = hideEmergencyBypass,
                    canUseEmergencyBypass = state.canUseEmergencyBypass,
                    onOpenAnki = {
                        val launchIntent =
                            packageManager.getLaunchIntentForPackage("com.ichi2.anki")
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        }
                    },
                    onEmergencyBypass = {
                        viewModel.useEmergencyBypass()
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
    hideEmergencyBypass: Boolean,
    canUseEmergencyBypass: Boolean,
    onOpenAnki: () -> Unit,
    onEmergencyBypass: () -> Unit,
    onClose: () -> Unit
) {
    val isUnlocked = availableMinutes > 0
    val isDarkMode = isSystemInDarkTheme()

    // Completely darker, modern aesthetic for the blocked state
    val blockedBgColor = Color(0xFF0F0F13) // Very dark, almost black with a tint
    val surfaceBgColor = MaterialTheme.colorScheme.surface

    val scaffoldBackgroundColor = if (isUnlocked) surfaceBgColor else blockedBgColor
    val textPrimary = if (isUnlocked) MaterialTheme.colorScheme.onSurface else Color.White
    val textSecondary = if (isUnlocked) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFA0A0A5)

    // Mint colors for unlocked state
    val unlockedMintColor = if (isDarkMode) AnkiReviewGreen else VetoMint40

    Scaffold(
        containerColor = scaffoldBackgroundColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            if (isUnlocked) {
                // Unlocked state - Happy message
                Text(
                    text = "🎉",
                    style = MaterialTheme.typography.displayLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "You're Free!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = unlockedMintColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$availableMinutes minutes",
                        style = MaterialTheme.typography.displaySmall,
                        color = unlockedMintColor,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "available now",
                        style = MaterialTheme.typography.bodyLarge,
                        color = unlockedMintColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Blocked state - Sleek Dark UI
                Text(
                    text = "🐈‍⬛", // Black cat for dark theme vibes
                    style = MaterialTheme.typography.displayLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Blocked.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = textPrimary,
                    fontWeight = FontWeight.Black
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Finish your Anki reviews to unlock this app.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = textSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(32.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF1C1C22),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$dueCards",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color(0xFFFF5252), // A striking red for urgency
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "CARDS DUE",
                        style = MaterialTheme.typography.labelLarge,
                        color = textSecondary,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onOpenAnki,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isUnlocked)
                            MaterialTheme.colorScheme.primary
                        else
                            Color(0xFF2E2E36) // Dark grey button
                    )
                ) {
                    Text(
                        text = if (isUnlocked) "Open AnkiDroid Anyway" else "Open AnkiDroid",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isUnlocked) {
                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = "Close",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (!hideEmergencyBypass && canUseEmergencyBypass) {
                    Button(
                        onClick = onEmergencyBypass,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x22FFFFFF) // Translucent for secondary action
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Text(
                            text = "Emergency 2-Minute Bypass",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFA0A0A5) // Muted text color
                        )
                    }
                }
            }
        }
    }
}
