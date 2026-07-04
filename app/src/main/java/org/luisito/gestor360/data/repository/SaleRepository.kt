package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.Sale

class SaleRepository {
    data class DatosCliente(val ci: String, val telefono: String, val nombre: String, val banco: String)

    suspend fun guardarVenta(
        androidId: String,
        carrito: List<CartItem>,
        metodo: String,
        montoEfectivo: Double,
        montoTransferencia: Double,
        cliente: DatosCliente? = null
    ): Result<Unit> {
        if (carrito.isEmpty()) return Result.failure(IllegalStateException("Carrito vacío"))
        return try {
            carrito.forEach { item ->
                val total = item.cantidad * item.precio
                val params = buildJsonObject {
                    put("p_android_id", androidId)
                    put("p_producto_id", item.productId.toString())
                    put("p_cantidad", item.cantidad)
                    put("p_total", total)
                    put("p_metodo", metodo)
                    put("p_efectivo", montoEfectivo / carrito.size)
                    put("p_transferencia", montoTransferencia / carrito.size)
                    put("p_usuario_id", androidId)
                    put("p_almacen_id", androidId)
                    put("p_cliente_ci", cliente?.ci ?: "")
                    put("p_cliente_tel", cliente?.telefono ?: "")
                    put("p_cliente_nombre", cliente?.nombre ?: "")
                }
                SupabaseClientProvider.client.postgrest.rpc("registrar_venta", params)
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getSales(androidId: String): Result<List<Sale>> {
        return try {
            val ventas = SupabaseClientProvider.client.postgrest
                .rpc("get_ventas", buildJsonObject { put("p_android_id", androidId); put("p_almacen_id", androidId) })
                .decodeList<Sale>()
            Result.success(ventas)
        } catch (e: Exception) { Result.failure(e) }
    }
}
