package com.revia.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.revia.data.db.Interruption
import com.revia.util.AppInfo
import com.revia.util.toRelativeTimeString

/**
 * [trail] is the apps entered since the interrupted task was left. A single entry is
 * just "where you are now" and says nothing worth the space, so it only appears once
 * the detour has actually wandered.
 */
@Composable
fun ResumptionCard(
    interruption: Interruption,
    onDismiss: () -> Unit,
    onJumpBackIn: () -> Unit,
    trail: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.History, contentDescription = null)
                Text(
                    text = "You got distracted",
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = interruption.timestamp.toRelativeTimeString(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = interruption.summary,
                modifier = Modifier.padding(top = 8.dp, bottom = if (trail.size > 1) 4.dp else 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            if (trail.size > 1) {
                Text(
                    text = "Since then: " +
                        trail.joinToString(" → ") { AppInfo.label(context, it) },
                    modifier = Modifier.padding(bottom = 12.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Dismiss")
                }
                Button(onClick = onJumpBackIn, modifier = Modifier.weight(1f)) {
                    Text("Jump back in")
                }
            }
        }
    }
}
