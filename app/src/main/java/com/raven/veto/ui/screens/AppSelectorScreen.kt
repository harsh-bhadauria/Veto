package com.raven.veto.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raven.veto.data.AppInfo
import com.raven.veto.ui.viewmodels.AppSelectorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppSelectorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val strictModeEnabled by viewModel.strictModeEnabled.collectAsStateWithLifecycle()
    val totalDueCards by viewModel.totalDueCards.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked Apps") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search apps...") },
                singleLine = true
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = uiState.apps,
                        key = { it.packageName }
                    ) { app ->
                        AppListItem(
                            app = app,
                            strictModeEnabled = strictModeEnabled,
                            totalDueCards = totalDueCards,
                            onToggle = { viewModel.toggleAppBlock(app.packageName) },
                            onMultiplierChange = { newMultiplier ->
                                viewModel.updateAppMultiplier(app.packageName, newMultiplier)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    strictModeEnabled: Boolean,
    totalDueCards: Int,
    onToggle: (Boolean) -> Unit,
    onMultiplierChange: (Float) -> Unit
) {
    val isLocked = app.isBlocked && strictModeEnabled && totalDueCards > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                color = if (app.isBlocked)
                    androidx.compose.material3.MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else
                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isLocked) { onToggle(!app.isBlocked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bitmap = app.icon?.toBitmap()?.asImageBitmap()
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Box(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = app.name,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    text = if (app.isBlocked) "Blocked (${app.costMultiplier}x Cost)" else "Allowed",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = if (app.isBlocked)
                        androidx.compose.ui.graphics.Color(0xFFF44336)
                    else
                        androidx.compose.ui.graphics.Color(0xFF4CAF50),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }

                if (isLocked) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                        contentDescription = "Locked by Strict Mode",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Checkbox(
                    checked = app.isBlocked,
                    onCheckedChange = if (isLocked) null else onToggle,
                    enabled = !isLocked
                )
            }
        
        if (app.isBlocked) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Cost: ${String.format("%.1f", app.costMultiplier)}x", 
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(60.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Slider(
                    value = app.costMultiplier,
                    onValueChange = onMultiplierChange,
                    valueRange = 0.5f..5.0f,
                    steps = 8,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

