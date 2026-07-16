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
import org.luisito.gestor360.data.models.MermaPendiente
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.data.sync.SyncReporter
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

class MermaRepository(
    private val context: Context = AppContextHolder.context
) {
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
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.success(emptyList())
        }
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
        db.mermaDao().insertarUna(
            org.luisito.gestor360.data.local.entities.MermaEntity(
                id = idTemporal, productoId = productoId, productoNombre = productoNombre,
                cantidad = cantidad, motivo = motivo, solicitadoPor = null, solicitadoPorNombre = null,
                estado = "pendiente", localId = localId
            )
        )
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_producto_id", productoId)
            put("p_cantidad", cantidad); put("p_motivo", motivo)
        }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "crear_merma", payloadJson = payload.toString(), idLocalTemporal = idTemporal))
        SyncReporter.reportar(androidId, localIdActivo(), "crear_merma", payload)
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    suspend fun resolver(androidId: String, mermaId: Long, estado: String): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para aprobar o rechazar una merma"))
        }
        val localId = localIdActivo()
        return try {
            val params = buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId); put("p_merma_id", mermaId); put("p_estado", estado) }
            SupabaseClientProvider.client.postgrest.rpc("resolver_merma", params)
            db.mermaDao().actualizarEstado(mermaId, estado, localId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun aprobar(androidId: String, mermaId: Long): Result<Unit> = resolver(androidId, mermaId, "aprobada")
    suspend fun rechazar(androidId: String, mermaId: Long): Result<Unit> = resolver(androidId, mermaId, "rechazada")

    /** Precarga las mermas pendientes de UN local específico, sin depender del local activo en sesión. */
    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val mermas = SupabaseClientProvider.client.postgrest
                .rpc("get_mermas_pendientes", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<MermaPendiente>()
            db.mermaDao().limpiarPendientesDeLocal(localId)
            db.mermaDao().insertarTodas(mermas.map { it.toEntity(localId) })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
