package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Sale
import java.util.UUID

class SaleRepository {

    suspend fun getVentas(androidId: String, almacenId: String): Result<List<Sale>> {
        return try {
            val ventas = SupabaseClientProvider.client.postgrest.rpc(
                "get_ventas", mapOf("p_android_id" to androidId, "p_almacen_id" to almacenId)
            ).decodeList<Sale>()
            Result.success(ventas)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun registrarVenta(
        androidId: String, productoId: String, cantidad: Double, total: Double,
        metodo: String, efectivo: Double, transferencia: Double,
        usuarioId: String, almacenId: String,
        clienteCi: String, clienteTel: String, clienteNombre: String
    ): Result<String> {
        return try {
            val result = SupabaseClientProvider.client.postgrest.rpc(
                "registrar_venta",
                mapOf(
                    "p_android_id" to androidId, "p_producto_id" to productoId,
                    "p_cantidad" to cantidad, "p_total" to total,
                    "p_metodo" to metodo, "p_efectivo" to efectivo, "p_transferencia" to transferencia,
                    "p_usuario_id" to usuarioId, "p_almacen_id" to almacenId,
                    "p_cliente_ci" to clienteCi, "p_cliente_tel" to clienteTel, "p_cliente_nombre" to clienteNombre
                )
            ).decodeSingle<String>()
            Result.success(result)
        } catch (e: Exception) { Result.failure(e) }
    }
}
