package org.cssnr.parking.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {

    val historyDuration: Flow<Int> = context.parkingDataStore.data.map { preferences ->
        preferences[HISTORY] ?: DEFAULT_HISTORY
    }

    suspend fun setHistoryDuration(index: Int) {
        context.parkingDataStore.edit { preferences ->
            preferences[HISTORY] = index.coerceIn(0, HISTORY_STEPS)
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
        // 0=Disabled | 1=1 week | 2=2 weeks | 3=1 month | 4=3 months | 5=6 months | 6=1 year
        const val HISTORY_STEPS = 6
        const val DEFAULT_HISTORY = 4
        val HISTORY = intPreferencesKey("history")
        val CRASH_REPORTING = booleanPreferencesKey("acra.enable")

        /**
         * Retention in millis for a history index, or null to keep everything
         * (Disabled). Months are 30-day approximations: history pruning is
         * housekeeping, not billing, so calendar exactness buys nothing here.
         */
        fun retentionMillis(index: Int): Long? = when (index.coerceIn(0, HISTORY_STEPS)) {
            1 -> 7L * DAY_MILLIS
            2 -> 14L * DAY_MILLIS
            3 -> 30L * DAY_MILLIS
            4 -> 90L * DAY_MILLIS
            5 -> 180L * DAY_MILLIS
            6 -> 365L * DAY_MILLIS
            else -> null
        }

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}