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

/**
 * RPC: get_tarjetas, crear_tarjeta (editar_tarjeta/eliminar_tarjeta/activar_tarjeta
 * son nombres supuestos, iguales a la nota que ya tenías — sigue pendiente
 * confirmarlos o crearlos). Offline-first: mismo patrón que Producto/Merma/Turno.
 */
class TarjetaRepository(
    private val context: Context = AppContextHolder.context,
    private val trazaRepository: TrazaRepository = TrazaRepository()
) {
    private val db = AppDatabase.obtener(context)

    suspend fun getTarjetas(androidId: String): Result<List<Tarjeta>> {
        val cacheadas = db.tarjetaDao().obtenerTodas()
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
        return try {
            val tarjetas = SupabaseClientProvider.client.postgrest
                .rpc("get_tarjetas", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Tarjeta>()
            db.tarjetaDao().limpiar()
            db.tarjetaDao().insertarTodas(tarjetas.map { it.toEntity() })
            Result.success(tarjetas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun crearTarjeta(androidId: String, banco: String, numero: String, titular: String): Result<Unit> {
        val idTemporal = -System.currentTimeMillis()
        db.tarjetaDao().insertarUna(TarjetaEntity(idTemporal, banco, numero, titular, activo = true))

        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_banco", banco); put("p_numero", numero); put("p_titular", titular)
        }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "crear_tarjeta", payloadJson = payload.toString(), idLocalTemporal = idTemporal))
        trazaRepository.registrar(androidId, "crear_tarjeta", "$banco $numero")
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    // NOTA: mismo supuesto de nombre que ya tenías. Requiere internet porque
    // editar una cuenta bancaria no es algo que quieras "reproducir" a ciegas
    // si el servidor tiene una versión más reciente (poca frecuencia, bajo riesgo
    // de necesitarlo offline, y mucho riesgo si el offline pisa un cambio real).
    suspend fun editarTarjeta(androidId: String, id: Long, banco: String, numero: String, titular: String): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) return Result.failure(IllegalStateException("Necesitas conexión para editar una tarjeta"))
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId); put("p_id", id); put("p_banco", banco); put("p_numero", numero); put("p_titular", titular)
            }
            SupabaseClientProvider.client.postgrest.rpc("editar_tarjeta", params)
            db.tarjetaDao().insertarUna(TarjetaEntity(id, banco, numero, titular, activo = true))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setActivo(androidId: String, id: Long, activo: Boolean): Result<Unit> {
        // Esto sí se puede aplicar optimista y encolar: es un simple on/off.
        db.tarjetaDao().setActivo(id, activo)
        val payload = buildJsonObject { put("p_android_id", androidId); put("p_id", id); put("p_activo", activo) }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "activar_tarjeta", payloadJson = payload.toString()))
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    suspend fun eliminarTarjeta(androidId: String, id: Long): Result<Unit> {
        db.tarjetaDao().eliminar(id)
        val payload = buildJsonObject { put("p_android_id", androidId); put("p_id", id) }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "eliminar_tarjeta", payloadJson = payload.toString()))
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }
}
