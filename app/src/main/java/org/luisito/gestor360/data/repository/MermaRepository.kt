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
import org.luisito.gestor360.data.local.entities.MermaEntity
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import java.util.UUID

class MermaRepository(
    private val context: Context = AppContextHolder.context
) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    // ---------- Lecturas ----------
    suspend fun getPendientes(androidId: String): Result<List<MermaEntity>> {
        val localId = localIdActivo()
        val cacheadas = db.mermaDao().obtenerPendientes(localId)
        if (cacheadas.isNotEmpty()) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch { refrescarDesdeServidor(localId) }
            }
            return Result.success(cacheadas)
        }
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.success(emptyList())
        }
        return try {
            refrescarDesdeServidor(localId).map { lista -> lista.filter { it.estado == "pendiente" } }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    suspend fun getTodas(androidId: String): Result<List<MermaEntity>> {
        val localId = localIdActivo()
        val cacheadas = db.mermaDao().obtenerTodas(localId)
        if (cacheadas.isNotEmpty()) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch { refrescarDesdeServidor(localId) }
            }
            return Result.success(cacheadas)
        }
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.success(emptyList())
        }
        return try {
            refrescarDesdeServidor(localId)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    suspend fun refrescarDesdeServidor(localId: Long): Result<List<MermaEntity>> {
        return try {
            val mermas = fetchRemoto(localId)
            db.mermaDao().reemplazarSincronizadas(localId, mermas.map { it.copy(pendienteSync = false) })
            Result.success(mermas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun precargarLocal(localId: Long): Result<Unit> {
        return try {
            val mermas = fetchRemoto(localId)
            db.mermaDao().insertarTodas(mermas.map { it.copy(pendienteSync = false) })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchRemoto(localId: Long): List<MermaEntity> =
        SupabaseClientProvider.client.postgrest.from("mermas")
            .select {
                filter { eq("local_id", localId) }
            }
            .decodeList<MermaEntity>()

    // ---------- Escrituras ----------
    suspend fun crear(
        androidId: String, productoId: String, productoNombre: String, cantidad: Int, motivo: String
    ): Result<Unit> {
        val localId = localIdActivo()
        val id = UUID.randomUUID().toString()
        val accionId = UUID.randomUUID().toString()
        val merma = MermaEntity(
            id = id, localId = localId, productoId = productoId, productoNombre = productoNombre,
            cantidad = cantidad.toDouble(), motivo = motivo, solicitadoPor = null, solicitadoPorNombre = null,
            estado = "pendiente", pendienteSync = true
        )
        db.mermaDao().insertarUna(merma)
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id)
            put("p_producto_id", productoId); put("p_cantidad", cantidad); put("p_motivo", motivo)
            put("p_accion_id", accionId)
        }
        encolarYSincronizar("crear_merma", payload)
        return Result.success(Unit)
    }

    suspend fun aprobar(androidId: String, id: String): Result<Unit> {
        return resolver(androidId, id, "aprobada")
    }

    suspend fun rechazar(androidId: String, id: String): Result<Unit> {
        return resolver(androidId, id, "rechazada")
    }

    private suspend fun resolver(androidId: String, id: String, estado: String): Result<Unit> {
        val localId = localIdActivo()
        val mermaExistente = db.mermaDao().obtenerPorId(id, localId)
        if (mermaExistente == null) {
            return Result.failure(IllegalStateException("La solicitud de merma ya no está disponible"))
        }
        if (mermaExistente.estado != "pendiente") {
            return Result.failure(IllegalStateException("Esta solicitud ya fue ${mermaExistente.estado}"))
        }

        // Actualiza SOLO esta merma a su nuevo estado localmente
        db.mermaDao().insertarUna(mermaExistente.copy(estado = estado, pendienteSync = true))

        // Descuenta stock local SOLO si es aprobada
        if (estado == "aprobada") {
            db.productoDao().descontarStock(mermaExistente.productoId, mermaExistente.cantidad, localId)
        }

        val accionId = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("p_android_id", androidId)
            put("p_local_id", localId)
            put("p_id", id)
            put("p_estado", estado)
            put("p_accion_id", accionId)
        }
        encolarYSincronizar("resolver_merma", payload)
        return Result.success(Unit)
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
