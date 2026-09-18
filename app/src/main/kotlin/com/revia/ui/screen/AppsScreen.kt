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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revia.data.preferences.UserPreferences
import com.revia.util.AppFilter
import com.revia.util.AppInfo
import com.revia.util.AppTrust
import com.revia.util.rememberAppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ObservableApp(
    val packageName: String,
    val label: String,
    val trust: AppTrust
)

/**
 * Picks the apps Revia is allowed to read. Nothing is read until chosen here, which is
 * both the privacy guarantee and the reason the service stays cheap - unpicked apps are
 * rejected on a set lookup before the screen is ever walked.
 *
 * [onFinished] is supplied during onboarding, where this is a setup step rather than a
 * settings page.
 */
@Composable
fun AppsScreen(onFinished: (() -> Unit)? = null) {
    val context = LocalContext.current
    val preferences = remember { UserPreferences(context) }
    val scope = rememberCoroutineScope()

    val observed by preferences.observedApps.collectAsStateWithLifecycle(initialValue = emptySet())
    var query by remember { mutableStateOf("") }

    // Resolving a label for every installed app is disk work; keep it off the main thread.
    val apps by produceState(initialValue = emptyList<ObservableApp>()) {
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }

    val matching = apps.filter { it.label.contains(query, ignoreCase = true) }
    val choosable = matching.filter { it.trust == AppTrust.ORDINARY }
    val financial = matching.filter { it.trust == AppTrust.LOOKS_FINANCIAL }
    val protectedApps = matching.filter { it.trust == AppTrust.HANDLES_PAYMENTS }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
            Text(
                text = if (onFinished != null) "Which apps should Revia watch?"
                       else "Apps Revia watches",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = when {
                    apps.isEmpty() -> "Loading your apps…"
                    observed.isEmpty() -> "Nothing selected — Revia reads nothing yet"
                    else -> "${observed.size} selected"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp)
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                SectionHeader(
                    title = "Your apps",
                    subtitle = "Revia reads only what you switch on here."
                )
            }
            items(choosable, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    checked = app.packageName in observed,
                    locked = false,
                    onToggle = { on ->
                        scope.launch { preferences.setAppObserved(app.packageName, on) }
                    }
                )
            }

            if (financial.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Might handle money",
                        subtitle = "These only read as financial by name, which is a guess " +
                            "and can be wrong. Switch one on if you know better."
                    )
                }
                items(financial, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        checked = app.packageName in observed,
                        locked = false,
                        onToggle = { on ->
                            scope.launch { preferences.setAppObserved(app.packageName, on) }
                        }
                    )
                }
            }

            if (protectedApps.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Protected",
                        subtitle = "Android routes payments to these. Revia refuses to read " +
                            "them, and they cannot be switched on."
                    )
                }
                items(protectedApps, key = { it.packageName }) { app ->
                    AppRow(app = app, checked = false, locked = true, onToggle = {})
                }
            }
        }

        onFinished?.let { finish ->
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Button(
                    onClick = finish,
                    enabled = observed.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (observed.isEmpty()) "Pick at least one app" else "Start using Revia")
                }
                Text(
                    text = "You can change this any time in Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
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
    checked: Boolean,
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
            Switch(checked = checked, onCheckedChange = onToggle)
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
                trust = AppFilter.trust(context, pkg)
            )
        }
        .sortedBy { it.label.lowercase() }
}.getOrDefault(emptyList())
