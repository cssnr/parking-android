package org.cssnr.parking.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AutomaticRepository(private val context: Context) {

    val automaticEnabled: Flow<Boolean> = context.parkingDataStore.data.map { preferences ->
        preferences[AUTOMATIC_ENABLED] ?: false
    }

    val backgroundPermissionRequested: Flow<Boolean> =
        context.parkingDataStore.data.map { preferences ->
            preferences[BACKGROUND_PERMISSION_REQUESTED] ?: false
        }

    val selectedBluetoothDevices: Flow<Set<String>> = context.parkingDataStore.data.map { preferences ->
        preferences[SELECTED_BLUETOOTH_DEVICES] ?: emptySet()
    }

    suspend fun setAutomaticEnabled(enabled: Boolean) {
        context.parkingDataStore.edit { preferences ->
            preferences[AUTOMATIC_ENABLED] = enabled
        }
    }

    suspend fun markBackgroundPermissionRequested() {
        context.parkingDataStore.edit { preferences ->
            preferences[BACKGROUND_PERMISSION_REQUESTED] = true
        }
    }

    suspend fun setSelectedBluetoothDevices(addresses: Set<String>) {
        context.parkingDataStore.edit { preferences ->
            preferences[SELECTED_BLUETOOTH_DEVICES] = addresses
        }
    }

    private companion object {
        val AUTOMATIC_ENABLED = booleanPreferencesKey("automatic.enabled")
        val BACKGROUND_PERMISSION_REQUESTED =
            booleanPreferencesKey("automatic.backgroundPermissionRequested")
        val SELECTED_BLUETOOTH_DEVICES = stringSetPreferencesKey("automatic.bluetoothDevices")
    }
}