package com.revia.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * Financial words, matched against the package name and the app label. Matching is by
 * token prefix rather than raw substring: "sbi" appearing inside "in.krosbits.musicolet"
 * is a music player, not State Bank of India, and a plain `contains` cannot tell the
 * difference. Prefixes rather than whole tokens so compounds - icicibank, bankofbaroda -
 * are still caught.
 */
private val FINANCE_KEYWORDS = listOf(
    "upi", "paytm", "phonepe", "gpay", "bhim", "paypal", "razorpay", "payu",
    "bank", "wallet", "money", "cash", "credit", "debit", "loan", "finance",
    "hdfc", "icici", "sbi", "axis", "kotak", "idfc", "indusind", "canara",
    "pnb", "bob", "yesbank", "rbl", "federal", "jupiter", "fampay", "cred",
    "navi", "slice", "groww", "zerodha", "upstox"
)

/** How far Revia trusts an app with what is on its screen. */
enum class AppTrust {
    /**
     * Android itself routes UPI payments here. The strongest signal there is, and the
     * only one allowed to overrule the user - these are never read, however they are
     * configured.
     */
    HANDLES_PAYMENTS,

    /**
     * The name reads as financial. A guess, and guesses are wrong: it is the user's
     * call, so picking the app overrides it.
     */
    LOOKS_FINANCIAL,

    ORDINARY
}

/**
 * Decides which apps Revia is allowed to observe.
 *
 * Two gates of unequal weight. An app the system says settles payments is walled off
 * permanently. Everything else is read only if the user picked it, and a merely
 * financial-sounding name is surfaced as a caution rather than an unappealable lock -
 * otherwise a misread name would bar an app from the feature with no way back.
 *
 * A blocked app is never read, not merely filtered later - the accessibility service
 * checks here before it touches the screen at all.
 */
object AppFilter {

    private val trustCache = ConcurrentHashMap<String, AppTrust>()

    /** Packages the user has opted in. Mirrored from DataStore to keep the hot path cheap. */
    @Volatile
    private var observed: Set<String> = emptySet()

    fun setObserved(packages: Set<String>) {
        observed = packages
    }

    fun observedCount(): Int = observed.size

    /**
     * True when Revia may read this app. Opt-in: an app is read only if the user picked
     * it, and never if the system says it settles payments. Most apps fail this check on
     * the first comparison, which is what keeps the accessibility service cheap - the
     * view tree is only walked for apps the user actually cares about.
     */
    fun isObservable(context: Context, packageName: String): Boolean =
        packageName in observed && trust(context, packageName) != AppTrust.HANDLES_PAYMENTS

    fun trust(context: Context, packageName: String): AppTrust =
        trustCache.getOrPut(packageName) { computeTrust(context, packageName) }

    private fun computeTrust(context: Context, packageName: String): AppTrust = when {
        handlesUpiPayments(context, packageName) -> AppTrust.HANDLES_PAYMENTS
        readsAsFinancial(context, packageName) -> AppTrust.LOOKS_FINANCIAL
        else -> AppTrust.ORDINARY
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

    private fun readsAsFinancial(context: Context, packageName: String): Boolean {
        val words = words(packageName) + words(AppInfo.label(context, packageName))
        return words.any { word -> FINANCE_KEYWORDS.any { word.startsWith(it) } }
    }

    /** Package names split on dots, labels on spaces; anything not a letter divides them. */
    private fun words(value: String): List<String> =
        value.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
}
