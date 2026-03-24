package com.raven.veto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raven.veto.ui.theme.AnkiLearnRed
import com.raven.veto.ui.theme.AnkiReviewGreen

@Composable
fun TimeStatusCard(availableMinutes: Int) {
    val isAvailable = availableMinutes > 0
    // Anki standard colors: Green for available, Red for blocked
    val accentColor = if (isAvailable) AnkiReviewGreen else AnkiLearnRed
    val backgroundColor = if (isAvailable)
        AnkiReviewGreen.copy(alpha = 0.15f)
    else
        AnkiLearnRed.copy(alpha = 0.15f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isAvailable) "You're Free!" else "Time Blocked",
            style = MaterialTheme.typography.titleMedium,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "$availableMinutes minutes available",
            style = MaterialTheme.typography.bodyMedium,
            color = accentColor,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

