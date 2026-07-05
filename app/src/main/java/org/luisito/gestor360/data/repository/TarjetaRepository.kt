package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.models.Tarjeta
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.utils.AppContextHolder

class TarjetaRepository(
    private val context: Context = AppContextHolder.context
) {
    private val db = AppDatabase.obtener(context)

    suspend fun getTarjetas(androidId: String): Result<List<Tarjeta>> {
        return try {
            if (NetworkMonitor.hayInternet(context)) {
                val tarjetas = SupabaseClientProvider.client.postgrest
                    .rpc("get_tarjetas", buildJsonObject { put("p_android_id", androidId) })
                    .decodeList<Tarjeta>()
                tarjetas.forEach { db.tarjetaDao().guardar(it) }
                Result.success(tarjetas)
            } else {
                Result.success(db.tarjetaDao().obtenerTodas())
            }
        } catch (e: Exception) {
            Result.success(db.tarjetaDao().obtenerTodas())
        }
    }

    suspend fun crearTarjeta(androidId: String, banco: String, numero: String, titular: String): Result<Unit> {
        return try {
            if (NetworkMonitor.hayInternet(context)) {
                SupabaseClientProvider.client.postgrest.rpc("crear_tarjeta", buildJsonObject {
                    put("p_android_id", androidId); put("p_banco", banco); put("p_numero", numero); put("p_titular", titular)
                })
            } else {
                db.accionPendienteDao().encolar(AccionPendienteEntity(
                    tipo = "crear_tarjeta",
                    payloadJson = buildJsonObject { put("p_android_id", androidId); put("p_banco", banco); put("p_numero", numero); put("p_titular", titular) }.toString()
                ))
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
