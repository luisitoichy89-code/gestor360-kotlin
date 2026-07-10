package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Devolucion
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

/** RPC: get_devoluciones, solicitar_devolucion, resolver_devolucion. Filtrado por local_id. */
class DevolucionRepository(private val context: Context = AppContextHolder.context) {
    private val session = SessionManager(context)
    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getPendientes(androidId: String): Result<List<Devolucion>> {
        return try {
            val lista = SupabaseClientProvider.client.postgrest
                .rpc("get_devoluciones", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localIdActivo()) })
                .decodeList<Devolucion>()
            Result.success(lista)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun solicitar(androidId: String, productoId: Long, cantidad: Double, metodo: String, motivo: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc("solicitar_devolucion", buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localIdActivo())
                put("p_producto_id", productoId); put("p_cantidad", cantidad); put("p_metodo", metodo); put("p_motivo", motivo)
            })
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** destino: "stock" (vuelve a venderse) o "merma" (no sirve, se descarta). Ignorado si se rechaza. */
    suspend fun resolver(androidId: String, id: Long, estado: String, destino: String? = null): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc("resolver_devolucion", buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localIdActivo())
                put("p_id", id); put("p_estado", estado); put("p_destino", destino)
            })
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
