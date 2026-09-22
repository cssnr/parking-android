package org.cssnr.parking.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AutomationRepository(private val context: Context) {

    val automationEnabled: Flow<Boolean> = context.parkingDataStore.data.map { preferences ->
        preferences[AUTOMATION_ENABLED] ?: false
    }

    val locationPermissionRequested: Flow<Boolean> =
        context.parkingDataStore.data.map { preferences ->
            preferences[LOCATION_PERMISSION_REQUESTED] ?: false
        }

    val backgroundPermissionRequested: Flow<Boolean> =
        context.parkingDataStore.data.map { preferences ->
            preferences[BACKGROUND_PERMISSION_REQUESTED] ?: false
        }

    val selectedBluetoothDevices: Flow<Set<String>> = context.parkingDataStore.data.map { preferences ->
        preferences[SELECTED_BLUETOOTH_DEVICES] ?: emptySet()
    }

    suspend fun setAutomationEnabled(enabled: Boolean) {
        context.parkingDataStore.edit { preferences ->
            preferences[AUTOMATION_ENABLED] = enabled
        }
    }

    suspend fun markLocationPermissionRequested() {
        context.parkingDataStore.edit { preferences ->
            preferences[LOCATION_PERMISSION_REQUESTED] = true
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
        val AUTOMATION_ENABLED = booleanPreferencesKey("automation.enabled")
        val LOCATION_PERMISSION_REQUESTED =
            booleanPreferencesKey("automation.locationPermissionRequested")
        val BACKGROUND_PERMISSION_REQUESTED =
            booleanPreferencesKey("automation.backgroundPermissionRequested")
        val SELECTED_BLUETOOTH_DEVICES = stringSetPreferencesKey("automation.bluetoothDevices")
    }
}