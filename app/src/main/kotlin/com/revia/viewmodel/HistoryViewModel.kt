package com.revia.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.revia.data.db.ReviaDatabase
import com.revia.data.db.Interruption
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = ReviaDatabase.getInstance(application).interruptionDao()

    val interruptions: StateFlow<List<Interruption>> = dao.observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Int) {
        viewModelScope.launch { dao.deleteById(id) }
    }
}
