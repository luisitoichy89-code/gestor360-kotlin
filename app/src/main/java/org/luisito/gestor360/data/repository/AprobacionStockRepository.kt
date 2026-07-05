package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider

@Serializable
data class AprobacionStock(
    val id: Long? = null,
    val producto_id: Long? = null,
    val producto_nombre: String = "",
    val precio: Double? = null,
    val cantidad: Double = 0.0,
    val tipo: String = "",
    val estado: String = "pendiente",
    val solicitado_por_nombre: String? = null,
    val created_at: String? = null
)

class AprobacionStockRepository {
    suspend fun getPendientes(androidId: String): Result<List<AprobacionStock>> {
        return try {
            val response = SupabaseClientProvider.client.postgrest
                .rpc("get_aprobaciones_stock", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<AprobacionStock>()
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun solicitarProducto(androidId: String, nombre: String, precio: Double, cantidad: Double): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc("solicitar_producto", buildJsonObject {
                put("p_android_id", androidId); put("p_nombre", nombre); put("p_precio", precio); put("p_cantidad", cantidad)
            })
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun solicitarAumento(androidId: String, productoId: Long, cantidad: Double): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc("solicitar_aumento_stock", buildJsonObject {
                put("p_android_id", androidId); put("p_producto_id", productoId); put("p_cantidad", cantidad)
            })
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun resolver(androidId: String, id: Long, estado: String, aprobadoPor: Long): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc("resolver_aprobacion_stock", buildJsonObject {
                put("p_android_id", androidId); put("p_id", id); put("p_estado", estado); put("p_aprobado_por", aprobadoPor)
            })
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
