package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.Local
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.utils.AppContextHolder

/**
 * RPC: get_locales.
 *
 * ESTE ERA EL BUG DE "MODO OFFLINE NO FUNCIONA": esta clase llamaba SIEMPRE
 * al servidor y no tenía ningún caché. El login offline (DeviceVerificationRepository)
 * sí sabía recuperarse sin internet, pero justo después el usuario cae en la
 * pantalla de selección de local (LocalSeleccionViewModel), que dependía 100%
 * de este repo. Sin internet, getLocales() fallaba siempre → la lista de
 * locales quedaba vacía → SessionManager.getLocalId() nunca se fijaba → y
 * absolutamente todo lo demás (Producto/Venta/Turno/Tarjeta/Merma) usa
 * localIdActivo(), que lanza IllegalStateException si no hay local
 * seleccionado. Es decir: toda la app quedaba bloqueada offline por este único
 * repo, aunque el resto sí estuviera bien implementado como offline-first.
 *
 * Fix: mismo patrón que el resto (cachear en Room, leer del caché si no hay
 * internet, y si la llamada al servidor falla igual devolver el último caché
 * conocido en vez de error).
 */
class LocalRepository(
    private val context: Context = AppContextHolder.context
) {
    private val db = AppDatabase.obtener(context)

    suspend fun getLocales(androidId: String): Result<List<Local>> {
        val cacheados = db.localDao().obtenerTodos().map { it.toModel() }

        if (cacheados.isNotEmpty() && !NetworkMonitor.hayInternet(context)) {
            return Result.success(cacheados)
        }

        return try {
            val locales = SupabaseClientProvider.client.postgrest
                .rpc("get_locales", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Local>()
            // db.localDao().limpiar()
            db.localDao().insertarTodos(locales.map { it.toEntity() })
            Result.success(locales)
        } catch (e: Exception) {
            // Sin internet (o el servidor falló): si hay algo cacheado de una
            // sesión anterior, se usa eso en vez de tumbar la pantalla.
            if (cacheados.isNotEmpty()) Result.success(cacheados) else Result.failure(e)
        }
    }
}
