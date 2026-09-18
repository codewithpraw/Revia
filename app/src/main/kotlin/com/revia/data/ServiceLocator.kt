package com.revia.data

import android.content.Context
import com.revia.data.db.Interruption
import com.revia.data.db.ReviaDatabase
import com.revia.data.repository.InterruptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
}
