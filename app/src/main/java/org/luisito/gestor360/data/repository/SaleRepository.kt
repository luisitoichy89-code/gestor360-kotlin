package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Sale

class SaleRepository {
    suspend fun getVentas(androidId: String, almacenId: String): Result<List<Sale>> {
        return try {
            val ventas = SupabaseClientProvider.client.postgrest.rpc(
                "get_ventas",
                buildJsonObject { put("p_android_id", androidId); put("p_almacen_id", almacenId) }
            ).decodeList<Sale>()
            Result.success(ventas)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun registrarVenta(
        androidId: String, productoId: String, cantidad: Double, total: Double,
        metodo: String, efectivo: Double, transferencia: Double,
        usuarioId: String, almacenId: String,
        clienteCi: String, clienteTel: String, clienteNombre: String
    ): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "registrar_venta",
                buildJsonObject {
                    put("p_android_id", androidId); put("p_producto_id", productoId)
                    put("p_cantidad", cantidad); put("p_total", total)
                    put("p_metodo", metodo); put("p_efectivo", efectivo); put("p_transferencia", transferencia)
                    put("p_usuario_id", usuarioId); put("p_almacen_id", almacenId)
                    put("p_cliente_ci", clienteCi); put("p_cliente_tel", clienteTel); put("p_cliente_nombre", clienteNombre)
                }
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
