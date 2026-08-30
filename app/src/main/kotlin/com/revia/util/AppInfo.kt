package com.revia.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object AppInfo {

    fun label(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /** Launcher, system UI, and Revia itself are transitions, not interruptions. */
    fun isTrackable(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return false
        if (packageName == "com.android.systemui") return false
        return packageName != launcherPackage(context)
    }

    private fun launcherPackage(context: Context): String? = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
    }.getOrNull()
}
