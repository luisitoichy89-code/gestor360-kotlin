package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.Sale

@Serializable
data class TopVendido(val producto_nombre: String, val total: Double)

/**
 * RPC: registrar_venta, get_ventas. El servidor recibe el carrito completo como un array
 * JSON y hace, en una sola transacción, el reparto proporcional de efectivo/transferencia
 * por ítem y el descuento de stock — ya no se hace ese cálculo ni el loop de inserts en Kotlin.
 *
 * Asumo que registrar_venta espera el carrito en un parámetro p_items (jsonb) con esta forma:
 * [{ "producto_id": 1, "cantidad": 2, "precio_unit": 10.0 }, ...]
 * Si tu función espera otros nombres de campos dentro del array, dímelo y ajusto el mapeo.
 */
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
        if (carrito.isEmpty()) return Result.failure(IllegalStateException("El carrito está vacío"))

        return try {
            val items: JsonArray = buildJsonArray {
                carrito.forEach { item ->
                    add(buildJsonObject {
                        put("producto_id", item.productId)
                        put("cantidad", item.cantidad)
                        put("precio_unit", item.precio)
                    })
                }
            }

            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_items", items)
                put("p_metodo", metodo)
                put("p_efectivo", montoEfectivo)
                put("p_transferencia", montoTransferencia)
                put("p_cliente_ci", cliente?.ci ?: "")
                put("p_cliente_tel", cliente?.telefono ?: "")
                put("p_cliente_nombre", cliente?.nombre ?: "")
            }

            SupabaseClientProvider.client.postgrest.rpc("registrar_venta", params)
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

    /** Top 5 por cantidad vendida, calculado en memoria a partir de get_ventas. */
    suspend fun getTop5Vendidos(androidId: String): Result<List<TopVendido>> {
        return getSales(androidId).map { ventas ->
            ventas.groupBy { it.producto_nombre }
                .map { (nombre, filas) -> TopVendido(nombre, filas.sumOf { it.cantidad }) }
                .sortedByDescending { it.total }
                .take(5)
        }
    }
}
