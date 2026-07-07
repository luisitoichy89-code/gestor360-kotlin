package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.MermaPendiente
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder

/** RPC: get_mermas_pendientes, crear_merma, resolver_merma. Offline-first. */
class MermaRepository(
    private val context: Context = AppContextHolder.context,
    private val trazaRepository: TrazaRepository = TrazaRepository()
) {
    private val db = AppDatabase.obtener(context)

    suspend fun getPendientes(androidId: String): Result<List<MermaPendiente>> {
        val cacheadas = db.mermaDao().obtenerPendientes()
        if (cacheadas.isNotEmpty()) {
            if (NetworkMonitor.hayInternet(context)) refrescarDesdeServidor(androidId)
            return Result.success(cacheadas.map { it.toModel() })
        }
        return refrescarDesdeServidor(androidId)
    }

    suspend fun refrescarDesdeServidor(androidId: String): Result<List<MermaPendiente>> {
        return try {
            val mermas = SupabaseClientProvider.client.postgrest
                .rpc("get_mermas_pendientes", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<MermaPendiente>()
            db.mermaDao().insertarTodas(mermas.map { it.toEntity() })
            Result.success(mermas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** El vendedor propone offline: queda visible como pendiente de inmediato, sin descontar stock. */
    suspend fun solicitar(androidId: String, productoId: Long, productoNombre: String, cantidad: Double, motivo: String): Result<Unit> {
        val idTemporal = -System.currentTimeMillis()
        db.mermaDao().insertarUna(
            org.luisito.gestor360.data.local.entities.MermaEntity(
                id = idTemporal, productoId = productoId, productoNombre = productoNombre,
                cantidad = cantidad, motivo = motivo, solicitadoPor = null, solicitadoPorNombre = null, estado = "pendiente"
            )
        )
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_producto_id", productoId)
            put("p_cantidad", cantidad); put("p_motivo", motivo)
        }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "crear_merma", payloadJson = payload.toString(), idLocalTemporal = idTemporal))
        trazaRepository.registrar(androidId, "proponer_merma", "producto_id=$productoId cantidad=$cantidad")
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    /**
     * Aprobar/rechazar descuenta stock real del lado del servidor — necesita
     * conexión sí o sí, para no arriesgarse a aprobar dos veces la misma merma
     * desde dos dispositivos offline.
     */
    suspend fun resolver(androidId: String, mermaId: Long, estado: String): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para aprobar o rechazar una merma"))
        }
        return try {
            val params = buildJsonObject { put("p_android_id", androidId); put("p_merma_id", mermaId); put("p_estado", estado) }
            SupabaseClientProvider.client.postgrest.rpc("resolver_merma", params)
            db.mermaDao().actualizarEstado(mermaId, estado)
            trazaRepository.registrar(androidId, "resolver_merma", "merma_id=$mermaId estado=$estado")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun aprobar(androidId: String, mermaId: Long): Result<Unit> = resolver(androidId, mermaId, "aprobada")
    suspend fun rechazar(androidId: String, mermaId: Long): Result<Unit> = resolver(androidId, mermaId, "rechazada")
}
