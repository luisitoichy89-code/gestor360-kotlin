package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.Sale
import kotlin.math.round

data class TopVendido(val nombre: String, val total: Double)

class SaleRepository(
    private val productRepository: ProductRepository = ProductRepository(),
    private val trazaRepository: TrazaRepository = TrazaRepository()
) {
    data class DatosCliente(val ci: String, val telefono: String, val nombre: String, val banco: String)

    suspend fun guardarVenta(
        androidId: String, carrito: List<CartItem>, metodo: String,
        montoEfectivo: Double, montoTransferencia: Double, cliente: DatosCliente? = null
    ): Result<Unit> {
        if (carrito.isEmpty()) return Result.failure(IllegalStateException("El carrito está vacío"))
        val totalVenta = carrito.sumOf { it.subtotal }
        if (totalVenta <= 0.0) return Result.failure(IllegalStateException("El total de la venta debe ser mayor a 0"))
        return try {
            for (item in carrito) {
                val ratio = item.subtotal / totalVenta
                val efectivoItem = round(montoEfectivo * ratio * 100) / 100
                val transferenciaItem = round(montoTransferencia * ratio * 100) / 100
                val params = buildJsonObject {
                    put("p_android_id", androidId); put("p_producto_id", item.productId)
                    put("p_cantidad", item.cantidad); put("p_total", item.subtotal)
                    put("p_metodo", metodo); put("p_efectivo", efectivoItem); put("p_transferencia", transferenciaItem)
                    put("p_cliente_ci", cliente?.ci ?: ""); put("p_cliente_tel", cliente?.telefono ?: ""); put("p_cliente_nombre", cliente?.nombre ?: "")
                }
                SupabaseClientProvider.client.postgrest.rpc("registrar_venta", params)
            }
            trazaRepository.registrar(androidId, "registrar_venta", "Total: $totalVenta ($metodo)")
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun anularVenta(androidId: String, ventaId: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "anular_venta", buildJsonObject { put("p_android_id", androidId); put("p_venta_id", ventaId) }
            )
            trazaRepository.registrar(androidId, "anular_venta", "Venta anulada: $ventaId")
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getSales(androidId: String): Result<List<Sale>> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc("get_ventas", buildJsonObject { put("p_android_id", androidId) }).decodeList<Sale>()
            Result.success(emptyList())
        } catch (e: Exception) { Result.failure(e) }
    }
}
