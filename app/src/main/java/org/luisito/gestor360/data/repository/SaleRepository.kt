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
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.Sale
import org.luisito.gestor360.data.models.SaleItem
import org.luisito.gestor360.data.models.ClienteInfo
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncReporter
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

class SaleRepository(
    private val context: Context = AppContextHolder.context,
    private val productRepository: ProductRepository = ProductRepository(context),
) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getSales(androidId: String): Result<List<Sale>> {
        val localId = localIdActivo()
        val cacheadas = db.ventaDao().obtenerTodas(localId)
        if (cacheadas.isNotEmpty()) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch { refrescarDesdeServidor(androidId) }
            }
            return Result.success(cacheadas.map { it.toModel() })
        }
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.success(emptyList())
        }
        return refrescarDesdeServidor(androidId)
    }

    suspend fun refrescarDesdeServidor(androidId: String): Result<List<Sale>> {
        val localId = localIdActivo()
        return try {
            val ventas = SupabaseClientProvider.client.postgrest
                .rpc("get_ventas", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Sale>()
            db.ventaDao().limpiarDeLocal(localId)
            db.ventaDao().insertarTodas(ventas.map { it.toEntity(localId) })
            Result.success(ventas)
        } catch (e: Exception) {
            val cacheadas = db.ventaDao().obtenerTodas(localId)
            if (cacheadas.isNotEmpty()) Result.success(cacheadas.map { it.toModel() }) else Result.failure(e)
        }
    }

    suspend fun guardarVenta(
        androidId: String, items: List<SaleItem>, metodo: String,
        efectivo: Double, transferencia: Double, cliente: ClienteInfo?
    ): Result<Unit> {
        val localId = localIdActivo()
        val fallos = mutableListOf<Pair<SaleItem, Exception>>()

        for (item in items) {
            try {
                val efectivoItem = if (metodo == "efectivo" || metodo == "mixto") item.subtotal else 0.0
                val transferenciaItem = if (metodo == "transferencia" || metodo == "mixto") item.subtotal else 0.0

                val ventaLocal = Sale(
                    id = java.util.UUID.randomUUID().toString(),
                    productoId = item.productId,
                    productoNombre = item.nombre,
                    cantidad = item.cantidad,
                    total = item.subtotal,
                    metodo = metodo,
                    efectivo = efectivoItem,
                    transferencia = transferenciaItem,
                    local_id = localId,
                    cliente_ci = cliente?.ci,
                    cliente_tel = cliente?.telefono,
                    cliente_nombre = cliente?.nombre,
                    tarjeta_id = cliente?.tarjetaId,
                    created_at = java.time.LocalDateTime.now().toString()
                )

                db.ventaDao().insertarUna(ventaLocal.toEntity(localId, sincronizada = false))

                val payload = buildJsonObject {
                    put("p_android_id", androidId); put("p_local_id", localId); put("p_producto_id", item.productId)
                    put("p_cantidad", item.cantidad); put("p_total", item.subtotal)
                    put("p_metodo", metodo); put("p_efectivo", efectivoItem); put("p_transferencia", transferenciaItem)
                    put("p_cliente_ci", cliente?.ci ?: ""); put("p_cliente_tel", cliente?.telefono ?: ""); put("p_cliente_nombre", cliente?.nombre ?: "")
                    cliente?.tarjetaId?.let { put("p_tarjeta_id", it) }
                }
                db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "registrar_venta", payloadJson = payload.toString()))
                try { SyncReporter.reportar(androidId, localId, "registrar_venta", payload) } catch (_: Exception) {}
            } catch (e: Exception) {
                android.util.Log.e("SaleRepository", "guardarVenta: falló el ítem ${item.nombre} (id=${item.productId})", e)
                fallos += item to e
            }
        }

        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)

        if (fallos.isNotEmpty()) {
            val detalle = fallos.joinToString(", ") { (item, e) -> "${item.nombre}: ${e.message ?: e::class.simpleName}" }
            return Result.failure(Exception("No se pudo registrar: $detalle"))
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

    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val ventas = SupabaseClientProvider.client.postgrest
                .rpc("get_ventas", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Sale>()
            db.ventaDao().insertarTodas(ventas.map { it.toEntity(localId) })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
