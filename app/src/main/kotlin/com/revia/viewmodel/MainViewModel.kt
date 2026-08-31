package com.revia.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.revia.data.ServiceLocator
import com.revia.data.db.Interruption
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Collections

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val currentCard: StateFlow<Interruption?> = ServiceLocator.pendingCard

    private val enriched = Collections.synchronizedSet(mutableSetOf<Int>())

    /**
     * Upgrades the card's template text using the on-device model. Driven from the
     * screen because AICore refuses inference for a background app, and the capture
     * that produced this card happened in a background service.
     */
    fun enrichCard(interruption: Interruption) {
        if (!enriched.add(interruption.id)) return
        viewModelScope.launch {
            val repository = ServiceLocator.repository(getApplication())
            val better = repository.enrich(interruption)
            if (better.summary != interruption.summary) {
                ServiceLocator.showCard(better)
            }
        }
    }

    fun dismissCard() = ServiceLocator.clearCard()
}
