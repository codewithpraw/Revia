package com.revia.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object AppInfo {

    fun label(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /**
     * Launcher, system UI, and Revia itself are transitions, not interruptions. Payment
     * apps and anything the user switched off are refused by [AppFilter], so a blocked
     * app never becomes an interruption in the first place.
     */
    fun isTrackable(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return false
        if (packageName == "com.android.systemui") return false
        if (packageName == launcherPackage(context)) return false
        return AppFilter.isObservable(context, packageName)
    }

    private fun launcherPackage(context: Context): String? = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
    }.getOrNull()
}
