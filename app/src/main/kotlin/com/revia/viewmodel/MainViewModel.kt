package com.revia.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.revia.data.ServiceLocator
import com.revia.data.db.Interruption
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val currentCard: StateFlow<Interruption?> = ServiceLocator.pendingCard

    /**
     * Upgrades the card's template text using the on-device model. Driven from the
     * screen because AICore refuses inference for a background app, and the capture
     * that produced this card happened in a background service. The work itself runs
     * on the app scope, so leaving this screen does not cancel it.
     */
    fun enrichCard(interruption: Interruption) =
        ServiceLocator.enrich(getApplication(), interruption)

    fun dismissCard() = ServiceLocator.clearCard()
}
