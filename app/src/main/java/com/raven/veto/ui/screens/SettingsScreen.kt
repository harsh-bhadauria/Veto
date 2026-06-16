package com.raven.veto.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raven.veto.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDeckSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Global Exchange Rate", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "1 card = ${"%.1f".format(uiState.exchangeRate)} minutes",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Slider(
                value = uiState.exchangeRate,
                onValueChange = { viewModel.updateExchangeRate(it) },
                valueRange = 0.1f..10.0f,
                steps = 99
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Text("Adjust how strictly Veto rewards your flashcard reviews.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onNavigateToDeckSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Configure Deck Specific Rates")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Strict Mode (Optional)", style = MaterialTheme.typography.titleLarge)
            Text(
                "When enabled, makes Veto very hard to bypass or uninstall. You can only turn this off if you have 0 Anki cards due.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            val context = LocalContext.current
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val component = android.content.ComponentName(context, com.raven.veto.services.VetoDeviceAdminReceiver::class.java)
            
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { _ ->
                if (dpm.isAdminActive(component)) {
                    viewModel.updateStrictMode(true, context) {}
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable Strict Mode", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.strictModeEnabled,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            if (!dpm.isAdminActive(component)) {
                                val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to prevent uninstalling Veto while you owe time.")
                                }
                                launcher.launch(intent)
                            } else {
                                viewModel.updateStrictMode(true, context) {}
                            }
                        } else {
                            viewModel.updateStrictMode(false, context) {
                                Toast.makeText(context, "Cannot disable! You have Anki cards due.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(32.dp))
            
            Text("Developer Options", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { viewModel.setTestTime() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test 5-Minute Warning")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Sets your balance to exactly 5.1 minutes. Open a blocked app (with 1.0x cost) to trigger the warning.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
