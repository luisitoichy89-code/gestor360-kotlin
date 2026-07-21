package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.TurnoEntity
import org.luisito.gestor360.data.local.entities.VentaEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.Sale
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncReporter
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.round

class SaleRepository(private val context: Context = AppContextHolder.context) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)
    private val productRepository = ProductRepository(context)

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    data class DatosCliente(
        val ci: String? = null,
        val telefono: String? = null,
        val nombre: String? = null,
        val tarjetaId: String? = null
    )

    suspend fun guardarVenta(
        androidId: String, carrito: List<CartItem>, metodo: String,
        montoEfectivo: Double, montoTransferencia: Double, cliente: DatosCliente? = null
    ): Result<Unit> {
        if (carrito.isEmpty()) return Result.failure(IllegalStateException("El carrito está vacío"))
        val totalVenta = carrito.sumOf { it.subtotal }
        if (totalVenta <= 0.0) return Result.failure(IllegalStateException("El total de la venta debe ser mayor a 0"))

        val localId = try { localIdActivo() } catch (e: Exception) { return Result.failure(e) }
        val userId = session.getUserId().takeIf { it > 0 }
        val now = LocalDateTime.now().toString()

        // Obtener o crear turno activo local (offline-first)
        var turnoActivo = db.turnoDao().obtenerActivo(localId)
        if (turnoActivo == null) {
            turnoActivo = TurnoEntity(
                id = 0, // ID temporal, se actualiza al sincronizar
                localId = localId,
                usuarioId = userId,
                apertura = 0.0,
                cierre = null,
                diferencia = null,
                createdAt = now
            )
            db.turnoDao().insertar(turnoActivo)
            turnoActivo = db.turnoDao().obtenerActivo(localId)
        }

        val fallos = mutableListOf<Pair<CartItem, Exception>>()

        for (item in carrito) {
            try {
                val ratio = item.subtotal / totalVenta
                val efectivoItem = round(montoEfectivo * ratio * 100) / 100
                val transferenciaItem = round(montoTransferencia * ratio * 100) / 100

                val id = UUID.randomUUID().toString()
                val accionId = UUID.randomUUID().toString()

                productRepository.descontarStockLocal(item.productId, item.cantidad)

                val ventaLocal = Sale(
                    id = id, producto_id = item.productId, producto_nombre = item.nombre,
                    cantidad = item.cantidad, total = item.subtotal,
                    metodo = metodo, efectivo = efectivoItem, transferencia = transferenciaItem,
                    local_id = localId, usuario_id = userId,
                    cliente_ci = cliente?.ci, cliente_tel = cliente?.telefono, cliente_nombre = cliente?.nombre,
                    tarjeta_id = cliente?.tarjetaId,
                    turno_id = turnoActivo?.id,
                    created_at = now
                )
                db.ventaDao().insertarUna(ventaLocal.toEntity(localId, sincronizada = false))

                val payload = buildJsonObject {
                    put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id)
                    put("p_producto_id", item.productId); put("p_cantidad", item.cantidad); put("p_total", item.subtotal)
                    put("p_metodo", metodo); put("p_efectivo", efectivoItem); put("p_transferencia", transferenciaItem)
                    put("p_cliente_ci", cliente?.ci ?: ""); put("p_cliente_tel", cliente?.telefono ?: ""); put("p_cliente_nombre", cliente?.nombre ?: "")
                    cliente?.tarjetaId?.let { put("p_tarjeta_id", it) }
                    put("p_accion_id", accionId)
                }
                encolarVenta(payload)
                SyncReporter.reportar(androidId, localId, "registrar_venta", payload)
            } catch (e: Exception) {
                android.util.Log.e("SaleRepository", "guardarVenta: falló el ítem ${item.nombre} (id=${item.productId})", e)
                fallos.add(item to e)
            }
        }

        return if (fallos.isEmpty()) Result.success(Unit)
        else Result.failure(Exception("${fallos.size} ítems fallaron: ${fallos.joinToString { it.first.nombre }}"))
    }

    private suspend fun encolarVenta(payload: kotlinx.serialization.json.JsonObject) {
        db.accionPendienteDao().encolar(
            org.luisito.gestor360.data.local.entities.AccionPendienteEntity(
                tipo = "registrar_venta", payloadJson = payload.toString()
            )
        )
        if (NetworkMonitor.hayInternet(context)) {
            SyncWorker.sincronizarAhora(context)
        }
    }

    suspend fun refrescarDesdeServidor(androidId: String): Result<List<Sale>> {
        val localId = localIdActivo()
        return try {
            val ventas = SupabaseClientProvider.client.postgrest
                .rpc("get_ventas", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Sale>()
            db.ventaDao().reemplazarDeLocal(localId, ventas.map { it.toEntity(localId, sincronizada = true) })
            Result.success(ventas)
        } catch (e: Exception) {
            Result.failure(e)
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

    suspend fun anularVenta(androidId: String, ventaId: String): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para anular una venta"))
        }
        return try {
            SupabaseClientProvider.client.postgrest.rpc("anular_venta", buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localIdActivo()); put("p_id", ventaId)
            })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
