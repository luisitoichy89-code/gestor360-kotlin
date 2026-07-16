package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.data.sync.SyncReporter
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

@Serializable
data class AprobacionStock(
    val id: Long? = null,
    val producto_id: Long? = null,
    val producto_nombre: String = "",
    val precio: Double? = null,
    val cantidad: Double = 0.0,
    val tipo: String = "",
    val estado: String = "pendiente",
    val venta_id: String? = null,
    val venta_total: Double? = null,
    val solicitado_por_nombre: String? = null,
    val solicitado_por: Long? = null,
    val resuelto_por: Long? = null,
    val resuelto_por_nombre: String? = null,
    val local_id: Long? = null,
    val created_at: String? = null
)

/**
 * Offline-first (antes pedía siempre en vivo y devolvía vacío sin internet):
 * getPendientes lee primero el caché de aprobaciones_cache (ver
 * AprobacionStockCacheEntity) y refresca en background si hay internet.
 * solicitarAumento (agregar a stock un producto existente) sigue el mismo
 * patrón que Merma/Devolucion.solicitar: guarda optimista en caché con id
 * temporal negativo y encola en acciones_pendientes para sincronizar cuando
 * vuelva la conexión.
 * Resolver (aprobar/rechazar) sigue requiriendo conexión sí o sí: mueve stock
 * real del lado del servidor, mismo criterio que Merma/Devolucion.resolver.
 */
class AprobacionStockRepository(private val context: Context = AppContextHolder.context) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getPendientes(androidId: String): Result<List<AprobacionStock>> {
        val localId = localIdActivo()
        val cacheadas = db.aprobacionStockCacheDao().obtener(localId)
        if (cacheadas != null) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch { refrescarDesdeServidor(androidId) }
            }
            return Result.success(cacheadas.toModel())
        }
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.success(emptyList())
        }
        return refrescarDesdeServidor(androidId)
    }

    /** Trae la verdad del servidor (ya filtrada por local_id) y reemplaza el caché de ese local. */
    suspend fun refrescarDesdeServidor(androidId: String): Result<List<AprobacionStock>> {
        val localId = localIdActivo()
        return try {
            val response = SupabaseClientProvider.client.postgrest
                .rpc("get_aprobaciones", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<AprobacionStock>()
            db.aprobacionStockCacheDao().guardar(response.toEntity(localId))
            Result.success(response)
        } catch (e: Exception) {
            val cacheadas = db.aprobacionStockCacheDao().obtener(localId)
            if (cacheadas != null) Result.success(cacheadas.toModel()) else Result.failure(e)
        }
    }

    /** Precarga las aprobaciones pendientes de UN local específico, sin depender del local activo en sesión. */
    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val response = SupabaseClientProvider.client.postgrest
                .rpc("get_aprobaciones", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<AprobacionStock>()
            db.aprobacionStockCacheDao().guardar(response.toEntity(localId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** El vendedor propone offline: queda visible como pendiente de inmediato, igual que solicitarAumento/Merma/Devolucion. */
    suspend fun solicitarProducto(androidId: String, nombre: String, precio: Double, cantidad: Double): Result<Unit> {
        val localId = localIdActivo()
        val idTemporal = -(System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong())
        val actuales = db.aprobacionStockCacheDao().obtener(localId)?.toModel() ?: emptyList()
        val nueva = AprobacionStock(
            id = idTemporal, producto_id = null, producto_nombre = nombre, precio = precio,
            cantidad = cantidad, tipo = "producto", estado = "pendiente", local_id = localId
        )
        db.aprobacionStockCacheDao().guardar((listOf(nueva) + actuales).toEntity(localId))

        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId)
        SyncReporter.reportar(androidId, localIdActivo(), "solicitar_producto", payload)
            put("p_nombre", nombre); put("p_precio", precio); put("p_cantidad", cantidad)
        }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "solicitar_producto", payloadJson = payload.toString(), idLocalTemporal = idTemporal))
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    /** El vendedor propone offline: queda visible como pendiente de inmediato, igual que Merma/Devolucion.solicitar. */
    suspend fun solicitarAumento(androidId: String, productoId: Long, productoNombre: String, cantidad: Double): Result<Unit> {
        val localId = localIdActivo()
        val idTemporal = -(System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong())
        val actuales = db.aprobacionStockCacheDao().obtener(localId)?.toModel() ?: emptyList()
        val nueva = AprobacionStock(
            id = idTemporal, producto_id = productoId, producto_nombre = productoNombre,
            cantidad = cantidad, tipo = "aumento", estado = "pendiente", local_id = localId
        )
        db.aprobacionStockCacheDao().guardar((listOf(nueva) + actuales).toEntity(localId))

        val payload = buildJsonObject {
        SyncReporter.reportar(androidId, localIdActivo(), "solicitar_aumento_stock", payload)
            put("p_android_id", androidId); put("p_local_id", localId)
            put("p_producto_id", productoId); put("p_cantidad", cantidad)
        }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "solicitar_aumento_stock", payloadJson = payload.toString(), idLocalTemporal = idTemporal))
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    suspend fun solicitarAnularVenta(androidId: String, ventaId: String, ventaTotal: Double): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc("solicitar_anular_venta", buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localIdActivo())
                put("p_venta_id", ventaId); put("p_venta_total", ventaTotal)
            })
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun resolver(androidId: String, id: Long, estado: String, aprobadoPor: Long): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para resolver una aprobación"))
        }
        return try {
            SupabaseClientProvider.client.postgrest.rpc("resolver_aprobacion", buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localIdActivo())
                put("p_id", id); put("p_estado", estado); put("p_aprobado_por", aprobadoPor)
            })
            refrescarDesdeServidor(androidId)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
