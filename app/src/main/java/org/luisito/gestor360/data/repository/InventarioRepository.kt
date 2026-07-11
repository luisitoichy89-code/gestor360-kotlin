package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.InventarioDia
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import java.time.LocalDate

/**
 * RPC: get_inventario_dia (trae todo lo del día en una sola llamada) y
 * cerrar_turno (única acción manual que queda; abrir turno es automático
 * del lado del servidor, ver fn_asegurar_turno_abierto en el SQL).
 *
 * Offline-first: get_inventario_dia se cachea completo como JSON por
 * local+fecha (ver InventarioCacheEntity, Opción B del audit). cerrarTurno
 * sigue requiriendo conexión siempre: depende de los totales reales del
 * servidor y es una operación transaccional que no puede arriesgarse a
 * duplicarse si se hace offline.
 */
class InventarioRepository(private val context: Context = AppContextHolder.context) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)
    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getInventarioDia(androidId: String, fecha: LocalDate): Result<InventarioDia> {
        val localId = localIdActivo()
        val cacheado = db.inventarioCacheDao().obtener(localId, fecha.toString())
        if (cacheado != null) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch { refrescarDesdeServidor(androidId, fecha) }
            }
            return Result.success(cacheado.toModel())
        }
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Sin conexión y sin datos guardados para este día todavía"))
        }
        return refrescarDesdeServidor(androidId, fecha)
    }

    /** Trae la verdad del servidor (ya filtrada por local_id) y actualiza el caché de ese día. */
    suspend fun refrescarDesdeServidor(androidId: String, fecha: LocalDate): Result<InventarioDia> {
        val localId = localIdActivo()
        return try {
            val resultado = SupabaseClientProvider.client.postgrest
                .rpc("get_inventario_dia", buildJsonObject {
                    put("p_android_id", androidId); put("p_local_id", localId); put("p_fecha", fecha.toString())
                })
                .decodeAs<InventarioDia>()
            db.inventarioCacheDao().guardar(resultado.toEntity(localId, fecha.toString()))
            Result.success(resultado)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cerrarTurno(androidId: String, turnoId: Long, cierre: Double): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para cerrar el turno (hay que confirmar el total vendido con el servidor)"))
        }
        return try {
            SupabaseClientProvider.client.postgrest.rpc("cerrar_turno", buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localIdActivo()); put("p_turno_id", turnoId); put("p_cierre", cierre)
            })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Precarga el inventario del día de HOY para un local específico (no necesariamente el activo). */
    suspend fun precargarLocal(androidId: String, localId: Long, fecha: LocalDate = LocalDate.now()): Result<Unit> {
        return try {
            val resultado = SupabaseClientProvider.client.postgrest
                .rpc("get_inventario_dia", buildJsonObject {
                    put("p_android_id", androidId); put("p_local_id", localId); put("p_fecha", fecha.toString())
                })
                .decodeAs<InventarioDia>()
            db.inventarioCacheDao().guardar(resultado.toEntity(localId, fecha.toString()))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
