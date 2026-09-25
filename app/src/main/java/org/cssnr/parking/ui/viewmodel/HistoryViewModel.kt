package org.cssnr.parking.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.cssnr.parking.data.HistoryRepository
import org.cssnr.parking.data.ReverseGeocoder
import org.cssnr.parking.data.db.AppDatabase
import org.cssnr.parking.data.db.History

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepository = HistoryRepository(AppDatabase.getDatabase(application))
    private val reverseGeocoder = ReverseGeocoder(application)

    val history: StateFlow<List<History>> = historyRepository.history
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    init {
        backfillAddresses()
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            historyRepository.delete(id)
        }
    }

    /**
     * Reverse geocodes records saved before schema version 2, which stored
     * coordinates only.
     *
     * Runs once per process rather than on every emission, because a record with
     * a null address is indistinguishable from one whose lookup already failed,
     * so a reactive trigger would hammer the geocoder on each screen open. Rows
     * that fail here are retried on the next app launch.
     */
    private fun backfillAddresses() {
        viewModelScope.launch {
            val pending = historyRepository.getWithoutAddress()
            if (pending.isEmpty()) return@launch
            Log.d(TAG, "backfilling addresses for ${pending.size} record(s)")
            pending.forEach { record ->
                val address = runCatching {
                    reverseGeocoder.reverseGeocode(record.latitude, record.longitude)
                }.onFailure {
                    Log.w(TAG, "backfill geocode failed for ${record.id}: ${it.message}")
                }.getOrNull() ?: return@forEach
                historyRepository.update(record.copy(address = address))
            }
        }
    }

    companion object {
        private const val TAG = "HistoryViewModel"
    }
}
