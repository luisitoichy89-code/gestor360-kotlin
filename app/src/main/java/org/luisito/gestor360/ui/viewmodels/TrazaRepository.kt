package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Traza

/**
 * RPC: registrar_traza, get_trazas. La limpieza de trazas > 30 días es automática
 * en el servidor (pg_cron o auto-limpieza en cada insert, según cuál SQL usaste).
 */
class TrazaRepository {

    /** Se llama "fire and forget": si falla el registro de traza, no debe tumbar la acción principal. */
    suspend fun registrar(androidId: String, accion: String, detalle: String) {
        try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_accion", accion)
                put("p_detalle", detalle)
            }
            SupabaseClientProvider.client.postgrest.rpc("registrar_traza", params)
        } catch (_: Exception) {
            // Silenciado a propósito: una traza fallida no debe romper la operación real.
        }
    }

    suspend fun getTrazas(androidId: String): Result<List<Traza>> {
        return try {
            val trazas = SupabaseClientProvider.client.postgrest
                .rpc("get_trazas", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Traza>()
            Result.success(trazas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
