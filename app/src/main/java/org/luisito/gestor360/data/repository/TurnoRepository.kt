package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Turno

/** RPC: abrir_turno, obtener_turno_activo, cerrar_turno, get_turnos. */
class TurnoRepository(
    private val trazaRepository: TrazaRepository = TrazaRepository()
) {

    suspend fun abrirTurno(androidId: String, apertura: Double): Result<Long> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_apertura", apertura)
            }
            val id = SupabaseClientProvider.client.postgrest.rpc("abrir_turno", params).decodeAs<Long>()
            trazaRepository.registrar(androidId, "abrir_turno", "Apertura: $apertura")
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun obtenerTurnoActivo(androidId: String): Result<Turno?> {
        return try {
            val turno = SupabaseClientProvider.client.postgrest
                .rpc("obtener_turno_activo", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Turno>()
                .firstOrNull()
            Result.success(turno)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cerrarTurno(androidId: String, turnoId: Long, cierre: Double): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_turno_id", turnoId)
                put("p_cierre", cierre)
            }
            SupabaseClientProvider.client.postgrest.rpc("cerrar_turno", params)
            trazaRepository.registrar(androidId, "cerrar_turno", "Cierre contado: $cierre")
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
