package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.Sale
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import kotlin.math.round

data class TopVendido(val nombre: String, val total: Double)

/**
 * Offline-first. registrar_venta inserta UN producto por llamada (no el
 * carrito completo) y "ventas" no guarda producto_nombre — solo producto_id.
 * Todo va filtrado/etiquetado con el local_id activo (SessionManager).
 */
class SaleRepository(
    private val context: Context = AppContextHolder.context,
    private val productRepository: ProductRepository = ProductRepository(context),
    //private val trazaRepository: TrazaRepository = TrazaRepository()
) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    data class DatosCliente(val ci: String, val telefono: String, val nombre: String, val banco: String)

    /**
     * Guarda local YA (stock descontado al instante + fila de venta visible de
     * inmediato) y encola una acción por cada producto del carrito. Si hay
     * internet, dispara sincronización en el momento; si no, queda pendiente.
     */
    suspend fun guardarVenta(
        androidId: String, carrito: List<CartItem>, metodo: String,
        montoEfectivo: Double, montoTransferencia: Double, cliente: DatosCliente? = null
    ): Result<Unit> {
        if (carrito.isEmpty()) return Result.failure(IllegalStateException("El carrito está vacío"))
        val totalVenta = carrito.sumOf { it.subtotal }
        if (totalVenta <= 0.0) return Result.failure(IllegalStateException("El total de la venta debe ser mayor a 0"))
        val localId = localIdActivo()

        for (item in carrito) {
            val ratio = item.subtotal / totalVenta
            val efectivoItem = round(montoEfectivo * ratio * 100) / 100
            val transferenciaItem = round(montoTransferencia * ratio * 100) / 100

            // 1. Aplicar YA en local: baja el stock cacheado y agrega la venta a la vista.
            productRepository.descontarStockLocal(item.productId, item.cantidad)
            val ventaLocal = Sale(
                id = null, producto_id = item.productId, cantidad = item.cantidad, total = item.subtotal,
                metodo = metodo, efectivo = efectivoItem, transferencia = transferenciaItem, local_id = localId,
                cliente_ci = cliente?.ci, cliente_tel = cliente?.telefono, cliente_nombre = cliente?.nombre,
                created_at = java.time.LocalDateTime.now().toString()
            )
            db.ventaDao().insertarUna(ventaLocal.toEntity(localId, sincronizada = false))

            // 2. Encolar la llamada real para cuando haya internet.
            val payload = buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localId); put("p_producto_id", item.productId)
                put("p_cantidad", item.cantidad); put("p_total", item.subtotal)
                put("p_metodo", metodo); put("p_efectivo", efectivoItem); put("p_transferencia", transferenciaItem)
                put("p_cliente_ci", cliente?.ci ?: ""); put("p_cliente_tel", cliente?.telefono ?: ""); put("p_cliente_nombre", cliente?.nombre ?: "")
            }
            db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "registrar_venta", payloadJson = payload.toString()))
        }

        //trazaRepository.registrar(androidId, "registrar_venta", "Total: $totalVenta ($metodo)")
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    /**
     * Anular una venta necesita internet sí o sí (no tiene sentido encolarla:
     * si el dispositivo está offline es porque la venta a anular tampoco se
     * sincronizó todavía).
     */
    suspend fun anularVenta(androidId: String, ventaId: String): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para anular una venta"))
        }
        val localId = localIdActivo()
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "anular_venta", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId); put("p_venta_id", ventaId) }
            )
            db.ventaDao().eliminar(ventaId)
            //trazaRepository.registrar(androidId, "anular_venta", "Venta anulada: $ventaId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lee del caché local primero (ya filtrado por local); refresca de fondo si hay internet. */
    suspend fun getSales(androidId: String): Result<List<Sale>> {
        val localId = localIdActivo()
        val cacheadas = db.ventaDao().obtenerTodas(localId)
        if (cacheadas.isNotEmpty() && !NetworkMonitor.hayInternet(context)) {
            return Result.success(cacheadas.map { it.toModel() })
        }
        return try {
            val ventas = SupabaseClientProvider.client.postgrest
                .rpc("get_ventas", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Sale>()
            // No se borra el caché entero aquí: podría haber ventas locales sin sincronizar todavía.
            db.ventaDao().insertarTodas(ventas.map { it.toEntity(localId, sincronizada = true) })
            Result.success((cacheadas.map { it.toModel() } + ventas).distinctBy { it.id ?: it.hashCode().toString() })
        } catch (e: Exception) {
            if (cacheadas.isNotEmpty()) Result.success(cacheadas.map { it.toModel() }) else Result.failure(e)
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
