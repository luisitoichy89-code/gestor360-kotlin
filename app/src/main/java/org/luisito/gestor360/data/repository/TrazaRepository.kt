package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Traza
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

/**
 * RPC: registrar_traza, get_trazas. La limpieza de trazas > 30 días es automática
 * en el servidor. Filtrado por local_id (una traza pertenece al local donde ocurrió).
 */
class TrazaRepository(private val context: Context = AppContextHolder.context) {
    private val session = SessionManager(context)
    private fun localIdActivo(): Long? = session.getLocalId()

    /** Se llama "fire and forget": si falla el registro de traza, no debe tumbar la acción principal. */
    suspend fun registrar(androidId: String, accion: String, detalle: String) {
        try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_local_id", localIdActivo())
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
                .rpc("get_trazas", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localIdActivo()) })
                .decodeList<Traza>()
            Result.success(trazas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
