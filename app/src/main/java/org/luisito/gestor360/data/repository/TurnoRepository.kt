package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Turno

class TurnoRepository {

    suspend fun getTurnoAbierto(androidId: String): Result<Turno?> {
        return try {
            val turnos = SupabaseClientProvider.client.postgrest
                .rpc("get_turno_abierto", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Turno>()
            Result.success(turnos.firstOrNull())
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun abrirTurno(androidId: String, efectivoInicial: Double, usuarioId: Long, almacenId: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "abrir_turno", buildJsonObject {
                    put("p_android_id", androidId)
                    put("p_efectivo_inicial", efectivoInicial)
                    put("p_usuario_id", usuarioId)
                    put("p_almacen_id", almacenId)
                }
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun cerrarTurno(androidId: String, turnoId: Long): Result<Turno> {
        return try {
            val result = SupabaseClientProvider.client.postgrest.rpc(
                "cerrar_turno", buildJsonObject { put("p_android_id", androidId); put("p_turno_id", turnoId) }
            ).decodeSingle<Turno>()
            Result.success(result)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getHistorialTurnos(androidId: String): Result<List<Turno>> {
        return try {
            val turnos = SupabaseClientProvider.client.postgrest
                .rpc("get_turnos", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Turno>()
            Result.success(turnos)
        } catch (e: Exception) { Result.failure(e) }
    }
}
