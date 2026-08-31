package com.revia.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revia.data.db.ReviaDatabase
import com.revia.R
import com.revia.data.preferences.UserPreferences
import com.revia.data.summary.OnDeviceSummarizer
import com.revia.ui.theme.LogoTile
import com.revia.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val preferences = remember { UserPreferences(context) }
    val scope = rememberCoroutineScope()

    val cardsEnabled by preferences.cardsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val autoDismissEnabled by preferences.autoDismissEnabled.collectAsStateWithLifecycle(initialValue = true)
    val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        AppIdentityHeader()

        SettingsSectionLabel("Appearance")
        ThemeModeSelector(
            selected = themeMode,
            onSelect = { mode -> scope.launch { preferences.setThemeMode(mode) } }
        )

        SettingsSectionLabel("Resumption")
        SettingsToggleRow(
            title = "Enable resumption cards",
            checked = cardsEnabled,
            onCheckedChange = { checked -> scope.launch { preferences.setCardsEnabled(checked) } }
        )
        SettingsToggleRow(
            title = "Auto-dismiss after 10 seconds",
            checked = autoDismissEnabled,
            onCheckedChange = { checked -> scope.launch { preferences.setAutoDismissEnabled(checked) } }
        )

        SettingsSectionLabel("On-device AI")
        val nanoStatus by produceState(initialValue = "checking…") {
            value = OnDeviceSummarizer().status()
        }
        Text(
            text = "Gemini Nano: $nanoStatus",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        var nanoAction by remember { mutableStateOf<String?>(null) }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val s = OnDeviceSummarizer()
                    nanoAction = "starting…"
                    val outcome = s.download { nanoAction = it }
                    nanoAction = "status: $outcome — running test…"
                    val result = s.summarize(
                        appName = "Notes",
                        screenText = "XP leaderboard sync returns stale ranks after 5pm. Cron runs 4:55pm, cache invalidates before write completes.",
                        lastNotification = "WhatsApp: Team Group - demo at 4?"
                    )
                    nanoAction = result?.let { "OUTPUT: $it" } ?: "inference returned nothing"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Download model and run test")
        }
        nanoAction?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        SettingsSectionLabel("Data")
        OutlinedButton(
            onClick = {
                scope.launch { ReviaDatabase.getInstance(context).interruptionDao().clearAll() }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text("Clear history", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AppIdentityHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(LogoTile)
        ) {
            Image(
                painter = painterResource(R.drawable.revia_mark),
                contentDescription = null,
                modifier = Modifier.size(34.dp)
            )
        }
        Text(
            text = "Revia",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "v1.0 · local-first",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to "System",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark"
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 12.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

