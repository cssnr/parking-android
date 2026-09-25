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
import java.util.concurrent.atomic.AtomicBoolean

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
     * [History.geocoded] is what stops a record being looked up twice: it is set
     * whenever a lookup returns anything, including a partial address with no
     * LocationAddress.line, so those records are never selected again. A lookup
     * that returns nothing leaves it unset, so that record is retried the next
     * time the app starts rather than on every screen open.
     *
     * The [backfillStarted] guard is what bounds that retry: a ViewModel is built
     * every time the History destination is entered, so without it every visit
     * would re-run the pending lookups.
     */
    private fun backfillAddresses() {
        if (!backfillStarted.compareAndSet(false, true)) return
        viewModelScope.launch {
            val pending = historyRepository.getUngeocoded()
            if (pending.isEmpty()) return@launch
            Log.d(TAG, "backfilling addresses for ${pending.size} record(s)")
            pending.forEach { record ->
                val address = runCatching {
                    reverseGeocoder.reverseGeocode(record.latitude, record.longitude)
                }.onFailure {
                    Log.w(TAG, "backfill geocode failed for ${record.id}: ${it.message}")
                }.getOrNull() ?: return@forEach
                historyRepository.update(record.copy(address = address, geocoded = true))
            }
        }
    }

    companion object {
        private const val TAG = "HistoryViewModel"

        // Process scoped: the backfill is a one-off per launch, and a new flag
        // appears with the process if the app restarts, which is the retry.
        private val backfillStarted = AtomicBoolean(false)
    }
}
