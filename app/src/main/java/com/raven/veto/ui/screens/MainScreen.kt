package com.raven.veto.ui.screens


import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.raven.veto.ui.components.StatItem
import com.raven.veto.ui.components.HeroCard
import com.raven.veto.ui.components.LocalHeroTextColor
import com.raven.veto.ui.components.TimeStatusCard
import com.raven.veto.ui.theme.AnkiNewBlue
import com.raven.veto.ui.theme.AnkiLearnRed
import com.raven.veto.ui.theme.AnkiReviewGreen
import com.raven.veto.ui.theme.vetoColors
import com.raven.veto.ui.viewmodels.MainViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel<MainViewModel>()) {
    var showAppSelector by remember { mutableStateOf(false) }
    var showPermissionsScreen by remember { mutableStateOf(false) }
    var isAnkiGranted by remember { mutableStateOf(false) }
    var isNotificationGranted by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }

    if (showAppSelector) {
        AppSelectorScreen(onNavigateBack = { showAppSelector = false })
        return
    }

    if (showPermissionsScreen) {
        PermissionsScreen(onNavigateBack = { showPermissionsScreen = false })
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ankiStats by viewModel.ankiStats.collectAsState()
    val availableMinutes by viewModel.availableMinutes.collectAsState()
    val lastUpdated by viewModel.lastUpdated.collectAsState()
    val permission = "com.ichi2.anki.permission.READ_WRITE_DATABASE"

    // Check permissions
    fun checkPermissions() {
        isAnkiGranted = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        isAccessibilityEnabled = isAccessibilityServiceEnabledForVeto(context)
    }

    val allPermissionsGranted = isAnkiGranted && isNotificationGranted && isAccessibilityEnabled

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.fetchAnkiData()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    // Initial permission request
    LaunchedEffect(Unit) {
        checkPermissions()

        if (ContextCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(permission)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Refresh data on RESUME
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
                if (ContextCompat.checkSelfPermission(
                        context,
                        permission
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    viewModel.fetchAnkiData()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Hero Card - Main Stat
            HeroCard(
                title = "Cards Due",
                subtitle = if (ankiStats.totalDue > 0) "Time to practice!" else "All caught up!",
                modifier = Modifier.fillMaxWidth(),
                content = {
                    Text(
                        text = NumberFormat.getInstance().format(ankiStats.totalDue),
                        style = MaterialTheme.typography.displayLarge,
                        color = LocalHeroTextColor.current,
                        fontWeight = FontWeight.Bold
                    )
                }
            )

            // Time Available Status Card
            TimeStatusCard(
                availableMinutes = availableMinutes
            )

            // Breakdown Stats - Grid Layout
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatItem(
                        label = "New",
                        count = ankiStats.new,
                        color = AnkiNewBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatItem(
                        label = "Learn",
                        count = ankiStats.learn,
                        color = AnkiLearnRed,
                        modifier = Modifier.weight(1f)
                    )
                    StatItem(
                        label = "Review",
                        count = ankiStats.review,
                        color = AnkiReviewGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Last Updated
            if (lastUpdated > 0) {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                Text(
                    text = "Last updated: ${sdf.format(Date(lastUpdated))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            // Action Buttons - 2x2 Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // First Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        label = "Open AnkiDroid",
                        icon = Icons.Default.Apps,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.ichi2.anki")
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        }
                    )

                    ActionButton(
                        label = "Refresh",
                        icon = Icons.Default.Refresh,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.fetchAnkiData() }
                    )
                }

                // Second Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        label = "Blocked Apps",
                        icon = Icons.Default.Lock,
                        modifier = Modifier.weight(1f),
                        onClick = { showAppSelector = true }
                    )

                    ActionButton(
                        label = "Permissions",
                        icon = Icons.Default.Settings,
                        modifier = Modifier.weight(1f),
                        onClick = { showPermissionsScreen = true },
                        isHighlighted = !allPermissionsGranted
                    )
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isHighlighted: Boolean = false
) {
    val colors = vetoColors()
    val backgroundColor = if (isHighlighted) {
        Color(0xFFEF5350) // Red for warning
    } else {
        colors.buttonBackground
    }
    val textColor = if (isHighlighted) Color.White else colors.buttonText

    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .padding(bottom = 8.dp),
                tint = textColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = textColor
            )
        }
    }
}

// Helper function to check if accessibility service is enabled
fun isAccessibilityServiceEnabledForVeto(context: android.content.Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    return enabledServices.contains("com.raven.veto")
}
