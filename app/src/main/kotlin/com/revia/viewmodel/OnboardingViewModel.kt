package com.revia.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.revia.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionState(
    val usageStatsGranted: Boolean = false,
    val notificationListenerGranted: Boolean = false,
    val accessibilityGranted: Boolean = false
) {
    val allGranted get() = usageStatsGranted && notificationListenerGranted && accessibilityGranted
}

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PermissionState())
    val state: StateFlow<PermissionState> = _state.asStateFlow()

    fun refresh(accessibilityServiceClassName: String) {
        val context = getApplication<Application>()
        _state.value = PermissionState(
            usageStatsGranted = PermissionUtils.hasUsageStatsPermission(context),
            notificationListenerGranted = PermissionUtils.isNotificationListenerEnabled(context),
            accessibilityGranted = PermissionUtils.isAccessibilityServiceEnabled(context, accessibilityServiceClassName)
        )
    }
}
