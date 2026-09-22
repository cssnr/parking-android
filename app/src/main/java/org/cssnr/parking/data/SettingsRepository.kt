package org.cssnr.parking.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {

    val crashReporting: Flow<Boolean> = context.parkingDataStore.data.map { preferences ->
        preferences[CRASH_REPORTING] ?: true
    }

    suspend fun setCrashReporting(enabled: Boolean) {
        context.parkingDataStore.edit { preferences ->
            preferences[CRASH_REPORTING] = enabled
        }
    }

    private companion object {
        val CRASH_REPORTING = booleanPreferencesKey("acra.enable")
    }
}