package com.raven.veto.ui.screens


import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.raven.veto.ui.components.StatItem
import com.raven.veto.ui.viewmodels.MainViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    var showAppSelector by remember { mutableStateOf(false) }
    var showPermissionsScreen by remember { mutableStateOf(false) }

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

    // Launcher for asking permission
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total Due Cards",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = NumberFormat.getInstance().format(ankiStats.totalDue),
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Breakdown Stats
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    StatItem(
                        label = "New",
                        count = ankiStats.new,
                        color = Color(0xFF2196F3)
                    ) // Blue
                    StatItem(
                        label = "Learn",
                        count = ankiStats.learn,
                        color = Color(0xFFF44336)
                    ) // Red
                    StatItem(
                        label = "Review",
                        count = ankiStats.review,
                        color = Color(0xFF4CAF50)
                    ) // Green
                }

                Text(
                    text = "Time Available: $availableMinutes mins",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (availableMinutes > 0) Color(0xFF4CAF50) else Color.Red,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (lastUpdated > 0) {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    Text(
                        text = "Last updated: ${sdf.format(Date(lastUpdated))}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = { viewModel.fetchAnkiData() },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Refresh")
                }

                Button(
                    onClick = { showAppSelector = true },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Blocked Apps")
                }

                Button(
                    onClick = { showPermissionsScreen = true },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Check Permissions")
                }
            }
        }
    }
}
