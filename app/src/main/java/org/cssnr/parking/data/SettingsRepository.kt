package org.cssnr.parking.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {

    val historyDuration: Flow<Int> = context.parkingDataStore.data.map { preferences ->
        preferences[HISTORY_DURATION] ?: DEFAULT_HISTORY_DURATION
    }

    suspend fun setHistoryDuration(index: Int) {
        context.parkingDataStore.edit { preferences ->
            preferences[HISTORY_DURATION] = index.coerceIn(0, HISTORY_DURATION_STEPS)
        }
    }

    val crashReporting: Flow<Boolean> = context.parkingDataStore.data.map { preferences ->
        preferences[CRASH_REPORTING] ?: true
    }

    suspend fun setCrashReporting(enabled: Boolean) {
        context.parkingDataStore.edit { preferences ->
            preferences[CRASH_REPORTING] = enabled
        }
    }

    companion object {
        // 0=1 week | 1=2 weeks | 2=1 month | 3=3 months | 4=6 months | 5=1 year | 6=Indefinitely
        const val HISTORY_DURATION_STEPS = 6
        const val DEFAULT_HISTORY_DURATION = 3
        val HISTORY_DURATION = intPreferencesKey("history_duration")
        val CRASH_REPORTING = booleanPreferencesKey("acra.enable")

        /**
         * Retention in millis for a history duration index, or null to keep
         * everything (Indefinitely). Months are 30-day approximations: history
         * pruning is housekeeping, not billing, so calendar exactness buys
         * nothing here.
         */
        fun retentionMillis(index: Int): Long? = when (index.coerceIn(0, HISTORY_DURATION_STEPS)) {
            0 -> 7L * DAY_MILLIS
            1 -> 14L * DAY_MILLIS
            2 -> 30L * DAY_MILLIS
            3 -> 90L * DAY_MILLIS
            4 -> 180L * DAY_MILLIS
            5 -> 365L * DAY_MILLIS
            else -> null
        }

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}