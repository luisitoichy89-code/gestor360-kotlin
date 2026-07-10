package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.InventarioDia
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import java.time.LocalDate

/**
 * RPC: get_inventario_dia (trae todo lo del día en una sola llamada) y
 * cerrar_turno (única acción manual que queda; abrir turno es automático
 * del lado del servidor, ver fn_asegurar_turno_abierto en el SQL).
 */
class InventarioRepository(private val context: Context = AppContextHolder.context) {
    private val session = SessionManager(context)
    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getInventarioDia(androidId: String, fecha: LocalDate): Result<InventarioDia> {
        return try {
            val resultado = SupabaseClientProvider.client.postgrest
                .rpc("get_inventario_dia", buildJsonObject {
                    put("p_android_id", androidId); put("p_local_id", localIdActivo()); put("p_fecha", fecha.toString())
                })
                .decodeAs<InventarioDia>()
            Result.success(resultado)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cerrarTurno(androidId: String, turnoId: Long, cierre: Double): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc("cerrar_turno", buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localIdActivo()); put("p_turno_id", turnoId); put("p_cierre", cierre)
            })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
