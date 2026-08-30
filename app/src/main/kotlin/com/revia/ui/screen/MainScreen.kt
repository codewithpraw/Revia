package com.revia.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revia.data.preferences.UserPreferences
import com.revia.ui.screen.components.ResumptionCard
import com.revia.util.Constants
import com.revia.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val preferences = remember { UserPreferences(context) }

    val pendingCard by viewModel.currentCard.collectAsStateWithLifecycle()
    val cardsEnabled by preferences.cardsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val autoDismissEnabled by preferences.autoDismissEnabled.collectAsStateWithLifecycle(initialValue = true)

    val card = pendingCard?.takeIf { cardsEnabled }

    LaunchedEffect(card, autoDismissEnabled) {
        if (card != null && autoDismissEnabled) {
            delay(Constants.AUTO_DISMISS_MILLIS)
            viewModel.dismissCard()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (card == null) {
            WaitingState(modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = card != null,
            enter = slideInVertically { -it },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            card?.let {
                ResumptionCard(
                    interruption = it,
                    onDismiss = { viewModel.dismissCard() },
                    onJumpBackIn = { viewModel.dismissCard() },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun WaitingState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = "Watching for interruptions",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "Switch to another app and come back — your card will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
