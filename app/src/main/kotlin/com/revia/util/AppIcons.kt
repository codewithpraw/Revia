package com.revia.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val ICON_SIZE_PX = 96

/**
 * Loads launcher icons for history rows. Cached because the list re-reads the
 * same handful of packages on every scroll.
 */
object AppIcons {

    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun cached(packageName: String?): ImageBitmap? =
        packageName?.takeIf { it.isNotBlank() }?.let { cache[it] }

    fun load(context: Context, packageName: String): ImageBitmap? {
        cache[packageName]?.let { return it }
        return runCatching {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
                .asImageBitmap()
                .also { cache[packageName] = it }
        }.getOrNull()
    }
}

/** Null while loading, or when the app is uninstalled or the row predates package tracking. */
@Composable
fun rememberAppIcon(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    return produceState(initialValue = AppIcons.cached(packageName), packageName) {
        if (packageName.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) { AppIcons.load(context, packageName) }
    }.value
}
