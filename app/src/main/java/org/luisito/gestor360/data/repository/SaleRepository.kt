package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.Sale
import kotlin.math.round

data class TopVendido(val nombre: String, val total: Double)

/**
 * registrar_venta inserta UN producto por llamada (no el carrito completo), y
 * "ventas" no guarda producto_nombre — solo producto_id. Por eso todo lo que
 * necesita mostrar nombres primero cruza con ProductRepository.getProducts().
 */
class SaleRepository(
    private val productRepository: ProductRepository = ProductRepository(),
    private val trazaRepository: TrazaRepository = TrazaRepository()
) {

    data class DatosCliente(val ci: String, val telefono: String, val nombre: String, val banco: String)

    /**
     * Inserta una fila por cada producto del carrito, con efectivo/transferencia
     * repartidos proporcionalmente al peso de cada ítem en el total de la venta.
     * Si un ítem falla a mitad de camino, los anteriores ya quedaron guardados
     * (registrar_venta no es una transacción conjunta del carrito completo).
     */
    suspend fun guardarVenta(
        androidId: String,
        carrito: List<CartItem>,
        metodo: String,
        montoEfectivo: Double,
        montoTransferencia: Double,
        cliente: DatosCliente? = null
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
                    put("p_android_id", androidId)
                    put("p_producto_id", item.productId)
                    put("p_cantidad", item.cantidad)
                    put("p_total", item.subtotal)
                    put("p_metodo", metodo)
                    put("p_efectivo", efectivoItem)
                    put("p_transferencia", transferenciaItem)
                    put("p_cliente_ci", cliente?.ci ?: "")
                    put("p_cliente_tel", cliente?.telefono ?: "")
                    put("p_cliente_nombre", cliente?.nombre ?: "")
                }
                SupabaseClientProvider.client.postgrest.rpc("registrar_venta", params)
            }
            trazaRepository.registrar(androidId, "registrar_venta", "Total: $totalVenta ($metodo)")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSales(androidId: String): Result<List<Sale>> {
        return try {
            val ventas = SupabaseClientProvider.client.postgrest
                .rpc("get_ventas", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Sale>()
            Result.success(ventas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** ventas + nombre de producto ya resuelto (join en memoria contra productos). */
    suspend fun getSalesConNombre(androidId: String): Result<List<Pair<Sale, String>>> {
        return try {
            val ventas = getSales(androidId).getOrThrow()
            val productos = productRepository.getProducts(androidId).getOrDefault(emptyList())
            val nombresPorId = productos.associateBy({ it.id }, { it.nombre })
            Result.success(ventas.map { venta -> venta to (nombresPorId[venta.producto_id] ?: "Producto #${venta.producto_id}") })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTop5Vendidos(androidId: String): Result<List<TopVendido>> {
        return getSalesConNombre(androidId).map { lista ->
            lista.groupBy { it.second }
                .map { (nombre, filas) -> TopVendido(nombre, filas.sumOf { it.first.cantidad }) }
                .sortedByDescending { it.total }
                .take(5)
        }
    }
}
