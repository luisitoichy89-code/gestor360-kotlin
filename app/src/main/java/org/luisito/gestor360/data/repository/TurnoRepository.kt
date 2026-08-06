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

class TurnoRepository(
    private val context: Context = AppContextHolder.context,
) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

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

    suspend fun refrescarDesdeServidor(androidId: String): Result<Turno?> {
        val localId = localIdActivo()
        return try {
            val turno = SupabaseClientProvider.client.postgrest
                .rpc("obtener_turno_abierto", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Turno>()
                .firstOrNull()
            if (turno != null) {
                db.turnoDao().insertar(
                    TurnoEntity(turno.id, turno.usuario_id, turno.apertura, turno.cierre, turno.diferencia, turno.created_at, localId, turno.numero_turno)
                )
            }
            db.turnoDao().limpiarDuplicadosAbiertos(localId, turno?.id)
            Result.success(turno)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cerrarTurno(androidId: String, turnoId: Long, cierre: Double): Result<Long> {
        val localId = localIdActivo()

        db.turnoDao().cerrar(turnoId, cierre, 0.0, localId)

        if (NetworkMonitor.hayInternet(context)) {
            try {
                val params = buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId); put("p_turno_id", turnoId); put("p_cierre", cierre) }
                val respuesta = SupabaseClientProvider.client.postgrest.rpc("cerrar_turno", params).decodeAs<TurnoCierreResponse>()
                val nuevoTurno = TurnoEntity(
                    id = respuesta.turno_id,
                    usuarioId = null,
                    apertura = 0.0,
                    cierre = null,
                    diferencia = null,
                    createdAt = java.time.LocalDateTime.now().toString(),
                    localId = localId,
                    numeroTurno = respuesta.numero_turno
                )
                db.turnoDao().insertar(nuevoTurno)
                db.turnoDao().limpiarDuplicadosAbiertos(localId, respuesta.turno_id)
                refrescarDesdeServidor(androidId)
                return Result.success(respuesta.turno_id)
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }

        return Result.failure(IllegalStateException("Necesitas conexión para cerrar el turno"))
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

    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val turno = SupabaseClientProvider.client.postgrest
                .rpc("obtener_turno_abierto", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Turno>()
                .firstOrNull()
            if (turno != null) {
                db.turnoDao().insertar(
                    TurnoEntity(turno.id, turno.usuario_id, turno.apertura, turno.cierre, turno.diferencia, turno.created_at, localId, turno.numero_turno)
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
