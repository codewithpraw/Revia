package com.contextswitch.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contextswitch.service.ContentAccessibilityService
import com.contextswitch.ui.screen.components.PermissionButton
import com.contextswitch.util.PermissionUtils
import com.contextswitch.viewmodel.OnboardingViewModel

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accessibilityServiceName = ContentAccessibilityService::class.java.name
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh(accessibilityServiceName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            text = "Never lose your\ntrain of thought",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = "3 permissions to get started",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        PermissionButton(
            icon = Icons.Filled.Timeline,
            title = "Detect app switches",
            description = "So we know when you've been interrupted",
            granted = state.usageStatsGranted,
            onGrantClick = { context.startActivity(PermissionUtils.usageAccessSettingsIntent()) }
        )

        PermissionButton(
            icon = Icons.Filled.Notifications,
            title = "Capture notifications",
            description = "Used to reconstruct what pulled you away",
            granted = state.notificationListenerGranted,
            onGrantClick = { context.startActivity(PermissionUtils.notificationListenerSettingsIntent()) }
        )

        PermissionButton(
            icon = Icons.Filled.RemoveRedEye,
            title = "Read on-screen text",
            description = "Used to summarize what you were doing",
            granted = state.accessibilityGranted,
            onGrantClick = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) }
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onFinished,
            enabled = state.allGranted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start using ContextSwitch")
        }
        Text(
            text = "Signals stay on your device",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 12.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}
