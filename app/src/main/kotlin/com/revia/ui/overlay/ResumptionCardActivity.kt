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
import androidx.lifecycle.lifecycleScope
import com.revia.data.ServiceLocator
import com.revia.data.db.Interruption
import com.revia.data.preferences.UserPreferences
import com.revia.ui.screen.components.ResumptionCard
import com.revia.ui.theme.ReviaTheme
import com.revia.ui.theme.ThemeMode
import com.revia.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val EXTRA_RETURNED_TO = "returned_to"

/**
 * The resumption card, shown over whatever app the user came back to.
 *
 * An Activity rather than a window-manager overlay because AICore refuses
 * inference for a background process, and an attached overlay window does not
 * count as foreground. Being a real Activity is what lets the summary be
 * generated on-device while the card is on screen.
 *
 * The window is transparent, top-aligned and not touch-modal, so it reads as a
 * floating card and taps outside it still reach the app underneath.
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

        val returnedTo = intent.getStringExtra(EXTRA_RETURNED_TO)
        val initial = ServiceLocator.pendingCard.value ?: run { finish(); return }

        setContent {
            var card by remember { mutableStateOf(initial) }
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }

            LaunchedEffect(Unit) {
                val preferences = UserPreferences(applicationContext)
                themeMode = preferences.themeMode.first()

                // Foreground at last, so this is where the real summary gets written.
                val repository = ServiceLocator.repository(applicationContext)
                runCatching { repository.enrich(card) }
                    .getOrNull()
                    ?.let { card = it }

                if (preferences.autoDismissEnabled.first()) {
                    delay(Constants.AUTO_DISMISS_MILLIS)
                    dismiss()
                }
            }

            ReviaTheme(themeMode = themeMode) {
                ResumptionCard(
                    interruption = card,
                    onDismiss = { dismiss() },
                    onJumpBackIn = {
                        returnedTo?.let(::launchApp)
                        dismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }
        }
    }

    private fun dismiss() {
        ServiceLocator.clearCard()
        finish()
    }

    private fun launchApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(it) }
        }
    }

    companion object {
        fun show(context: Context, returnedTo: String) {
            val intent = Intent(context, ResumptionCardActivity::class.java)
                .putExtra(EXTRA_RETURNED_TO, returnedTo)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            runCatching { context.startActivity(intent) }
        }
    }
}
