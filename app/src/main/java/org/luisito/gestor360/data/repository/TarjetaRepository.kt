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
import org.luisito.gestor360.data.local.entities.TarjetaEntity
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import java.util.UUID

class TarjetaRepository(
    private val context: Context = AppContextHolder.context
) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    // ---------- Lecturas ----------
    // OJO: a diferencia de Productos, tarjetas_setup.sql no define un RPC de
    // lectura (get_tarjetas); solo comenta "grant select on tarjetas to
    // anon, authenticated". Por eso acá se lee la tabla directo por
    // postgrest, sin pasar por validar_admin_tarjeta (esa función solo la
    // llaman los RPC de escritura). Si más adelante se agrega RLS o un RPC
    // de lectura validado, este método es el que hay que tocar.

    suspend fun getTarjetas(androidId: String): Result<List<TarjetaEntity>> {
        val localId = localIdActivo()
        val cacheadas = db.tarjetaDao().obtenerTodos(localId)
        if (cacheadas.isNotEmpty()) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch { refrescarDesdeServidor(localId) }
            }
            return Result.success(cacheadas)
        }
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.success(emptyList())
        }
        return refrescarDesdeServidor(localId)
    }

    suspend fun refrescarDesdeServidor(localId: Long): Result<List<TarjetaEntity>> {
        return try {
            val tarjetas = fetchRemoto(localId)
            // Antes de reinsertar lo confirmado por el servidor, se limpia solo
            // lo que ya estaba confirmado (pendienteSync = 0): así una tarjeta
            // creada offline que todavía no sincronizó no desaparece de la
            // lista mientras se espera la confirmación.
            db.tarjetaDao().limpiarSincronizadasDeLocal(localId)
            db.tarjetaDao().insertarTodos(tarjetas.map { it.copy(pendienteSync = false) })
            Result.success(tarjetas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun precargarLocal(localId: Long): Result<Unit> {
        return try {
            val tarjetas = fetchRemoto(localId)
            db.tarjetaDao().insertarTodos(tarjetas.map { it.copy(pendienteSync = false) })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchRemoto(localId: Long): List<TarjetaEntity> =
        SupabaseClientProvider.client.postgrest.from("tarjetas")
            .select {
                filter { eq("local_id", localId) }
            }
            .decodeList<TarjetaEntity>()

    // ---------- Escrituras: solo admin (validado también server-side en el RPC) ----------

    suspend fun createTarjeta(
        androidId: String, nombre: String, tipo: String?, numeroCuenta: String?
    ): Result<Unit> {
        val localId = localIdActivo()
        // UUID generado en el dispositivo: es el id definitivo, el mismo antes
        // y después de sincronizar. Igual que Productos.
        val id = UUID.randomUUID().toString()
        val accionId = UUID.randomUUID().toString()
        val tarjeta = TarjetaEntity(
            id = id, localId = localId, nombre = nombre, tipo = tipo,
            numeroCuenta = numeroCuenta, activo = true, pendienteSync = true
        )
        db.tarjetaDao().insertarUno(tarjeta)

        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id)
            put("p_nombre", nombre); put("p_tipo", tipo); put("p_numero_cuenta", numeroCuenta)
            put("p_accion_id", accionId)
        }
        encolarYSincronizar("crear_tarjeta", payload)
        return Result.success(Unit)
    }

    suspend fun updateTarjeta(
        androidId: String, id: String, nombre: String, tipo: String?, numeroCuenta: String?, activo: Boolean
    ): Result<Unit> {
        val localId = localIdActivo()
        val accionId = UUID.randomUUID().toString()
        db.tarjetaDao().obtenerPorId(id, localId)?.let {
            db.tarjetaDao().insertarUno(
                it.copy(nombre = nombre, tipo = tipo, numeroCuenta = numeroCuenta, activo = activo, pendienteSync = true)
            )
        }
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id)
            put("p_nombre", nombre); put("p_tipo", tipo); put("p_numero_cuenta", numeroCuenta)
            put("p_activo", activo); put("p_accion_id", accionId)
        }
        encolarYSincronizar("actualizar_tarjeta", payload)
        return Result.success(Unit)
    }

    /** Toggle informativo (no es delete). Usa el mismo RPC actualizar_tarjeta con p_activo. */
    suspend fun cambiarActivo(androidId: String, tarjeta: TarjetaEntity, activo: Boolean): Result<Unit> =
        updateTarjeta(androidId, tarjeta.id, tarjeta.nombre, tarjeta.tipo, tarjeta.numeroCuenta, activo)

    /** DELETE real, igual que Productos. Ojo con FKs desde Ventas (ver comentario en el SQL). */
    suspend fun deleteTarjeta(androidId: String, id: String): Result<Unit> {
        val localId = localIdActivo()

        // Si la tarjeta se creó offline y esa creación todavía no sincronizó,
        // no tiene sentido avisarle al servidor de una eliminación: el servidor
        // nunca llegó a saber que esta tarjeta existía. Se cancela la
        // creación pendiente directamente.
        val creacionPendiente = db.accionPendienteDao().obtenerPendientes()
            .firstOrNull { it.tipo == "crear_tarjeta" && it.payloadJson.contains("\"p_id\":\"$id\"") }
        if (creacionPendiente != null) {
            db.accionPendienteDao().eliminar(creacionPendiente)
            db.tarjetaDao().eliminar(id, localId)
            return Result.success(Unit)
        }

        // Evitar encolar un eliminar_tarjeta duplicado si ya hay uno pendiente.
        val yaPendiente = db.accionPendienteDao().obtenerPendientes()
            .any { it.tipo == "eliminar_tarjeta" && it.payloadJson.contains("\"p_id\":\"$id\"") }
        if (yaPendiente) return Result.success(Unit)

        val accionId = UUID.randomUUID().toString()
        db.tarjetaDao().eliminar(id, localId)
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "eliminar_tarjeta", payloadJson = buildJsonObject {
            put("p_android_id", androidId)
            put("p_local_id", localId)
            put("p_id", id)
            put("p_accion_id", accionId)
        }.toString()))
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
