package com.revia.data

import android.content.Context
import com.revia.data.db.Interruption
import com.revia.data.db.ReviaDatabase
import com.revia.data.repository.InterruptionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Connects the detection service to the UI. Both run in the same process, so a
 * singleton is enough - no broadcasts or binding needed.
 */
object ServiceLocator {

    @Volatile
    private var repository: InterruptionRepository? = null

    private val _pendingCard = MutableStateFlow<Interruption?>(null)
    val pendingCard: StateFlow<Interruption?> = _pendingCard.asStateFlow()

    /**
     * Apps passed through since the interrupted task was left, in order. Kept beside the
     * card rather than folded into its summary: the summary is written to the database
     * and later rewritten by the on-device model, while the trail is live session state
     * that would be destroyed by either.
     */
    private val _chainTrail = MutableStateFlow<List<String>>(emptyList())
    val chainTrail: StateFlow<List<String>> = _chainTrail.asStateFlow()

    fun setChainTrail(trail: List<String>) {
        _chainTrail.value = trail
    }

    fun repository(context: Context): InterruptionRepository {
        repository?.let { return it }
        return synchronized(this) {
            repository ?: InterruptionRepository(
                ReviaDatabase.getInstance(context.applicationContext).interruptionDao()
            ).also { repository = it }
        }
    }

    /**
     * Marks a switch as one Revia asked for, so the detection service can tell "Jump
     * back in" apart from the user wandering off. Consumed by the switch it describes.
     */
    @Volatile
    private var intentionalReturn: String? = null

    fun noteIntentionalReturn(packageName: String) {
        intentionalReturn = packageName
    }

    fun consumeIntentionalReturn(packageName: String): Boolean {
        if (intentionalReturn != packageName) return false
        intentionalReturn = null
        return true
    }

    fun showCard(interruption: Interruption) {
        _pendingCard.value = interruption
    }

    fun clearCard() {
        _pendingCard.value = null
    }

    /**
     * Outlives any one screen, so a card being closed cannot cancel work it started.
     * Nothing here is tied to a lifecycle - the process is the lifecycle.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val enrichRequested = Collections.synchronizedSet(mutableSetOf<Int>())

    private val _enrichingId = MutableStateFlow<Int?>(null)

    /** The card currently being summarized, so a screen can wait rather than close over it. */
    val enrichingId: StateFlow<Int?> = _enrichingId.asStateFlow()

    /**
     * Upgrades a card's summary with the on-device model.
     *
     * Deliberately not run from the caller's scope: this used to sit in the resumption
     * card's composition, so dismissing the card - or the ten second auto-dismiss firing
     * first - cancelled the inference and the summary was lost for good. AICore only
     * refuses a request *issued* from the background, so starting it here while a screen
     * is up and letting it finish on its own is safe.
     *
     * The result is written to the database either way; it is only pushed back to the
     * card if that same card is still the one on screen, so a dismissed card never
     * reappears.
     */
    fun enrich(context: Context, interruption: Interruption) {
        if (!enrichRequested.add(interruption.id)) return

        appScope.launch {
            _enrichingId.value = interruption.id
            try {
                val better = runCatching { repository(context).enrich(interruption) }.getOrNull()
                if (better != null && _pendingCard.value?.id == interruption.id) {
                    _pendingCard.value = better
                }
            } finally {
                _enrichingId.value = null
            }
        }
    }
}
