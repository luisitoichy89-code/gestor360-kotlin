package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.local.entities.MisVentasCacheEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.Sale
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.data.sync.SyncReporter
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import java.util.UUID
import kotlin.math.round

data class TopVendido(val nombre: String, val total: Double)

class SaleRepository(
    private val context: Context = AppContextHolder.context,
    private val productRepository: ProductRepository = ProductRepository(context),
) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    data class DatosCliente(val ci: String, val telefono: String, val nombre: String, val banco: String? = null, val tarjetaId: String? = null)

    suspend fun guardarVenta(
        androidId: String, carrito: List<CartItem>, metodo: String,
        montoEfectivo: Double, montoTransferencia: Double, cliente: DatosCliente? = null
    ): Result<Unit> {
        if (carrito.isEmpty()) return Result.failure(IllegalStateException("El carrito está vacío"))
        val totalVenta = carrito.sumOf { it.subtotal }
        if (totalVenta <= 0.0) return Result.failure(IllegalStateException("El total de la venta debe ser mayor a 0"))
        val localId = try { localIdActivo() } catch (e: Exception) { return Result.failure(e) }

        val fallos = mutableListOf<Pair<CartItem, Exception>>()

        for (item in carrito) {
            try {
                val ratio = item.subtotal / totalVenta
                val efectivoItem = round(montoEfectivo * ratio * 100) / 100
                val transferenciaItem = round(montoTransferencia * ratio * 100) / 100

                val id = UUID.randomUUID().toString()
                val accionId = UUID.randomUUID().toString()

                productRepository.descontarStockLocal(item.productId, item.cantidad)
                // Turno activo conocido localmente (turno_cache, poblado cada
                // vez que hay respuesta del servidor). Se usa tanto para
                // marcar la venta local como para que "Mis Ventas" y el
                // reporte offline puedan contarla en el turno correcto sin
                // depender de comparar horas.
                val turnoActivoId = db.turnoDao().obtenerActivo(localId)?.id
                val ventaLocal = Sale(
                    id = id, producto_id = item.productId, producto_nombre = item.nombre,
                    cantidad = item.cantidad, total = item.subtotal,
                    metodo = metodo, efectivo = efectivoItem, transferencia = transferenciaItem, local_id = localId,
                    usuario_id = session.getUserId(),
                    cliente_ci = cliente?.ci, cliente_tel = cliente?.telefono, cliente_nombre = cliente?.nombre,
                    tarjeta_id = cliente?.tarjetaId,
                    created_at = java.time.LocalDateTime.now().toString()
                )
                db.ventaDao().insertarUna(ventaLocal.toEntity(localId, sincronizada = false, turnoId = turnoActivoId))
                    db.misVentasCacheDao().insertar(
                        MisVentasCacheEntity(
                            id = id,
                            localId = localId,
                            usuarioId = session.getUserId() ?: 0,
                            productoId = item.productId,
                            productoNombre = item.nombre,
                            cantidad = item.cantidad,
                            total = item.subtotal,
                            metodo = metodo,
                            efectivo = efectivoItem,
                            transferencia = transferenciaItem,
                            tarjetaId = cliente?.tarjetaId,
                            turnoId = turnoActivoId,
                            createdAt = java.time.LocalDateTime.now().toString(),
                            sincronizada = false
                        )
                    )
                    db.misVentasCacheDao().insertar(
                        org.luisito.gestor360.data.local.entities.MisVentasCacheEntity(
                            id = id,
                            localId = localId,
                            usuarioId = session.getUserId() ?: 0,
                            productoId = item.productId,
                            productoNombre = item.nombre,
                            cantidad = item.cantidad,
                            total = item.subtotal,
                            metodo = metodo,
                            efectivo = efectivoItem,
                            transferencia = transferenciaItem,
                            tarjetaId = cliente?.tarjetaId,
                            turnoId = turnoActivoId,
                            createdAt = java.time.LocalDateTime.now().toString(),
                            sincronizada = false
                        )
                    )

                val payload = buildJsonObject {
                    put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id)
                    put("p_producto_id", item.productId)
                    put("p_cantidad", item.cantidad); put("p_total", item.subtotal)
                    put("p_metodo", metodo); put("p_efectivo", efectivoItem); put("p_transferencia", transferenciaItem)
                    put("p_cliente_ci", cliente?.ci ?: ""); put("p_cliente_tel", cliente?.telefono ?: ""); put("p_cliente_nombre", cliente?.nombre ?: "")
                    cliente?.tarjetaId?.let { put("p_tarjeta_id", it) }
                    put("p_accion_id", accionId)
                }
                encolarVenta(payload)
                SyncReporter.reportar(androidId, localId, "registrar_venta", payload)
            } catch (e: Exception) {
                android.util.Log.e("SaleRepository", "guardarVenta: falló el ítem ${item.nombre} (id=${item.productId})", e)
                fallos += item to e
            }
        }

        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)

        if (fallos.isNotEmpty()) {
            val detalle = fallos.joinToString(", ") { (item, e) -> "${item.nombre}: ${e.message ?: e::class.simpleName}" }
            val guardados = carrito.size - fallos.size
            return Result.failure(IllegalStateException("Se guardaron $guardados de ${carrito.size} productos. Fallaron: $detalle"))
        }
        return Result.success(Unit)
    }

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
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refrescarDesdeServidor(androidId: String): Result<List<Sale>> {
        val localId = localIdActivo()
        return try {
            val ventas = SupabaseClientProvider.client.postgrest
                .rpc("get_ventas", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Sale>()
            // Solo marca como sincronizadas las que ya están en Supabase.
            // No borra nada: las ventas offline pendientes quedan intactas.
            ventas.forEach { venta ->
                venta.id?.let { db.ventaDao().marcarSincronizada(it) }
            }
            Result.success(ventas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
            db.ventaDao().insertarTodas(ventas.map { it.toEntity(localId, sincronizada = true) })
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

    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val ventas = SupabaseClientProvider.client.postgrest
                .rpc("get_ventas", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Sale>()
            db.ventaDao().insertarTodas(ventas.map { it.toEntity(localId, sincronizada = true) })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun encolarVenta(payload: JsonObject) {
        db.accionPendienteDao().encolar(
            AccionPendienteEntity(tipo = "registrar_venta", payloadJson = payload.toString())
        )
    }
}
