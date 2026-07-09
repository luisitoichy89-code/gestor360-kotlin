package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.local.entities.TurnoEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.Turno
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

class TurnoRepository(
    private val context: Context = AppContextHolder.context
) {
    private val db = AppDatabase.obtener(context)

    suspend fun obtenerTurnoActivo(androidId: String): Result<Turno?> {
        val activoLocal = db.turnoDao().obtenerActivo()
        if (activoLocal != null) {
            if (NetworkMonitor.hayInternet(context)) refrescarDesdeServidor(androidId)
            return Result.success(activoLocal.toModel())
        }
        if (!NetworkMonitor.hayInternet(context)) return Result.success(null)
        return refrescarDesdeServidor(androidId).map { it }
    }

    suspend fun refrescarDesdeServidor(androidId: String): Result<Turno?> {
        return try {
            val turno = SupabaseClientProvider.client.postgrest
                .rpc("obtener_turno_activo", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Turno>()
                .firstOrNull()
            if (turno != null) {
                db.turnoDao().insertar(
                    TurnoEntity(turno.id, turno.usuario_id, turno.apertura, turno.cierre, turno.diferencia, turno.created_at)
                )
            }
            Result.success(turno)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun abrirTurno(androidId: String, apertura: Double): Result<Long> {
        db.turnoDao().obtenerActivo()?.let { return Result.success(it.id) }
        val localId = SessionManager(context).getLocalId()
        val idTemporal = -System.currentTimeMillis()
        db.turnoDao().insertar(TurnoEntity(idTemporal, null, apertura, null, null, java.time.LocalDateTime.now().toString()))

        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_apertura", apertura)
            put("p_local_id", localId)
        }
        db.accionPendienteDao().encolar(
            AccionPendienteEntity(tipo = "abrir_turno", payloadJson = payload.toString(), idLocalTemporal = idTemporal)
        )
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(idTemporal)
    }

    suspend fun cerrarTurno(androidId: String, turnoId: Long, cierre: Double): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para cerrar el turno"))
        }
        return try {
            val params = buildJsonObject { put("p_android_id", androidId); put("p_turno_id", turnoId); put("p_cierre", cierre) }
            SupabaseClientProvider.client.postgrest.rpc("cerrar_turno", params)
            db.turnoDao().cerrar(turnoId, cierre, 0.0)
            refrescarDesdeServidor(androidId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTurnos(androidId: String): Result<List<Turno>> {
        return try {
            val turnos = SupabaseClientProvider.client.postgrest
                .rpc("get_turnos", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Turno>()
            Result.success(turnos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
