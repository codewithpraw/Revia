package com.revia.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import com.revia.service.ContentAccessibilityService

object PermissionUtils {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (TextUtils.isEmpty(flat)) return false
        return flat.contains(context.packageName)
    }

    fun isAccessibilityServiceEnabled(context: Context, serviceClassName: String): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (TextUtils.isEmpty(flat)) return false
        return flat.contains(serviceClassName)
    }

    /**
     * The one permission without which nothing works. The other two only enrich
     * the summary, and the system can revoke accessibility at any time - losing
     * it should not send the user back to onboarding.
     */
    fun hasEssentialPermission(context: Context): Boolean = hasUsageStatsPermission(context)

    fun allGranted(context: Context): Boolean =
        hasUsageStatsPermission(context) &&
            isNotificationListenerEnabled(context) &&
            isAccessibilityServiceEnabled(context, ContentAccessibilityService::class.java.name)

    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun notificationListenerSettingsIntent(): Intent =
        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}
