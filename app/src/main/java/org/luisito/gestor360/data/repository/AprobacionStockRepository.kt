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
import org.luisito.gestor360.data.sync.SyncReporter
import org.luisito.gestor360.data.sync.SyncWorker
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
    val solicitado_por: Long? = null,
    val solicitado_por_nombre: String? = null,
    val resuelto_por: Long? = null,
    val resuelto_por_nombre: String? = null,
    val local_id: Long? = null,
    val created_at: String? = null
)

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
        if (!NetworkMonitor.hayInternet(context)) return Result.success(emptyList())
        return refrescarDesdeServidor(androidId)
    }

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

    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val response = SupabaseClientProvider.client.postgrest
                .rpc("get_aprobaciones", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<AprobacionStock>()
            db.aprobacionStockCacheDao().guardar(response.toEntity(localId))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun solicitarProducto(androidId: String, nombre: String, precio: Double, cantidad: Double): Result<Unit> {
        val localId = localIdActivo()
        val idTemporal = -(System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong())
        val actuales = db.aprobacionStockCacheDao().obtener(localId)?.toModel() ?: emptyList()
        val nueva = AprobacionStock(id = idTemporal, producto_nombre = nombre, precio = precio, cantidad = cantidad, tipo = "producto", estado = "pendiente", local_id = localId)
        db.aprobacionStockCacheDao().guardar((listOf(nueva) + actuales).toEntity(localId))
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId)
            put("p_nombre", nombre); put("p_precio", precio); put("p_cantidad", cantidad)
        }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "solicitar_producto", payloadJson = payload.toString(), idLocalTemporal = idTemporal))
        try { SyncReporter.reportar(androidId, localId, "solicitar_producto", payload) } catch (_: Exception) {}
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    suspend fun solicitarAumento(androidId: String, productoId: Long, productoNombre: String, cantidad: Double): Result<Unit> {
        val localId = localIdActivo()
        val idTemporal = -(System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong())
        val actuales = db.aprobacionStockCacheDao().obtener(localId)?.toModel() ?: emptyList()
        val nueva = AprobacionStock(id = idTemporal, producto_id = productoId, producto_nombre = productoNombre, cantidad = cantidad, tipo = "aumento", estado = "pendiente", local_id = localId)
        db.aprobacionStockCacheDao().guardar((listOf(nueva) + actuales).toEntity(localId))
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId)
            put("p_producto_id", productoId); put("p_cantidad", cantidad)
        }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "solicitar_aumento_stock", payloadJson = payload.toString(), idLocalTemporal = idTemporal))
        try { SyncReporter.reportar(androidId, localId, "solicitar_aumento_stock", payload) } catch (_: Exception) {}
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
        if (!NetworkMonitor.hayInternet(context)) return Result.failure(IllegalStateException("Necesitas conexión para resolver una aprobación"))
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
