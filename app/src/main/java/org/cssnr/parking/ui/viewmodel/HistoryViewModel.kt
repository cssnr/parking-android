package org.cssnr.parking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.cssnr.parking.data.HistoryRepository
import org.cssnr.parking.data.db.AppDatabase
import org.cssnr.parking.data.db.History

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepository = HistoryRepository(AppDatabase.getDatabase(application))

    val history: StateFlow<List<History>> = historyRepository.history
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    fun delete(id: Long) {
        viewModelScope.launch {
            historyRepository.delete(id)
        }
    }
}