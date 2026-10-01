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
import org.cssnr.parking.data.LocationProvider
import org.cssnr.parking.data.ReverseGeocoder
import org.cssnr.parking.data.db.AppDatabase
import org.cssnr.parking.data.db.History
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
     * Fills in positions that a background disconnect could not obtain, and reverse
     * geocodes records saved before schema version 2, which stored coordinates only.
     *
     * A locationless record exists because the request came back empty. Nothing can
     * be done about that while the app is in the background, but here the app is in
     * the foreground and permitted to ask, so the position is worth a second attempt
     * before the user goes looking for the car. That attempt is bounded to recent
     * events by [resolveUnlocated].
     *
     * The records are not deleted if the second attempt also fails, or if the event
     * is too old to resolve safely. They stay listed with no position, which is the
     * honest state.
     *
     * [History.geocoded] is what stops a record being geocoded twice: it is set
     * whenever a lookup returns anything, including a partial address with no
     * LocationAddress.line, so those records are never selected again. A lookup
     * that returns nothing leaves it unset.
     *
     * The [backfillStarted] guard is what bounds that retry: a ViewModel is built
     * every time the History destination is entered, so without it every visit
     * would re-run the pending lookups.
     */
    private fun backfillAddresses() {
        if (!backfillStarted.compareAndSet(false, true)) return
        viewModelScope.launch {
            resolveUnlocated()
            val pending = historyRepository.getUngeocoded()
            if (pending.isEmpty()) return@launch
            Log.d(TAG, "backfilling addresses for ${pending.size} record(s)")
            pending.forEach { record ->
                val latitude = record.latitude
                val longitude = record.longitude
                if (latitude == null || longitude == null) {
                    // Still no position, so there is nothing to look up. The
                    // coordinates are left null and resolveUnlocated will retry.
                    return@forEach
                }
                val address = runCatching {
                    reverseGeocoder.reverseGeocode(latitude, longitude)
                }.onFailure {
                    Log.w(TAG, "backfill geocode failed for ${record.id}: ${it.message}")
                }.getOrNull() ?: return@forEach
                historyRepository.update(record.copy(address = address, geocoded = true))
            }
        }
    }

    /**
     * Asks for a position for recent records that do not have one, in the
     * foreground where the request is not throttled.
     *
     * Only records newer than [RESOLVE_WINDOW] are considered, and that limit is
     * the whole correctness of this function. A fix taken now is the user's position
     * *now*, so it is only the right answer for an event that happened while they
     * were still at the car. Measured on device: opening the app an hour after a
     * disconnect resolved it with a fix aged 85ms and wrote the user's home onto a
     * record for the car, which is worse than leaving it blank because it looks
     * correct. The fix age gate cannot catch this, since the fix genuinely is
     * current; only the event's age says anything about whether it belongs here.
     *
     * One shared request covers the batch. Anything older stays unresolved and is
     * listed as such rather than guessed at.
     */
    private suspend fun resolveUnlocated() {
        val notBefore = System.currentTimeMillis() - RESOLVE_WINDOW.inWholeMilliseconds
        val pending = historyRepository.getUnlocatedSince(notBefore)
        if (pending.isEmpty()) return
        Log.d(TAG, "resolving ${pending.size} recent record(s) left without a position")
        val fix = LocationProvider(getApplication()).getBestFix(RESOLVE_BUDGET) ?: run {
            Log.w(TAG, "still no fix, leaving ${pending.size} record(s) unresolved")
            return
        }
        pending.forEach { record ->
            historyRepository.update(
                record.copy(
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    fixAgeMillis = fix.fixAgeMillis,
                    accuracy = fix.accuracy,
                    altitude = fix.altitude,
                    verticalAccuracy = fix.verticalAccuracy,
                ),
            )
        }
    }

    companion object {
        private const val TAG = "HistoryViewModel"

        /**
         * How recent a disconnect must be for the current position to be a valid
         * answer for it.
         *
         * Covers the walk from the car to wherever the phone is picked up: getting
         * out, closing the door, gathering things, unlocking. Past that the user is
         * somewhere else, and resolving would be writing a position for a place they
         * are not.
         */
        private val RESOLVE_WINDOW = 2.minutes

        /**
         * Generous, because this runs in the foreground with the app on screen and
         * there is no broadcast window to respect. It only exists so the History
         * screen is not blocked indefinitely on a device that cannot produce a fix.
         */
        private val RESOLVE_BUDGET = 30.seconds

        // Process scoped: the backfill is a one-off per launch, and a new flag
        // appears with the process if the app restarts, which is the retry.
        private val backfillStarted = AtomicBoolean(false)
    }
}
