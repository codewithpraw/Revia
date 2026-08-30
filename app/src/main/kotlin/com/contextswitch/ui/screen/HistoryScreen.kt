package com.contextswitch.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contextswitch.data.db.Interruption
import com.contextswitch.ui.screen.components.HistoryListItem
import com.contextswitch.viewmodel.HistoryViewModel

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val interruptions by viewModel.interruptions.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HistoryHeader(interruptions)

        if (interruptions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "No interruptions yet.\nSwitch between apps to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(interruptions, key = { it.id }) { interruption ->
                    HistoryListItem(interruption)
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(interruptions: List<Interruption>) {
    val distinctApps = interruptions.map { it.appName }.distinct().size
    val subtitle = if (interruptions.isEmpty()) {
        "Nothing logged yet"
    } else {
        "${interruptions.size} interruptions · $distinctApps apps"
    }

    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
        Text(text = "Today", style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
