package com.revia.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.revia.data.db.Interruption
import com.revia.ui.screen.components.ResumptionCard
import com.revia.ui.theme.ReviaTheme
import com.revia.ui.theme.ThemeMode

/**
 * Shows the resumption card over whichever app the user returned to.
 *
 * A window-manager overlay has no Activity behind it, so Compose's three
 * ViewTree owners have to be supplied by hand or the ComposeView will not
 * compose.
 */
object ResumptionOverlay {

    private var attached: ComposeView? = null
    private var shown: androidx.compose.runtime.MutableState<Interruption>? = null
    private var host: OverlayHost? = null
    private val main = Handler(Looper.getMainLooper())
    private var autoHide: Runnable? = null

    fun canShow(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun show(
        context: Context,
        interruption: Interruption,
        themeMode: ThemeMode,
        autoDismissMillis: Long?,
        onJumpBackIn: () -> Unit
    ) = main.post {
        if (!canShow(context)) return@post
        hideInternal(context)

        val overlayHost = OverlayHost().also { it.start(); host = it }
        val state = mutableStateOf(interruption).also { shown = it }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(overlayHost)
            setViewTreeViewModelStoreOwner(overlayHost)
            setViewTreeSavedStateRegistryOwner(overlayHost)
            setContent {
                ReviaTheme(themeMode = themeMode) {
                    val current by state
                    ResumptionCard(
                        interruption = current,
                        onDismiss = { hide(context) },
                        onJumpBackIn = {
                            hide(context)
                            onJumpBackIn()
                        },
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        runCatching {
            windowManager(context).addView(view, layoutParams())
            attached = view
        }.onFailure { overlayHost.stop() }

        autoDismissMillis?.let { delay ->
            autoHide = Runnable { hide(context) }.also { main.postDelayed(it, delay) }
        }
    }

    /** Swaps in a better summary without disturbing a card already on screen. */
    fun update(interruption: Interruption) = main.post {
        shown?.takeIf { it.value.id == interruption.id }?.value = interruption
    }

    fun hide(context: Context) = main.post { hideInternal(context) }

    private fun hideInternal(context: Context) {
        autoHide?.let { main.removeCallbacks(it) }
        autoHide = null
        attached?.let { view -> runCatching { windowManager(context).removeView(view) } }
        attached = null
        shown = null
        host?.stop()
        host = null
    }

    private fun windowManager(context: Context) =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Not focusable so the app underneath keeps input; the card itself is still
        // touchable, which is all the two buttons need.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP }
}

private class OverlayHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
