package com.revia.ui.overlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revia.data.ServiceLocator
import com.revia.data.preferences.UserPreferences
import com.revia.ui.screen.components.ResumptionCard
import com.revia.ui.theme.ReviaTheme
import com.revia.ui.theme.ThemeMode
import com.revia.util.Constants
import com.revia.util.launchApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The resumption card, shown over whatever app the user switched to after leaving a
 * tracked one. It reports on the task they left, and "Jump back in" relaunches that -
 * never the app the card happens to be floating over.
 *
 * Card and trail are read live from [ServiceLocator] rather than out of the launching
 * intent, because the detection service updates both while this is on screen: hopping
 * onward through more apps extends one card instead of raising another. The activity is
 * singleTop, so a second launch would only deliver a new intent to this same instance
 * and leave a snapshot taken at onCreate frozen.
 *
 * An Activity rather than a window-manager overlay because AICore refuses inference for
 * a background process, and an attached overlay window does not count as foreground.
 * Being a real Activity is what lets the summary be generated on-device while the card
 * is on screen.
 *
 * The window is transparent, top-aligned and not touch-modal, so it reads as a floating
 * card and taps outside it still reach the app underneath.
 */
class ResumptionCardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.TOP)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        if (ServiceLocator.pendingCard.value == null) {
            finish()
            return
        }

        setContent {
            val preferences = remember { UserPreferences(applicationContext) }
            val card by ServiceLocator.pendingCard.collectAsStateWithLifecycle()
            val trail by ServiceLocator.chainTrail.collectAsStateWithLifecycle()
            val autoDismissEnabled by preferences.autoDismissEnabled
                .collectAsStateWithLifecycle(initialValue = true)
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            val enrichedIds = remember { mutableSetOf<Int>() }

            LaunchedEffect(Unit) {
                themeMode = preferences.themeMode.first()
            }

            // Every route out of the card clears the same shared state, so following it
            // closes this whether the user dismissed it, jumped back, or resumed the task
            // on their own and the service withdrew it.
            LaunchedEffect(card) {
                if (card == null) finish()
            }

            // Foreground at last, so this is where the real summary gets written. Keyed on
            // the id so neither a trail update nor the write this effect itself makes can
            // send it round again.
            LaunchedEffect(card?.id) {
                val current = card ?: return@LaunchedEffect
                if (!enrichedIds.add(current.id)) return@LaunchedEffect

                val repository = ServiceLocator.repository(applicationContext)
                runCatching { repository.enrich(current) }.getOrNull()?.let { better ->
                    if (ServiceLocator.pendingCard.value?.id == better.id) {
                        ServiceLocator.showCard(better)
                    }
                }
            }

            // Each hop is new information and earns a fresh viewing window, rather than
            // counting down from whenever the first card of the chain went up.
            LaunchedEffect(card, trail, autoDismissEnabled) {
                if (card != null && autoDismissEnabled) {
                    delay(Constants.AUTO_DISMISS_MILLIS)
                    dismiss()
                }
            }

            card?.let { current ->
                ReviaTheme(themeMode = themeMode) {
                    ResumptionCard(
                        interruption = current,
                        onDismiss = { dismiss() },
                        onJumpBackIn = {
                            ServiceLocator.noteIntentionalReturn(current.packageName)
                            launchApp(current.packageName)
                            dismiss()
                        },
                        trail = trail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
        }
    }

    private fun dismiss() {
        ServiceLocator.clearCard()
        finish()
    }

    companion object {
        fun show(context: Context) {
            val intent = Intent(context, ResumptionCardActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            runCatching { context.startActivity(intent) }
        }
    }
}
