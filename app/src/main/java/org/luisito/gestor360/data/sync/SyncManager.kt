package org.luisito.gestor360.data.sync

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.ConflictoEntity
import org.luisito.gestor360.data.repository.AprobacionStockRepository
import org.luisito.gestor360.data.repository.DeviceVerificationRepository
import org.luisito.gestor360.data.repository.MermaRepository
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.TarjetaRepository
import org.luisito.gestor360.data.repository.TurnoRepository
import org.luisito.gestor360.data.repository.VerificacionEnCalienteResultado
import org.luisito.gestor360.utils.SessionManager

/**
 * `licenciaBloqueada`: true cuando NO se sincronizó nada porque, antes de
 * tocar la cola, se detectó que el usuario ya no está activo o la licencia
 * ya no es válida (ver SyncManager.sincronizar()). Útil si SyncWorker quiere
 * avisar de esto en una notificación en vez de tratarlo como un fallo de red más.
 */
data class SyncResultado(val exitosas: Int, val fallidas: Int, val error: String? = null, val licenciaBloqueada: Boolean = false)

class SyncManager(private val context: Context) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)
    private val deviceVerificationRepository = DeviceVerificationRepository(context)
    private val productRepository = ProductRepository(context)
    private val tarjetaRepository = TarjetaRepository(context)
    private val mermaRepository = MermaRepository(context)
    private val turnoRepository = TurnoRepository(context)
    private val aprobacionStockRepository = AprobacionStockRepository(context)

    companion object {
        /** Máximo de acciones a procesar por ciclo para no saturar conexiones lentas. */
        private const val MAX_ACCIONES_POR_CICLO = 50
    }

    suspend fun sincronizar(androidId: String): SyncResultado {
        if (androidId.isBlank()) return SyncResultado(0, 0, "Sin sesión activa")
        if (!NetworkMonitor.hayInternet(context)) return SyncResultado(0, 0, "Sin conexión")

        // Antes de procesar CUALQUIER acción pendiente: si el usuario fue
        // desactivado o la licencia dejó de ser válida mientras el
        // dispositivo estuvo offline, no sincronizamos nada de lo pendiente
        // (evita colar ventas falsas de un empleado ya echado) y marcamos la
        // sesión como revocada para que la app vuelva a pedir verificación
        // de dispositivo la próxima vez. Si no hay respuesta clara del
        // servidor (sin internet real, error, timeout) esto NO bloquea —
        // ver VerificacionEnCalienteResultado.NoVerificado.
        when (val estado = deviceVerificationRepository.verificarEnCaliente(androidId)) {
            is VerificacionEnCalienteResultado.Bloqueado -> {
                session.marcarSesionRevocada()
                session.limpiarLicenciaVerificada()
                return SyncResultado(0, 0, estado.mensaje, licenciaBloqueada = true)
            }
            else -> {}
        }

        repararAccionesLegacyCreacionProducto()

        val pendientes = db.accionPendienteDao().obtenerPendientes().take(MAX_ACCIONES_POR_CICLO)
        var exitosas = 0
        var fallidas = 0
        val eliminadosProductos = mutableListOf<Pair<String, Long>>()
        val eliminadosTarjetas = mutableListOf<Pair<String, Long>>()

        for (accion in pendientes) {
            try {
                val payload = Json.parseToJsonElement(accion.payloadJson).jsonObject
                val respuesta = SupabaseClientProvider.client.postgrest.rpc(accion.tipo, payload)

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
                    "crear_tarjeta", "actualizar_tarjeta", "activar_tarjeta",
                    "crear_merma", "resolver_merma",
                    "crear_devolucion", "resolver_devolucion",
                    "solicitar_producto", "solicitar_aumento_stock",
                    "registrar_venta", "anular_venta" -> { }
                    "eliminar_tarjeta" -> {
                        val tid = payload["p_id"]?.toString()?.trim('"')
                        val lid = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull()
                        if (tid != null && lid != null) eliminadosTarjetas.add(tid to lid)
                    }
                }

                db.accionPendienteDao().actualizar(accion.copy(estado = "sincronizado"))
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
            mermaRepository.refrescarDesdeServidor(localIdActivo)
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
            "abrir_turno" -> runCatching { respuesta.decodeAs<Long>() }.getOrNull()
                ?.let { db.turnoDao().reemplazarIdTemporal(idTemporal, it, localId) }
        }
    }

    private suspend fun repararAccionesLegacyCreacionProducto() {
        val legacy = db.accionPendienteDao().obtenerPendientes()
            .filter { it.tipo == "crear_producto" && !it.payloadJson.contains("\"p_id\"") && it.idLocalTemporal != null }

        for (accion in legacy) {
            try {
                val idTemporal = accion.idLocalTemporal ?: continue
                val payload = Json.parseToJsonElement(accion.payloadJson).jsonObject.toMutableMap()
                val localId = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull() ?: continue
                val nuevoId = java.util.UUID.randomUUID().toString()
                payload["p_id"] = JsonPrimitive(nuevoId)
                payload["p_accion_id"] = JsonPrimitive(java.util.UUID.randomUUID().toString())

                val filaVieja = db.productoDao().obtenerPorId(idTemporal.toString(), localId)
                if (filaVieja != null) {
                    db.productoDao().eliminar(idTemporal.toString(), localId)
                    db.productoDao().insertarUno(filaVieja.copy(id = nuevoId))
                }
                db.accionPendienteDao().actualizar(
                    accion.copy(payloadJson = JsonObject(payload).toString(), idLocalTemporal = null)
                )
            } catch (_: Exception) { }
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
