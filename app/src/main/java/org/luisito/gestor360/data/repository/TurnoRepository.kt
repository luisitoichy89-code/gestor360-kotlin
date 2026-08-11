package org.luisito.gestor360.data.repository

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.TurnoEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.Turno
import org.luisito.gestor360.data.sync.NetworkMonitor
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
        Log.d("TurnoRepo", "obtenerTurnoActivo: localId=$localId, activoLocal=${activoLocal?.id}")
        if (activoLocal != null) {
            if (NetworkMonitor.hayInternet(context)) refrescarDesdeServidor(androidId)
            return Result.success(activoLocal.toModel())
        }
        if (!NetworkMonitor.hayInternet(context)) {
            Log.d("TurnoRepo", "obtenerTurnoActivo: sin internet y sin cache local")
            return Result.success(null)
        }
        return try {
            refrescarDesdeServidor(androidId).map { it }
        } catch (e: Exception) {
            Log.e("TurnoRepo", "obtenerTurnoActivo: excepción", e)
            Result.success(null)
        }
    }

    suspend fun refrescarDesdeServidor(androidId: String): Result<Turno?> {
        val localId = localIdActivo()
        Log.d("TurnoRepo", "refrescarDesdeServidor: obtener_turno_abierto localId=$localId")
        return try {
            val turnoId = SupabaseClientProvider.client.postgrest
                .rpc("obtener_turno_abierto", buildJsonObject { put("p_local_id", localId) })
                .decodeAs<Long>()
            Log.d("TurnoRepo", "refrescarDesdeServidor: RPC devolvió turnoId=$turnoId")
            if (turnoId > 0) {
                val turnos = SupabaseClientProvider.client.postgrest
                    .rpc("get_turnos", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                    .decodeList<Turno>()
                val turno = turnos.firstOrNull { it.id == turnoId }
                if (turno != null) {
                    db.turnoDao().insertar(
                        TurnoEntity(turno.id, turno.usuario_id, turno.apertura, turno.cierre, turno.diferencia, turno.created_at, localId, turno.numero_turno)
                    )
                    db.turnoDao().limpiarDuplicadosAbiertos(localId, turno.id)
                }
                Result.success(turno)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("TurnoRepo", "refrescarDesdeServidor: error en RPC obtener_turno_abierto", e)
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

    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val turnoId = SupabaseClientProvider.client.postgrest
                .rpc("obtener_turno_abierto", buildJsonObject { put("p_local_id", localId) })
                .decodeAs<Long>()
            if (turnoId > 0) {
                val turnos = SupabaseClientProvider.client.postgrest
                    .rpc("get_turnos", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                    .decodeList<Turno>()
                val turno = turnos.firstOrNull { it.id == turnoId }
                if (turno != null) {
                    db.turnoDao().insertar(
                        TurnoEntity(turno.id, turno.usuario_id, turno.apertura, turno.cierre, turno.diferencia, turno.created_at, localId, turno.numero_turno)
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
