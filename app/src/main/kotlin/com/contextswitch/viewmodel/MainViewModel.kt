package com.contextswitch.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.contextswitch.data.api.ContextSwitchApi
import com.contextswitch.data.db.ContextSwitchDatabase
import com.contextswitch.data.db.Interruption
import com.contextswitch.data.preferences.UserPreferences
import com.contextswitch.data.repository.InterruptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = UserPreferences(application)
    private val dao = ContextSwitchDatabase.getInstance(application).interruptionDao()
    private lateinit var repository: InterruptionRepository

    private val _currentCard = MutableStateFlow<Interruption?>(null)
    val currentCard: StateFlow<Interruption?> = _currentCard.asStateFlow()

    init {
        viewModelScope.launch {
            val baseUrl = preferences.baseUrl.first()
            repository = InterruptionRepository(dao, ContextSwitchApi.create(baseUrl))
        }
    }

    fun onInterruptionDetected(appName: String, onScreenText: String?, lastNotification: String?) {
        viewModelScope.launch {
            if (!::repository.isInitialized) return@launch
            _currentCard.value = repository.captureInterruption(appName, onScreenText, lastNotification)
        }
    }

    fun dismissCard() {
        _currentCard.value = null
    }
}
