package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.local.entities.TarjetaEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.Tarjeta
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

/**
 * RPC: get_tarjetas, crear_tarjeta, editar_tarjeta, activar_tarjeta, eliminar_tarjeta.
 * Offline-first: mismo patrón que Producto/Merma/Turno, filtrado por local_id.
 */
class TarjetaRepository(
    private val context: Context = AppContextHolder.context,
    //private val trazaRepository: TrazaRepository = TrazaRepository()
) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getTarjetas(androidId: String): Result<List<Tarjeta>> {
        val localId = localIdActivo()
        val cacheadas = db.tarjetaDao().obtenerTodas(localId)
        if (cacheadas.isNotEmpty()) {
            if (NetworkMonitor.hayInternet(context)) refrescarDesdeServidor(androidId)
            return Result.success(cacheadas.map { it.toModel() })
        }
        return refrescarDesdeServidor(androidId)
    }

    suspend fun getTarjetasActivas(androidId: String): Result<List<Tarjeta>> {
        return getTarjetas(androidId).map { lista -> lista.filter { it.activo } }
    }

    suspend fun refrescarDesdeServidor(androidId: String): Result<List<Tarjeta>> {
        val localId = localIdActivo()
        return try {
            val tarjetas = SupabaseClientProvider.client.postgrest
                .rpc("get_tarjetas", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Tarjeta>()
            db.tarjetaDao().limpiar()
            db.tarjetaDao().insertarTodas(tarjetas.map { it.toEntity(localId) })
            Result.success(tarjetas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun limpiarCache() { db.tarjetaDao().limpiar() }

    suspend fun crearTarjeta(androidId: String, banco: String, numero: String, titular: String): Result<Unit> {
        val localId = localIdActivo()
        val idTemporal = -System.currentTimeMillis()
        db.tarjetaDao().insertarUna(TarjetaEntity(idTemporal, banco, numero, titular, activo = true, localId = localId))

        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_banco", banco); put("p_numero", numero); put("p_titular", titular)
        }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "crear_tarjeta", payloadJson = payload.toString(), idLocalTemporal = idTemporal))
        //trazaRepository.registrar(androidId, "crear_tarjeta", "$banco $numero")
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    // Requiere internet porque editar una cuenta bancaria no es algo que quieras
    // "reproducir" a ciegas si el servidor tiene una versión más reciente.
    suspend fun editarTarjeta(androidId: String, id: Long, banco: String, numero: String, titular: String): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) return Result.failure(IllegalStateException("Necesitas conexión para editar una tarjeta"))
        val localId = localIdActivo()
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id); put("p_banco", banco); put("p_numero", numero); put("p_titular", titular)
            }
            SupabaseClientProvider.client.postgrest.rpc("editar_tarjeta", params)
            db.tarjetaDao().insertarUna(TarjetaEntity(id, banco, numero, titular, activo = true, localId = localId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setActivo(androidId: String, id: Long, activo: Boolean): Result<Unit> {
        val localId = localIdActivo()
        // Esto sí se puede aplicar optimista y encolar: es un simple on/off.
        db.tarjetaDao().setActivo(id, activo, localId)
        val payload = buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id); put("p_activo", activo) }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "activar_tarjeta", payloadJson = payload.toString()))
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    suspend fun eliminarTarjeta(androidId: String, id: Long): Result<Unit> {
        val localId = localIdActivo()
        db.tarjetaDao().eliminar(id, localId)
        val payload = buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id) }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "eliminar_tarjeta", payloadJson = payload.toString()))
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }
}
