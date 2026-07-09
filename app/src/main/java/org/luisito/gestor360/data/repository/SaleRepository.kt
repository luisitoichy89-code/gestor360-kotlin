package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
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
 * Offline-first.
 *
 * CAMBIO: guardarVenta() ahora incluye local_id (leído de SessionManager) en la
 * VentaEntity local, para que el historial del caché esté correctamente etiquetado
 * por local incluso antes de sincronizar con el servidor.
 */
class SaleRepository(
    private val context: Context = AppContextHolder.context,
    private val productRepository: ProductRepository = ProductRepository(context),
) {
    private val db = AppDatabase.obtener(context)

    data class DatosCliente(val ci: String, val telefono: String, val nombre: String, val banco: String)

    suspend fun guardarVenta(
        androidId: String, carrito: List<CartItem>, metodo: String,
        montoEfectivo: Double, montoTransferencia: Double, cliente: DatosCliente? = null
    ): Result<Unit> {
        if (carrito.isEmpty()) return Result.failure(IllegalStateException("El carrito está vacío"))
        val totalVenta = carrito.sumOf { it.subtotal }
        if (totalVenta <= 0.0) return Result.failure(IllegalStateException("El total de la venta debe ser mayor a 0"))

        val localId = SessionManager(context).getLocalId()

        for (item in carrito) {
            val ratio = item.subtotal / totalVenta
            val efectivoItem = round(montoEfectivo * ratio * 100) / 100
            val transferenciaItem = round(montoTransferencia * ratio * 100) / 100

            productRepository.descontarStockLocal(item.productId, item.cantidad)
            val ventaLocal = Sale(
                id = null,
                producto_id = item.productId,
                cantidad = item.cantidad,
                total = item.subtotal,
                metodo = metodo,
                efectivo = efectivoItem,
                transferencia = transferenciaItem,
                local_id = localId,                // ← ahora se guarda el local correcto
                cliente_ci = cliente?.ci,
                cliente_tel = cliente?.telefono,
                cliente_nombre = cliente?.nombre,
                created_at = java.time.LocalDateTime.now().toString()
            )
            db.ventaDao().insertarUna(ventaLocal.toEntity(sincronizada = false))

            val payload = buildJsonObject {
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
                // Para admins: el servidor usa este local_id en lugar de intentar
                // resolverlo desde android_id (que daría NULL para un admin).
                if (localId != null) put("p_local_id", localId)
            }
            db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "registrar_venta", payloadJson = payload.toString()))
        }

        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    suspend fun anularVenta(androidId: String, ventaId: String): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para anular una venta"))
        }
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "anular_venta", buildJsonObject { put("p_android_id", androidId); put("p_venta_id", ventaId) }
            )
            db.ventaDao().eliminar(ventaId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSales(androidId: String): Result<List<Sale>> {
        val cacheadas = db.ventaDao().obtenerTodas()
        if (cacheadas.isNotEmpty() && !NetworkMonitor.hayInternet(context)) {
            return Result.success(cacheadas.map { it.toModel() })
        }
        return try {
            val ventas = SupabaseClientProvider.client.postgrest
                .rpc("get_ventas", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Sale>()
            db.ventaDao().insertarTodas(ventas.map { it.toEntity(sincronizada = true) })
            Result.success((cacheadas.map { it.toModel() } + ventas).distinctBy { it.id ?: it.hashCode().toString() })
        } catch (e: Exception) {
            if (cacheadas.isNotEmpty()) Result.success(cacheadas.map { it.toModel() }) else Result.failure(e)
        }
    }

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
