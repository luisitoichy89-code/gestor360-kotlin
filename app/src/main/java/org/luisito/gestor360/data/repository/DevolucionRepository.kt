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
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.Devolucion
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

/**
 * RPC: get_devoluciones, solicitar_devolucion, resolver_devolucion. Filtrado por local_id.
 *
 * Offline-first:
 * - Lectura (getPendientes): se cachea la lista completa como JSON por local
 *   (ver DevolucionCacheEntity), lee caché primero y refresca en background.
 * - solicitar: el vendedor la pide offline igual que MermaRepository.solicitar
 *   — queda visible al toque como "pendiente" en el caché local, y encolada
 *   en acciones_pendientes para sincronizar cuando vuelva la conexión.
 * - resolver: SIGUE requiriendo conexión, porque mueve stock real del lado
 *   del servidor y no puede arriesgarse a resolverse dos veces desde dos
 *   dispositivos offline (mismo motivo que MermaRepository.resolver).
 */
class DevolucionRepository(private val context: Context = AppContextHolder.context) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)
    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getPendientes(androidId: String): Result<List<Devolucion>> {
        val localId = localIdActivo()
        val cacheadas = db.devolucionCacheDao().obtener(localId)
        if (cacheadas != null) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch { refrescarDesdeServidor(androidId) }
            }
            return Result.success(cacheadas.toModel())
        }
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Sin conexión y sin datos guardados todavía"))
        }
        return refrescarDesdeServidor(androidId)
    }

    /** Trae la verdad del servidor (ya filtrada por local_id) y reemplaza el caché de ese local. */
    suspend fun refrescarDesdeServidor(androidId: String): Result<List<Devolucion>> {
        val localId = localIdActivo()
        return try {
            val lista = SupabaseClientProvider.client.postgrest
                .rpc("get_devoluciones", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Devolucion>()
            db.devolucionCacheDao().guardar(lista.toEntity(localId))
            Result.success(lista)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** El vendedor propone offline: queda visible como pendiente de inmediato, sin mover stock todavía. */
    suspend fun solicitar(androidId: String, productoId: Long, productoNombre: String, cantidad: Double, metodo: String, motivo: String): Result<Unit> {
        val localId = localIdActivo()
        val idTemporal = -(System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong())
        val actuales = db.devolucionCacheDao().obtener(localId)?.toModel() ?: emptyList()
        val nueva = Devolucion(
            id = idTemporal, producto_id = productoId, producto_nombre = productoNombre,
            cantidad = cantidad, metodo = metodo, motivo = motivo, estado = "pendiente", local_id = localId
        )
        db.devolucionCacheDao().guardar((listOf(nueva) + actuales).toEntity(localId))

        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId)
            put("p_producto_id", productoId); put("p_cantidad", cantidad); put("p_metodo", metodo); put("p_motivo", motivo)
        }
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "solicitar_devolucion", payloadJson = payload.toString(), idLocalTemporal = idTemporal))
        if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)
        return Result.success(Unit)
    }

    /**
     * Aprobar/rechazar mueve stock real del lado del servidor — necesita
     * conexión sí o sí, para no arriesgarse a resolver dos veces la misma
     * devolución desde dos dispositivos offline.
     * destino: "stock" (vuelve a venderse) o "merma" (no sirve, se descarta). Ignorado si se rechaza.
     */
    suspend fun resolver(androidId: String, id: Long, estado: String, destino: String? = null): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para resolver una devolución"))
        }
        val localId = localIdActivo()
        return try {
            SupabaseClientProvider.client.postgrest.rpc("resolver_devolucion", buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localId)
                put("p_id", id); put("p_estado", estado); put("p_destino", destino)
            })
            refrescarDesdeServidor(androidId)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
