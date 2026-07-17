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
    // Igual que Tarjetas: solo hay 2 RPCs (crear_merma, resolver_merma), así
    // que la lectura es directo por postgrest sobre la tabla "mermas", sin
    // pasar por validar_admin_merma (esa función solo la llaman los RPC de
    // escritura; ver comentario en mermas_setup.sql).

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
            // Antes de reinsertar lo confirmado por el servidor, se limpia solo
            // lo que ya estaba confirmado (pendienteSync = 0): así una merma
            // creada offline que todavía no sincronizó no desaparece de la
            // lista mientras se espera la confirmación.
            db.mermaDao().limpiarSincronizadasDeLocal(localId)
            db.mermaDao().insertarTodas(mermas.map { it.copy(pendienteSync = false) })
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

    /**
     * Cualquier usuario activo del local puede solicitar una merma (no hace
     * falta ser admin para pedirla, solo para resolverla — ver
     * validar_usuario_local vs validar_admin_merma en el SQL).
     */
    suspend fun crear(
        androidId: String, productoId: String, productoNombre: String, cantidad: Double, motivo: String
    ): Result<Unit> {
        val localId = localIdActivo()
        // UUID generado en el dispositivo: es el id definitivo, el mismo antes
        // y después de sincronizar. Igual que Productos y Tarjetas.
        val id = UUID.randomUUID().toString()
        val accionId = UUID.randomUUID().toString()
        val merma = MermaEntity(
            id = id, localId = localId, productoId = productoId, productoNombre = productoNombre,
            cantidad = cantidad, motivo = motivo, solicitadoPor = null, solicitadoPorNombre = null,
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

    /**
     * Solo admin (validado también server-side en el RPC). estado debe ser
     * "aprobada" o "rechazada". Si se aprueba, el RPC descuenta el stock del
     * producto en el servidor; acá se descuenta también en el caché local
     * para que la UI no espere a la próxima sincronización.
     */
    suspend fun resolver(androidId: String, id: String, estado: String): Result<Unit> {
        val localId = localIdActivo()
        val accionId = UUID.randomUUID().toString()
        val merma = db.mermaDao().obtenerPorId(id, localId)
        merma?.let {
            db.mermaDao().insertarUna(it.copy(estado = estado, pendienteSync = true))
            if (estado == "aprobada") {
                db.productoDao().descontarStock(it.productoId, it.cantidad, localId)
            }
        }
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id)
            put("p_estado", estado); put("p_accion_id", accionId)
        }
        encolarYSincronizar("resolver_merma", payload)
        return Result.success(Unit)
    }

    suspend fun aprobar(androidId: String, id: String): Result<Unit> = resolver(androidId, id, "aprobada")
    suspend fun rechazar(androidId: String, id: String): Result<Unit> = resolver(androidId, id, "rechazada")

    private suspend fun encolarYSincronizar(tipo: String, payload: JsonObject) {
        db.accionPendienteDao().encolar(
            AccionPendienteEntity(tipo = tipo, payloadJson = payload.toString())
        )
        if (NetworkMonitor.hayInternet(context)) {
            SyncWorker.sincronizarAhora(context)
        }
    }
}
