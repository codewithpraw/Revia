package com.revia.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.revia.data.api.ReviaApi
import com.revia.data.db.ReviaDatabase
import com.revia.data.db.Interruption
import com.revia.data.preferences.UserPreferences
import com.revia.data.repository.InterruptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = UserPreferences(application)
    private val dao = ReviaDatabase.getInstance(application).interruptionDao()
    private lateinit var repository: InterruptionRepository

    private val _currentCard = MutableStateFlow<Interruption?>(null)
    val currentCard: StateFlow<Interruption?> = _currentCard.asStateFlow()

    init {
        viewModelScope.launch {
            val baseUrl = preferences.baseUrl.first()
            repository = InterruptionRepository(dao, ReviaApi.create(baseUrl))
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
