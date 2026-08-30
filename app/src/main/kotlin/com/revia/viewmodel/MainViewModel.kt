package com.revia.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.revia.data.ServiceLocator
import com.revia.data.db.Interruption
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val currentCard: StateFlow<Interruption?> = ServiceLocator.pendingCard

    fun dismissCard() = ServiceLocator.clearCard()
}
