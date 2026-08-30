package com.revia.data

import android.content.Context
import com.revia.data.api.ReviaApi
import com.revia.data.db.Interruption
import com.revia.data.db.ReviaDatabase
import com.revia.data.preferences.UserPreferences
import com.revia.data.repository.InterruptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Connects the detection service to the UI. Both run in the same process, so a
 * singleton is enough - no broadcasts or binding needed.
 */
object ServiceLocator {

    @Volatile
    private var repository: InterruptionRepository? = null

    private val _pendingCard = MutableStateFlow<Interruption?>(null)
    val pendingCard: StateFlow<Interruption?> = _pendingCard.asStateFlow()

    suspend fun repository(context: Context): InterruptionRepository {
        repository?.let { return it }
        val baseUrl = UserPreferences(context).baseUrl.first()
        return synchronized(this) {
            repository ?: InterruptionRepository(
                ReviaDatabase.getInstance(context.applicationContext).interruptionDao(),
                ReviaApi.create(baseUrl)
            ).also { repository = it }
        }
    }

    fun showCard(interruption: Interruption) {
        _pendingCard.value = interruption
    }

    fun clearCard() {
        _pendingCard.value = null
    }
}
