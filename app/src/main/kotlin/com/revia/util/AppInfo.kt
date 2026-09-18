package com.revia.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Brings [packageName] back to the front. Only its launcher entry point is reachable -
 * Android gives no way to restore a third-party app's actual prior state - so this
 * resumes the app, and the card supplies the context the app cannot.
 */
fun Context.launchApp(packageName: String) {
    packageManager.getLaunchIntentForPackage(packageName)?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(it) }
    }
}

object AppInfo {

    fun label(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /**
     * The home screen, system UI, and Revia itself are passages rather than places.
     * Arriving at one is not a distraction - dropping to the launcher between two apps
     * is part of switching, not the thing worth interrupting someone over - and leaving
     * one is not an interruption either.
     */
    fun isTransition(context: Context, packageName: String): Boolean =
        packageName == context.packageName ||
            packageName == "com.android.systemui" ||
            packageName == launcherPackage(context)

    /**
     * Whether Revia may build an interruption out of this app. Payment apps and anything
     * the user switched off are refused by [AppFilter], so a blocked app never becomes an
     * interruption in the first place.
     */
    fun isTrackable(context: Context, packageName: String): Boolean =
        !isTransition(context, packageName) && AppFilter.isObservable(context, packageName)

    private fun launcherPackage(context: Context): String? = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
    }.getOrNull()
}
