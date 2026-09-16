package com.revia.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revia.data.preferences.UserPreferences
import com.revia.util.AppFilter
import com.revia.util.AppInfo
import com.revia.util.rememberAppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ObservableApp(
    val packageName: String,
    val label: String,
    val sensitive: Boolean
)

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val preferences = remember { UserPreferences(context) }
    val scope = rememberCoroutineScope()

    val excluded by preferences.excludedApps.collectAsStateWithLifecycle(initialValue = emptySet())

    // Resolving labels for every installed app is disk work; keep it off the main thread.
    val apps by produceState(initialValue = emptyList<ObservableApp>()) {
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }

    val protected = apps.filter { it.sensitive }
    val choosable = apps.filterNot { it.sensitive }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
            Text("Apps Revia watches", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (apps.isEmpty()) "Loading your apps…"
                       else "${choosable.size - excluded.count { pkg -> choosable.any { it.packageName == pkg } }} " +
                           "of ${choosable.size} on · ${protected.size} protected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (protected.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Protected",
                        subtitle = "Payment and banking apps. Revia never reads these, " +
                            "and this cannot be switched on."
                    )
                }
                items(protected, key = { it.packageName }) { app ->
                    AppRow(app = app, excluded = false, locked = true, onToggle = {})
                }
            }

            item {
                SectionHeader(
                    title = "Your apps",
                    subtitle = "Turn one off and Revia stops reading it entirely."
                )
            }
            items(choosable, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    excluded = app.packageName in excluded,
                    locked = false,
                    onToggle = { on ->
                        scope.launch { preferences.setAppExcluded(app.packageName, !on) }
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun AppRow(
    app: ObservableApp,
    excluded: Boolean,
    locked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val icon = rememberAppIcon(app.packageName)
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(modifier = Modifier.size(30.dp))
        }

        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 10.dp, end = 10.dp)
                .weight(1f)
        )

        if (locked) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Always protected",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Switch(checked = !excluded, onCheckedChange = onToggle)
        }
    }
}

private fun loadInstalledApps(context: Context): List<ObservableApp> = runCatching {
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    // Flag 0, not MATCH_DEFAULT_ONLY: launcher activities declare CATEGORY_LAUNCHER and
    // often not CATEGORY_DEFAULT, so the stricter flag returns a fraction of the phone.
    context.packageManager
        .queryIntentActivities(launcher, 0)
        .mapNotNull { it.activityInfo?.packageName }
        .distinct()
        .filter { it != context.packageName }
        .map { pkg ->
            ObservableApp(
                packageName = pkg,
                label = AppInfo.label(context, pkg),
                sensitive = AppFilter.isSensitive(context, pkg)
            )
        }
        .sortedBy { it.label.lowercase() }
}.getOrDefault(emptyList())
