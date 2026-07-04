package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.MermaPendiente

class MermaRepository {
    suspend fun getMermasPendientes(androidId: String): Result<List<MermaPendiente>> {
        return try {
            val mermas = SupabaseClientProvider.client.postgrest.rpc(
                "get_mermas_pendientes", buildJsonObject { put("p_android_id", androidId) }
            ).decodeList<MermaPendiente>()
            Result.success(mermas)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun crearMerma(androidId: String, productoId: String, productoNombre: String, cantidad: Double, motivo: String, almacenId: String, solicitadoPor: String, solicitadoPorNombre: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "crear_merma",
                buildJsonObject {
                    put("p_android_id", androidId); put("p_producto_id", productoId)
                    put("p_producto_nombre", productoNombre); put("p_cantidad", cantidad)
                    put("p_motivo", motivo); put("p_almacen_id", almacenId)
                    put("p_solicitado_por", solicitadoPor); put("p_solicitado_por_nombre", solicitadoPorNombre)
                }
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun resolverMerma(androidId: String, mermaId: String, estado: String, aprobadoPor: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "resolver_merma",
                buildJsonObject { put("p_android_id", androidId); put("p_merma_id", mermaId); put("p_estado", estado); put("p_aprobado_por", aprobadoPor) }
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
