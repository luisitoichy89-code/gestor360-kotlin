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
import org.luisito.gestor360.data.models.MermaPendiente
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncReporter
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

class MermaRepository(private val context: Context = AppContextHolder.context) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getPendientes(androidId: String): Result<List<MermaPendiente>> {
        val localId = localIdActivo()
        val cacheadas = db.mermaDao().obtenerPendientes(localId)
        if (cacheadas.isNotEmpty()) {
            if (NetworkMonitor.hayInternet(context)) refrescarDesdeServidor(androidId)
            return Result.success(cacheadas.map { it.toModel() })
        }
        if (!NetworkMonitor.hayInternet(context)) return Result.success(emptyList())
        return refrescarDesdeServidor(androidId)
    }

    suspend fun refrescarDesdeServidor(androidId: String): Result<List<MermaPendiente>> {
        val localId = localIdActivo()
        return try {
            val mermas = SupabaseClientProvider.client.postgrest
                .rpc("get_mermas_pendientes", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<MermaPendiente>()
            db.mermaDao().limpiarPendientesDeLocal(localId)
            db.mermaDao().insertarTodas(mermas.map { it.toEntity(localId) })
            Result.success(mermas)
        } catch (e: Exception) {
            val cacheadas = db.mermaDao().obtenerPendientes(localId)
            if (cacheadas.isNotEmpty()) Result.success(cacheadas.map { it.toModel() }) else Result.failure(e)
        }
    }

    suspend fun solicitar(androidId: String, productoId: Long, productoNombre: String, cantidad: Double, motivo: String): Result<Unit> {
        val localId = localIdActivo()
        val idTemporal = -(System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong())
        val actuales = db.mermaDao().obtenerPendientes(localId)
        val nueva = MermaPendiente(id = idTemporal, producto_id = productoId, producto_nombre = productoNombre, cantidad = cantidad, motivo = motivo, estado = "pendiente", local_id = localId)
        db.mermaDao().insertarTodas((actuales + nueva.toEntity(localId)).map { it.toModel() }.toEntity(localId))
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId)
            put("p_producto_id", productoId); put("p_cantidad", cantidad); put("p_motivo", motivo)
        }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "crear_merma", payloadJson = payload.toString(), idLocalTemporal = idTemporal))
        try { SyncReporter.reportar(androidId, localId, "crear_merma", payload) } catch (_: Exception) {}
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    suspend fun resolver(androidId: String, id: Long, estado: String, resueltoPor: Long): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) return Result.failure(IllegalStateException("Necesitas conexión para resolver una merma"))
        val localId = localIdActivo()
        return try {
            SupabaseClientProvider.client.postgrest.rpc("resolver_merma", buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id); put("p_estado", estado); put("p_resuelto_por", resueltoPor)
            })
            refrescarDesdeServidor(androidId)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val mermas = SupabaseClientProvider.client.postgrest
                .rpc("get_mermas_pendientes", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<MermaPendiente>()
            db.mermaDao().limpiarPendientesDeLocal(localId)
            db.mermaDao().insertarTodas(mermas.map { it.toEntity(localId) })
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
