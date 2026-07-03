package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.Sale
import java.time.LocalDateTime
import kotlin.math.round

@Serializable
data class TopVendido(val producto_nombre: String, val total: Double)

/**
 * IMPORTANTE: se usa buildJsonObject en vez de mapOf(...) porque el payload de una venta
 * mezcla String, Long y Double. Un mapOf con tipos mixtos se infiere como Map<String, Any>,
 * y kotlinx.serialization no puede serializar "Any" ("Serializer for class 'Any' is not
 * found"). JsonObject sí tiene serializador propio.
 */
class SaleRepository(
    private val productRepository: ProductRepository = ProductRepository()
) {

    data class DatosCliente(val ci: String, val telefono: String, val nombre: String, val banco: String)

    /**
     * Guarda la venta completa: inserta una fila en "ventas" por cada producto del carrito
     * (con efectivo/transferencia repartidos proporcionalmente al peso de cada ítem en el
     * total, igual que el backend Flask) y descuenta el stock vendido de cada producto.
     */
    suspend fun guardarVenta(
        carrito: List<CartItem>,
        metodo: String,
        montoEfectivo: Double,
        montoTransferencia: Double,
        usuarioId: Long,
        almacenId: String,
        cliente: DatosCliente? = null
    ): Result<Unit> {
        if (carrito.isEmpty()) return Result.failure(IllegalStateException("El carrito está vacío"))

        val totalVenta = carrito.sumOf { it.subtotal }
        if (totalVenta <= 0.0) return Result.failure(IllegalStateException("El total de la venta debe ser mayor a 0"))

        return try {
            val ahora = LocalDateTime.now().toString()

            for (item in carrito) {
                val itemTotal = item.subtotal
                val ratio = itemTotal / totalVenta
                val efectivoItem = round(montoEfectivo * ratio * 100) / 100
                val transferenciaItem = round(montoTransferencia * ratio * 100) / 100

                val payload = buildJsonObject {
                    put("producto_id", item.productId)
                    put("producto_nombre", item.nombre)
                    put("cantidad", item.cantidad)
                    put("precio_unit", item.precio)
                    put("total", itemTotal)
                    put("metodo", metodo)
                    put("efectivo", efectivoItem)
                    put("transferencia", transferenciaItem)
                    put("usuario_id", usuarioId)
                    put("almacen_id", almacenId)
                    put("cliente_ci", cliente?.ci ?: "")
                    put("cliente_tel", cliente?.telefono ?: "")
                    put("cliente_nombre", cliente?.nombre ?: "")
                    put("created_at", ahora)
                }

                SupabaseClientProvider.client.from("ventas").insert(payload)

                productRepository.descontarStock(item.productId, item.stockDisponible, item.cantidad)
                    .onFailure { /* la venta ya quedó registrada; el stock se puede corregir manualmente */ }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSalesByAlmacen(almacenId: String): Result<List<Sale>> {
        return try {
            val ventas = SupabaseClientProvider.client
                .from("ventas")
                .select {
                    filter { eq("almacen_id", almacenId) }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(100)
                }
                .decodeList<Sale>()
            Result.success(ventas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Top 5 productos más vendidos por cantidad, para sugerir accesos rápidos en Ventas. */
    suspend fun getTop5Vendidos(almacenId: String): Result<List<TopVendido>> {
        return try {
            val ventas = SupabaseClientProvider.client
                .from("ventas")
                .select { filter { eq("almacen_id", almacenId) } }
                .decodeList<Sale>()

            val top = ventas
                .groupBy { it.producto_nombre }
                .map { (nombre, filas) -> TopVendido(nombre, filas.sumOf { it.cantidad }) }
                .sortedByDescending { it.total }
                .take(5)

            Result.success(top)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
