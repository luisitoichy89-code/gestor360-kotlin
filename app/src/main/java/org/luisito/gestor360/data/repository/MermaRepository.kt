package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.MermaPendiente

/** RPC: get_mermas_pendientes, crear_merma, resolver_merma. */
class MermaRepository(
    private val trazaRepository: TrazaRepository = TrazaRepository()
) {

    suspend fun solicitar(
        androidId: String,
        productoId: Long,
        cantidad: Double,
        motivo: String
    ): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_producto_id", productoId)
                put("p_cantidad", cantidad)
                put("p_motivo", motivo)
            }
            SupabaseClientProvider.client.postgrest.rpc("crear_merma", params)
            trazaRepository.registrar(androidId, "proponer_merma", "producto_id=$productoId cantidad=$cantidad")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPendientes(androidId: String): Result<List<MermaPendiente>> {
        return try {
            val lista = SupabaseClientProvider.client.postgrest
                .rpc("get_mermas_pendientes", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<MermaPendiente>()
            Result.success(lista)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** p_estado: "aprobada" o "rechazada". El descuento real de stock lo hace el RPC server-side. */
    suspend fun resolver(androidId: String, mermaId: Long, estado: String): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_merma_id", mermaId)
                put("p_estado", estado)
            }
            SupabaseClientProvider.client.postgrest.rpc("resolver_merma", params)
            trazaRepository.registrar(androidId, "resolver_merma", "merma_id=$mermaId estado=$estado")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun aprobar(androidId: String, mermaId: Long): Result<Unit> = resolver(androidId, mermaId, "aprobada")

    suspend fun rechazar(androidId: String, mermaId: Long): Result<Unit> = resolver(androidId, mermaId, "rechazada")
}
