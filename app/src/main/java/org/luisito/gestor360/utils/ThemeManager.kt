package org.luisito.gestor360.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.temaDataStore by preferencesDataStore(name = "tema_prefs")

/**
 * Preferencia de tema claro/oscuro, persistida con DataStore. Es una
 * preferencia del dispositivo (no del usuario logueado ni de Room), así
 * que sobrevive a cerrar sesión y no depende de la base cifrada.
 */
object ThemeManager {
    private val KEY_TEMA_OSCURO = booleanPreferencesKey("tema_oscuro")

    fun observarTemaOscuro(context: Context): Flow<Boolean> =
        context.applicationContext.temaDataStore.data.map { it[KEY_TEMA_OSCURO] ?: false }

    suspend fun alternarTema(context: Context) {
        context.applicationContext.temaDataStore.edit { prefs ->
            val actual = prefs[KEY_TEMA_OSCURO] ?: false
            prefs[KEY_TEMA_OSCURO] = !actual
        }
    }
}
