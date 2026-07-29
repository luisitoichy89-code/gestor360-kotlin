package org.luisito.gestor360.data.sync

import android.content.Context
import androidx.room.withTransaction
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
        private const val TAMANO_LOTE = 50
        private const val MAX_ACCIONES_POR_SESION = 500
        private const val PAUSA_ENTRE_LOTES_MS = 300L
        private const val MAX_INTENTOS = 8
        private val TIPOS_NUNCA_ABANDONAR = setOf("registrar_venta", "anular_venta")
    }

    suspend fun sincronizar(androidId: String): SyncResultado {
        if (androidId.isBlank()) return SyncResultado(0, 0, "Sin sesión activa")
        if (!NetworkMonitor.hayInternet(context)) return SyncResultado(0, 0, "Sin conexión")

        when (val estado = deviceVerificationRepository.verificarEnCaliente(androidId)) {
            is VerificacionEnCalienteResultado.Bloqueado -> {
                session.marcarSesionRevocada()
                return SyncResultado(0, 0, estado.mensaje, licenciaBloqueada = true)
            }
            else -> {}
        }

        repararAccionesLegacyCreacionProducto()

        var exitosas = 0
        var fallidas = 0
        var procesadasEnSesion = 0
        val eliminadosProductos = mutableListOf<Pair<String, Long>>()
        val eliminadosTarjetas = mutableListOf<Pair<String, Long>>()

        while (procesadasEnSesion < MAX_ACCIONES_POR_SESION) {
            if (!NetworkMonitor.hayInternet(context)) break

            val lote = db.accionPendienteDao().obtenerLotePendiente(TAMANO_LOTE)
            if (lote.isEmpty()) break

            for (accion in lote) {
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
                        "registrar_venta" -> {
                            val ventaId = payload["p_id"]?.toString()?.trim('"')
                            if (ventaId != null) db.ventaDao().marcarSincronizada(ventaId)
                        }
                        "crear_tarjeta", "actualizar_tarjeta", "activar_tarjeta",
                        "crear_merma", "resolver_merma",
                        "crear_devolucion", "resolver_devolucion",
                        "solicitar_producto", "solicitar_aumento_stock",
                        "anular_venta" -> { }
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
                    val intentos = accion.intentos + 1
                    val seAbandona = intentos >= MAX_INTENTOS && accion.tipo !in TIPOS_NUNCA_ABANDONAR
                    db.accionPendienteDao().actualizar(
                        accion.copy(
                            intentos = intentos,
                            ultimoError = e.message?.take(300),
                            estado = if (seAbandona) "error_permanente" else "pendiente"
                        )
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

            procesadasEnSesion += lote.size
            if (lote.size < TAMANO_LOTE) break
            kotlinx.coroutines.delay(PAUSA_ENTRE_LOTES_MS)
        }

        db.withTransaction {
            for ((pid, lid) in eliminadosProductos) db.productoDao().eliminar(pid, lid)
            for ((tid, lid) in eliminadosTarjetas) db.tarjetaDao().eliminar(tid, lid)
        }

        val localIdActivo = session.getLocalId()
        if (localIdActivo != null) {
            refrescarProductosYDetectarConflictos(androidId)
            tarjetaRepository.refrescarDesdeServidor(localIdActivo)
            mermaRepository.refrescarDesdeServidor(localIdActivo)
            turnoRepository.refrescarDesdeServidor(androidId)
            aprobacionStockRepository.refrescarDesdeServidor(androidId)
        }

        db.withTransaction {
            db.accionPendienteDao().limpiarSincronizadas()
            db.ventaDao().limpiarSincronizadas(localIdActivo!!)
            db.turnoDao().limpiarCerrados(localIdActivo!!)
        }
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
