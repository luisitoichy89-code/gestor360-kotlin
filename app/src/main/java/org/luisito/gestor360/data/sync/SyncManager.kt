package org.luisito.gestor360.data.sync

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.ConflictoEntity
import org.luisito.gestor360.data.repository.AprobacionStockRepository
import org.luisito.gestor360.data.repository.MermaRepository
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.TarjetaRepository
import org.luisito.gestor360.data.repository.TurnoRepository
import org.luisito.gestor360.utils.SessionManager

data class SyncResultado(val exitosas: Int, val fallidas: Int, val error: String? = null)

class SyncManager(private val context: Context) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)
    private val productRepository = ProductRepository(context)
    private val tarjetaRepository = TarjetaRepository(context)
    private val mermaRepository = MermaRepository(context)
    private val turnoRepository = TurnoRepository(context)
    private val aprobacionStockRepository = AprobacionStockRepository(context)

    suspend fun sincronizar(androidId: String): SyncResultado {
        if (androidId.isBlank()) return SyncResultado(0, 0, "Sin sesión activa")
        if (!NetworkMonitor.hayInternet(context)) return SyncResultado(0, 0, "Sin conexión")

        repararAccionesLegacyCreacionProducto()

        val pendientes = db.accionPendienteDao().obtenerPendientes()
        var exitosas = 0
        var fallidas = 0
        val eliminadosProductos = mutableListOf<Pair<String, Long>>()
        val eliminadosTarjetas = mutableListOf<Pair<String, Long>>()

        for (accion in pendientes) {
            try {
                val payload = Json.parseToJsonElement(accion.payloadJson).jsonObject
                val respuesta = SupabaseClientProvider.client.postgrest.rpc(accion.tipo, payload)

                // Productos ya no usa idLocalTemporal (su id es el UUID definitivo
                // desde el momento en que se crea); esta rama sigue viva para
                // Tarjetas/Turnos, que todavía no pasaron por este módulo.
                if (accion.idLocalTemporal != null) {
                    val localIdDeLaAccion = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull()
                        ?: session.getLocalId()
                    if (localIdDeLaAccion != null) {
                        reemplazarIdTemporal(accion.tipo, accion.idLocalTemporal, respuesta, localIdDeLaAccion)
                    }
                }

                when (accion.tipo) {
                    "eliminar_producto" -> {
                        val pid = payload["p_id"]?.toString()?.trim('"')
                        val lid = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull()
                        if (pid != null && lid != null) eliminadosProductos.add(pid to lid)
                    }
                    "eliminar_tarjeta" -> {
                        val tid = payload["p_id"]?.toString()?.trim('"')
                        val lid = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull()
                        if (tid != null && lid != null) eliminadosTarjetas.add(tid to lid)
                    }
                }

                db.accionPendienteDao().actualizar(accion.copy(estado = "sincronizado"))
                // Reportar éxito a sync_queue
                try {
                    val localIdAccion = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull() ?: session.getLocalId()
                    if (localIdAccion != null) {
                        SyncReporter.reportar(androidId, localIdAccion, accion.tipo, payload)
                    }
                } catch (_: Exception) {}
                exitosas++
            } catch (e: Exception) {
                db.accionPendienteDao().actualizar(
                    accion.copy(intentos = accion.intentos + 1, ultimoError = e.message?.take(300))
                )
                // Reportar error a sync_queue
                try {
                    val localIdAccion = session.getLocalId()
                    if (localIdAccion != null) {
                        SyncReporter.reportarError(androidId, localIdAccion, accion.tipo, e.message ?: "Error desconocido")
                    }
                } catch (_: Exception) {}
                fallidas++
            }
        }

        for ((pid, lid) in eliminadosProductos) db.productoDao().eliminar(pid, lid)
        for ((tid, lid) in eliminadosTarjetas) db.tarjetaDao().eliminar(tid, lid)

        val localIdActivo = session.getLocalId()
        if (localIdActivo != null) {
            refrescarProductosYDetectarConflictos(androidId)
            tarjetaRepository.refrescarDesdeServidor(localIdActivo)
            mermaRepository.refrescarDesdeServidor(session.getLocalId() ?: 0)
            turnoRepository.refrescarDesdeServidor(androidId)
            aprobacionStockRepository.refrescarDesdeServidor(androidId)
        }

        db.accionPendienteDao().limpiarSincronizadas()
        db.ventaDao().limpiarSincronizadas()
        db.turnoDao().limpiarCerrados()
        return SyncResultado(exitosas, fallidas, null)
    }

    private suspend fun reemplazarIdTemporal(
        tipo: String, idTemporal: Long,
        respuesta: io.github.jan.supabase.postgrest.result.PostgrestResult, localId: Long
    ) {
        when (tipo) {
            // "crear_producto" ya no pasa por acá: ver ProductRepository.createProduct
            // (el id ahora es un UUID definitivo generado en el cliente, no hay
            // id temporal que reemplazar).
            "abrir_turno" -> runCatching { respuesta.decodeAs<Long>() }.getOrNull()
                ?.let { db.turnoDao().reemplazarIdTemporal(idTemporal, it, localId) }
            // "crear_tarjeta" no pasa por acá: el id es un UUID definitivo
            // generado en el cliente (igual que productos), no hay id temporal
            // que reemplazar. TarjetaRepository.createTarjeta nunca setea
            // idLocalTemporal, así que esta rama nunca se alcanzaría igual.
        }
    }

    /**
     * Caso borde de la migración a UUID (v9): si un producto se creó offline
     * con el sistema viejo (id temporal negativo) y esa creación quedó
     * pendiente justo cuando se actualizó la app, su acción "crear_producto"
     * no tiene "p_id" en el payload (el RPC viejo no lo pedía, generaba el id
     * en el servidor). El nuevo RPC sí lo exige, así que sin este parche esa
     * acción fallaría para siempre. Se corrige una sola vez: se genera un UUID
     * nuevo, se lo agrega al payload y se actualiza el id de esa fila en el
     * caché para que coincidan.
     */
    private suspend fun repararAccionesLegacyCreacionProducto() {
        val legacy = db.accionPendienteDao().obtenerPendientes()
            .filter { it.tipo == "crear_producto" && !it.payloadJson.contains("\"p_id\"") && it.idLocalTemporal != null }
        for (accion in legacy) {
            try {
                val idTemporal = accion.idLocalTemporal ?: continue
                val payload = Json.parseToJsonElement(accion.payloadJson).jsonObject.toMutableMap()
                val localId = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull() ?: continue
                val nuevoId = java.util.UUID.randomUUID().toString()
                payload["p_id"] = kotlinx.serialization.json.JsonPrimitive(nuevoId)
                payload["p_accion_id"] = kotlinx.serialization.json.JsonPrimitive(java.util.UUID.randomUUID().toString())

                val filaVieja = db.productoDao().obtenerPorId(idTemporal.toString(), localId)
                if (filaVieja != null) {
                    db.productoDao().eliminar(idTemporal.toString(), localId)
                    db.productoDao().insertarUno(filaVieja.copy(id = nuevoId))
                }
                db.accionPendienteDao().actualizar(
                    accion.copy(payloadJson = kotlinx.serialization.json.JsonObject(payload).toString(), idLocalTemporal = null)
                )
            } catch (_: Exception) {
                // Si algo falla acá, la acción sigue pendiente tal cual y se
                // reintenta en el próximo ciclo de sync; no se pierde nada.
            }
        }
    }

    private suspend fun refrescarProductosYDetectarConflictos(androidId: String) {
        productRepository.refrescarDesdeServidor(androidId).onSuccess { productosServidor ->
            productosServidor.filter { it.stock < 0 }.forEach { producto ->
                db.conflictoDao().insertar(
                    ConflictoEntity(
                        tipo = "stock_negativo",
                        descripcion = "\"${producto.nombre}\" quedó con stock ${producto.stock}. " +
                            "Probablemente dos dispositivos vendieron lo mismo estando sin conexión. " +
                            "Revisa y ajusta el stock manualmente.",
                        productoId = producto.id
                    )
                )
            }
        }
    }
}
