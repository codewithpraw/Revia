package com.revia.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * Financial keywords, matched against both package name and app label. Deliberately
 * broad: a false positive costs one missed summary, a false negative means Revia read
 * someone's account balance. The wall errs toward blocking.
 */
private val FINANCE_KEYWORDS = listOf(
    "upi", "paytm", "phonepe", "gpay", "bhim", "paypal", "razorpay", "payu",
    "bank", "wallet", "money", "cash", "credit", "debit", "loan", "finance",
    "hdfc", "icici", "sbi", "axis", "kotak", "idfc", "indusind", "canara",
    "pnb", "bob", "yesbank", "rbl", "federal", "jupiter", "fampay", "cred",
    "navi", "slice", "groww", "zerodha", "upstox"
)

/**
 * Decides which apps Revia is allowed to observe.
 *
 * Two independent gates. Payment and banking apps are walled off permanently and
 * cannot be re-enabled from settings; everything else is on unless the user turns it
 * off. A blocked app is never read, not merely filtered later - the accessibility
 * service checks here before it touches the screen at all.
 */
object AppFilter {

    private val sensitiveCache = ConcurrentHashMap<String, Boolean>()

    /** Packages the user has chosen not to observe. Mirrored from DataStore. */
    @Volatile
    private var userExcluded: Set<String> = emptySet()

    fun setUserExcluded(packages: Set<String>) {
        userExcluded = packages
    }

    fun isUserExcluded(packageName: String): Boolean = packageName in userExcluded

    /**
     * True when Revia may read this app. Sensitive apps are refused regardless of
     * settings; the user's own exclusions are honoured on top of that.
     */
    fun isObservable(context: Context, packageName: String): Boolean =
        !isSensitive(context, packageName) && packageName !in userExcluded

    /**
     * Payment or banking app. Established two ways: whether Android itself routes UPI
     * payment intents to it, and whether its package or label reads as financial.
     */
    fun isSensitive(context: Context, packageName: String): Boolean =
        sensitiveCache.getOrPut(packageName) { computeSensitive(context, packageName) }

    private fun computeSensitive(context: Context, packageName: String): Boolean {
        if (handlesUpiPayments(context, packageName)) return true

        val label = AppInfo.label(context, packageName).lowercase()
        val pkg = packageName.lowercase()
        return FINANCE_KEYWORDS.any { it in pkg || it in label }
    }

    /**
     * Asking the system which apps can settle a UPI payment is more reliable than any
     * list we could maintain - it catches payment apps we have never heard of.
     */
    private fun handlesUpiPayments(context: Context, packageName: String): Boolean =
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
            context.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .any { it.activityInfo?.packageName == packageName }
        }.getOrDefault(false)

    /** Every installed app Revia will never observe. Shown in settings as locked. */
    fun sensitiveInstalledApps(context: Context): List<String> = runCatching {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // Flag 0, not MATCH_DEFAULT_ONLY: launcher activities declare CATEGORY_LAUNCHER
        // and often not CATEGORY_DEFAULT, so the stricter flag hides most of the phone.
        context.packageManager
            .queryIntentActivities(launcher, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .filter { isSensitive(context, it) }
    }.getOrDefault(emptyList())
}
