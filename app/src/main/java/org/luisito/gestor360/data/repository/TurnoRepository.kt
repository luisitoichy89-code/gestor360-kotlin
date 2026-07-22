package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.TurnoEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.Turno
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

/** RPC: abrir_turno, obtener_turno_activo, cerrar_turno, get_turnos. Offline-first, filtrado por local_id. */
class TurnoRepository(
    private val context: Context = AppContextHolder.context,
) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    /** Siempre lee primero del caché local (así "¿tengo turno abierto?" nunca depende de la red). */
    suspend fun obtenerTurnoActivo(androidId: String): Result<Turno?> {
        val localId = localIdActivo()
        val activoLocal = db.turnoDao().obtenerActivo(localId)
        if (activoLocal != null) {
            if (NetworkMonitor.hayInternet(context)) refrescarDesdeServidor(androidId)
            return Result.success(activoLocal.toModel())
        }
        if (!NetworkMonitor.hayInternet(context)) return Result.success(null)
        return try {
            refrescarDesdeServidor(androidId).map { it }
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    /** Trae el turno activo real del servidor (ya filtrado por local_id) y actualiza el caché. */
    suspend fun refrescarDesdeServidor(androidId: String): Result<Turno?> {
        val localId = localIdActivo()
        return try {
            val turno = SupabaseClientProvider.client.postgrest
                .rpc("obtener_turno_activo", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Turno>()
                .firstOrNull()
            if (turno != null) {
                db.turnoDao().insertar(
                    TurnoEntity(turno.id, turno.usuario_id, turno.apertura, turno.cierre, turno.diferencia, turno.created_at, localId)
                )
            }
            Result.success(turno)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cerrar turno necesita el total de efectivo vendido para calcular la
     * diferencia, y eso requiere las ventas ya sincronizadas del servidor —
     * por eso, a diferencia de abrir_turno, esto SÍ requiere conexión.
     */
    suspend fun cerrarTurno(androidId: String, turnoId: Long, cierre: Double): Result<Long> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para cerrar el turno (hay que confirmar el total vendido con el servidor)"))
        }
        val localId = localIdActivo()
        return try {
            val params = buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId); put("p_turno_id", turnoId); put("p_cierre", cierre) }
            val nuevoTurnoId = SupabaseClientProvider.client.postgrest.rpc("cerrar_turno", params).decodeAs<Long>()
            db.turnoDao().cerrar(turnoId, cierre, 0.0, localId)
            refrescarDesdeServidor(androidId)
            Result.success(nuevoTurnoId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTurnos(androidId: String): Result<List<Turno>> {
        val localId = localIdActivo()
        return try {
            val turnos = SupabaseClientProvider.client.postgrest
                .rpc("get_turnos", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Turno>()
            Result.success(turnos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Precarga el turno activo de UN local específico, sin depender del local activo en sesión. */
    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val turno = SupabaseClientProvider.client.postgrest
                .rpc("obtener_turno_activo", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Turno>()
                .firstOrNull()
            if (turno != null) {
                db.turnoDao().insertar(
                    TurnoEntity(turno.id, turno.usuario_id, turno.apertura, turno.cierre, turno.diferencia, turno.created_at, localId)
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
