package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.MermaPendiente

class MermaRepository {

    suspend fun getMermasPendientes(androidId: String): Result<List<MermaPendiente>> {
        return try {
            val mermas = SupabaseClientProvider.client.postgrest.rpc(
                "get_mermas_pendientes", mapOf("p_android_id" to androidId)
            ).decodeList<MermaPendiente>()
            Result.success(mermas)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun crearMerma(androidId: String, productoId: String, productoNombre: String, cantidad: Double, motivo: String, almacenId: String, solicitadoPor: String, solicitadoPorNombre: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "crear_merma",
                mapOf(
                    "p_android_id" to androidId, "p_producto_id" to productoId,
                    "p_producto_nombre" to productoNombre, "p_cantidad" to cantidad,
                    "p_motivo" to motivo, "p_almacen_id" to almacenId,
                    "p_solicitado_por" to solicitadoPor, "p_solicitado_por_nombre" to solicitadoPorNombre
                )
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun resolverMerma(androidId: String, mermaId: String, estado: String, aprobadoPor: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "resolver_merma",
                mapOf("p_android_id" to androidId, "p_merma_id" to mermaId, "p_estado" to estado, "p_aprobado_por" to aprobadoPor)
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
