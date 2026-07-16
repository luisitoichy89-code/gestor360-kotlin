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

class TarjetaRepository(
    private val context: Context = AppContextHolder.context
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
        if (!NetworkMonitor.hayInternet(context)) return Result.success(emptyList())
        return refrescarDesdeServidor(androidId)
    }

    suspend fun getTarjetasActivas(androidId: String): Result<List<Tarjeta>> {
        return getTarjetas(androidId).map { it.filter { t -> t.activo } }
    }

    suspend fun refrescarDesdeServidor(androidId: String): Result<List<Tarjeta>> {
        val localId = localIdActivo()
        return try {
            val tarjetas = SupabaseClientProvider.client.postgrest
                .rpc("get_tarjetas", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Tarjeta>()
            db.tarjetaDao().insertarTodas(tarjetas.map { it.toEntity(localId) })
            Result.success(tarjetas)
        } catch (e: Exception) {
            val cacheadas = db.tarjetaDao().obtenerTodas(localId)
            if (cacheadas.isNotEmpty()) Result.success(cacheadas.map { it.toModel() }) else Result.failure(e)
        }
    }

    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val tarjetas = SupabaseClientProvider.client.postgrest
                .rpc("get_tarjetas", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Tarjeta>()
            db.tarjetaDao().insertarTodas(tarjetas.map { it.toEntity(localId) })
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun crearTarjeta(androidId: String, banco: String, numero: String, titular: String): Result<Unit> {
        val localId = localIdActivo()
        val idTemporal = -(System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong())
        db.tarjetaDao().insertarUna(TarjetaEntity(idTemporal, banco, numero, titular, activo = true, localId = localId))
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_banco", banco); put("p_numero", numero); put("p_titular", titular)
        }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "crear_tarjeta", payloadJson = payload.toString(), idLocalTemporal = idTemporal))
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    suspend fun editarTarjeta(androidId: String, id: Long, banco: String, numero: String, titular: String): Result<Unit> {
        val localId = localIdActivo()
        val activoActual = db.tarjetaDao().obtenerTodas(localId).find { it.id == id }?.activo ?: true
        db.tarjetaDao().insertarUna(TarjetaEntity(id, banco, numero, titular, activo = activoActual, localId = localId))
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id); put("p_banco", banco); put("p_numero", numero); put("p_titular", titular)
        }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "editar_tarjeta", payloadJson = payload.toString()))
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    suspend fun setActivo(androidId: String, id: Long, activo: Boolean): Result<Unit> {
        val localId = localIdActivo()
        db.tarjetaDao().setActivo(id, activo, localId)
        val payload = buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id); put("p_activo", activo) }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "activar_tarjeta", payloadJson = payload.toString()))
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    suspend fun eliminarTarjeta(androidId: String, id: Long): Result<Unit> {
        val localId = localIdActivo()
        
        // Verificar si ya hay una acción eliminar_tarjeta pendiente para este id
        val yaPendiente = db.accionPendienteDao().obtenerPendientes()
            .filter { it.tipo == "eliminar_tarjeta" }
            .any { it.payloadJson.contains("\"p_id\":$id") || it.payloadJson.contains("\"p_id\": $id") }
        if (yaPendiente) return Result.success(Unit)
        
        if (id < 0) {
            db.tarjetaDao().eliminar(id, localId)
            db.accionPendienteDao().cancelarPorIdTemporal(id)
            return Result.success(Unit)
        }
        db.tarjetaDao().eliminar(id, localId)
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "eliminar_tarjeta", payloadJson = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id)
        }.toString()))
        return Result.success(Unit)
    }
}
