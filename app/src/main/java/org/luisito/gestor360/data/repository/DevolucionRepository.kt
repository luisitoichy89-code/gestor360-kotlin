package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
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
import java.util.UUID

/**
 * RPC: get_devoluciones, solicitar_devolucion, resolver_devolucion. Filtrado por local_id.
 *
 * Offline-first, calcado a Producto/Tarjeta/Merma:
 * - Lectura (getPendientes): se cachea la lista completa como JSON por local
 *   (ver DevolucionCacheEntity), lee caché primero y refresca en background.
 * - solicitar: UUID generado en el dispositivo como PK definitivo (igual que
 *   crear_producto/crear_tarjeta/crear_merma) + p_accion_id para idempotencia
 *   contra acciones_procesadas. Ya no depende de id temporal negativo ni de
 *   SyncReporter: el id es el mismo antes y después de sincronizar.
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
            return Result.success(emptyList())
        }
        return try {
            refrescarDesdeServidor(androidId)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
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

    /**
     * El vendedor propone offline: queda visible como pendiente de inmediato,
     * sin mover stock todavía. UUID generado en el dispositivo (p_id), igual
     * que Productos/Tarjetas/Mermas.
     */
    suspend fun solicitar(androidId: String, productoId: String, productoNombre: String, cantidad: Double, metodo: String, motivo: String): Result<Unit> {
        // Verificar si ya hay una acción solicitar_devolucion pendiente para este producto
        val yaPendiente = db.accionPendienteDao().obtenerPendientes()
            .filter { it.tipo == "solicitar_devolucion" }
            .any { it.payloadJson.contains("\"p_producto_id\":\"$productoId\"") }
        if (yaPendiente) return Result.success(Unit)

        val localId = localIdActivo()
        // UUID generado en el dispositivo: es el id definitivo, el mismo antes
        // y después de sincronizar. Igual que Productos/Tarjetas/Mermas.
        val id = UUID.randomUUID().toString()
        val accionId = UUID.randomUUID().toString()
        val actuales = db.devolucionCacheDao().obtener(localId)?.toModel() ?: emptyList()
        val nueva = Devolucion(
            id = id, producto_id = productoId, producto_nombre = productoNombre,
            cantidad = cantidad, metodo = metodo, motivo = motivo, estado = "pendiente", local_id = localId
        )
        db.devolucionCacheDao().guardar((listOf(nueva) + actuales).toEntity(localId))

        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id)
            put("p_producto_id", productoId); put("p_cantidad", cantidad); put("p_metodo", metodo); put("p_motivo", motivo)
            put("p_accion_id", accionId)
        }
        encolarYSincronizar("solicitar_devolucion", payload)
        return Result.success(Unit)
    }

    /**
     * Aprobar/rechazar mueve stock real del lado del servidor — necesita
     * conexión sí o sí, para no arriesgarse a resolver dos veces la misma
     * devolución desde dos dispositivos offline.
     * destino: "stock" (vuelve a venderse) o "merma" (no sirve, se descarta). Ignorado si se rechaza.
     */
    suspend fun resolver(androidId: String, id: String, estado: String, destino: String? = null): Result<Unit> {
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

    /** Precarga las devoluciones pendientes de UN local específico, sin depender del local activo en sesión. */
    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val lista = SupabaseClientProvider.client.postgrest
                .rpc("get_devoluciones", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Devolucion>()
            db.devolucionCacheDao().guardar(lista.toEntity(localId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun encolarYSincronizar(tipo: String, payload: JsonObject) {
        db.accionPendienteDao().encolar(
            AccionPendienteEntity(tipo = tipo, payloadJson = payload.toString())
        )
        if (NetworkMonitor.hayInternet(context)) {
            SyncWorker.sincronizarAhora(context)
        }
    }
}
