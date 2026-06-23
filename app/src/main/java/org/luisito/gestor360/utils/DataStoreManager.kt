package org.luisito.gestor360.utils

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("gestor360_prefs")

class DataStoreManager(private val context: Context) {

    companion object {
        private val KEY_LOGGED_IN = booleanPreferencesKey("logged_in")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_LOGGED_IN] ?: false }

    val username: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_USERNAME] ?: "" }

    suspend fun saveSession(username: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOGGED_IN] = true
            preferences[KEY_USERNAME] = username
        }
    }

    suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVICE_ID] = deviceId
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
