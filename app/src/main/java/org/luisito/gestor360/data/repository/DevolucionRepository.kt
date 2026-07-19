package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.Devolucion
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import java.util.UUID

class DevolucionRepository(private val context: Context = AppContextHolder.context) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)
    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getPendientes(androidId: String): Result<List<Devolucion>> {
        val localId = localIdActivo()
        val cacheadas = db.devolucionCacheDao().obtener(localId)
        if (cacheadas != null) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch { refrescarDesdeServidor(androidId) }
            }
            return Result.success(cacheadas.toModel())
        }
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.success(emptyList())
        }
        return try {
            refrescarDesdeServidor(androidId)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    suspend fun refrescarDesdeServidor(androidId: String): Result<List<Devolucion>> {
        val localId = localIdActivo()
        return try {
            val lista = SupabaseClientProvider.client.postgrest
                .rpc("get_devoluciones", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Devolucion>()
            db.devolucionCacheDao().guardar(lista.toEntity(localId))
            Result.success(lista)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun solicitar(androidId: String, productoId: String, productoNombre: String, cantidad: Double, metodo: String, motivo: String): Result<Unit> {
        val yaPendiente = db.accionPendienteDao().obtenerPendientes()
            .filter { it.tipo == "solicitar_devolucion" }
            .any { it.payloadJson.contains("\"p_producto_id\":\"$productoId\"") }
        if (yaPendiente) return Result.success(Unit)

        val localId = localIdActivo()
        val id = UUID.randomUUID().toString()
        val accionId = UUID.randomUUID().toString()
        val actuales = db.devolucionCacheDao().obtener(localId)?.toModel() ?: emptyList()
        val nueva = Devolucion(
            id = id, producto_id = productoId, producto_nombre = productoNombre,
            cantidad = cantidad, metodo = metodo, motivo = motivo, estado = "pendiente", local_id = localId
        )
        db.devolucionCacheDao().guardar((listOf(nueva) + actuales).toEntity(localId))

        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id)
            put("p_producto_id", productoId); put("p_cantidad", cantidad); put("p_metodo", metodo); put("p_motivo", motivo)
            put("p_accion_id", accionId)
        }
        encolarYSincronizar("solicitar_devolucion", payload)
        return Result.success(Unit)
    }

    suspend fun resolver(androidId: String, id: String, estado: String, destino: String? = null): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para resolver una devolución"))
        }
        val localId = localIdActivo()
        return try {
            val accionId = UUID.randomUUID().toString()
            SupabaseClientProvider.client.postgrest.rpc("resolver_devolucion", buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localId)
                put("p_id", id); put("p_estado", estado); put("p_destino", destino)
                put("p_accion_id", accionId)
            })
            refrescarDesdeServidor(androidId)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val lista = SupabaseClientProvider.client.postgrest
                .rpc("get_devoluciones", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Devolucion>()
            db.devolucionCacheDao().guardar(lista.toEntity(localId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun encolarYSincronizar(tipo: String, payload: JsonObject) {
        db.accionPendienteDao().encolar(
            AccionPendienteEntity(tipo = tipo, payloadJson = payload.toString())
        )
        if (NetworkMonitor.hayInternet(context)) {
            SyncWorker.sincronizarAhora(context)
        }
    }
}
