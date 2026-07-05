package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.models.Turno
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder

class TurnoRepository(
    private val context: Context = AppContextHolder.context
) {
    private val db = AppDatabase.obtener(context)

    suspend fun getTurnoAbierto(androidId: String): Result<Turno?> {
        // Siempre busca en servidor primero
        return try {
            if (NetworkMonitor.hayInternet(context)) {
                val turno = SupabaseClientProvider.client.postgrest
                    .rpc("obtener_turno_activo", buildJsonObject { put("p_android_id", androidId) })
                    .decodeList<Turno>().firstOrNull()
                if (turno != null) {
                    db.turnoDao().guardar(turno)
                }
                Result.success(turno)
            } else {
                Result.success(db.turnoDao().obtenerActivo())
            }
        } catch (e: Exception) {
            Result.success(db.turnoDao().obtenerActivo())
        }
    }

    suspend fun abrirTurno(androidId: String, apertura: Double): Result<Long> {
        return try {
            if (NetworkMonitor.hayInternet(context)) {
                val id = SupabaseClientProvider.client.postgrest
                    .rpc("abrir_turno", buildJsonObject { put("p_android_id", androidId); put("p_apertura", apertura) })
                    .decodeAs<Long>()
                Result.success(id)
            } else {
                db.accionPendienteDao().encolar(AccionPendienteEntity(
                    tipo = "abrir_turno",
                    payloadJson = buildJsonObject { put("p_android_id", androidId); put("p_apertura", apertura) }.toString()
                ))
                Result.success(0L)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun cerrarTurno(androidId: String, turnoId: Long, cierre: Double): Result<Turno?> {
        return try {
            if (NetworkMonitor.hayInternet(context)) {
                val result = SupabaseClientProvider.client.postgrest
                    .rpc("cerrar_turno", buildJsonObject { put("p_android_id", androidId); put("p_turno_id", turnoId); put("p_cierre", cierre) })
                    .decodeList<Turno>().firstOrNull()
                if (result != null) db.turnoDao().guardar(result)
                Result.success(result)
            } else {
                db.accionPendienteDao().encolar(AccionPendienteEntity(
                    tipo = "cerrar_turno",
                    payloadJson = buildJsonObject { put("p_android_id", androidId); put("p_turno_id", turnoId); put("p_cierre", cierre) }.toString()
                ))
                db.turnoDao().marcarCerrado(turnoId, cierre)
                Result.success(null)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getHistorialTurnos(androidId: String): Result<List<Turno>> {
        return try {
            if (NetworkMonitor.hayInternet(context)) {
                val turnos = SupabaseClientProvider.client.postgrest
                    .rpc("get_turnos", buildJsonObject { put("p_android_id", androidId) })
                    .decodeList<Turno>()
                turnos.forEach { db.turnoDao().guardar(it) }
                Result.success(turnos)
            } else {
                Result.success(db.turnoDao().obtenerTodos())
            }
        } catch (e: Exception) {
            Result.success(db.turnoDao().obtenerTodos())
        }
    }
}
