package com.contextswitch.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contextswitch.ui.screen.components.ResumptionCard
import com.contextswitch.util.Constants
import com.contextswitch.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val currentCard by viewModel.currentCard.collectAsStateWithLifecycle()

    LaunchedEffect(currentCard) {
        if (currentCard != null) {
            delay(Constants.AUTO_DISMISS_MILLIS)
            viewModel.dismissCard()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val card = currentCard
        if (card == null) {
            Text(
                text = "No interruptions yet. Switch between apps to get started.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        } else {
            ResumptionCard(
                interruption = card,
                onDismiss = { viewModel.dismissCard() },
                onJumpBackIn = { viewModel.dismissCard() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
        }
    }
}
