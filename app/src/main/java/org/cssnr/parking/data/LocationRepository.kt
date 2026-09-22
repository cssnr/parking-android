package org.cssnr.parking.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocationRepository(private val context: Context) {

    val initialLocationPromptDismissed: Flow<Boolean> =
        context.parkingDataStore.data.map { preferences ->
            preferences[INITIAL_LOCATION_PROMPT_DISMISSED] ?: false
        }

    suspend fun dismissInitialLocationPrompt() {
        context.parkingDataStore.edit { preferences ->
            preferences[INITIAL_LOCATION_PROMPT_DISMISSED] = true
        }
    }

    private companion object {
        val INITIAL_LOCATION_PROMPT_DISMISSED = booleanPreferencesKey("location.initialPromptDismissed")
    }
}