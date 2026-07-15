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

        val pendientes = db.accionPendienteDao().obtenerPendientes()
        var exitosas = 0
        var fallidas = 0
        val eliminadosProductos = mutableListOf<Pair<Long, Long>>()
        val eliminadosTarjetas = mutableListOf<Pair<Long, Long>>()

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
                        val pid = payload["p_id"]?.toString()?.trim('"')?.toLongOrNull()
                        val lid = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull()
                        if (pid != null && lid != null) eliminadosProductos.add(pid to lid)
                    }
                    "eliminar_tarjeta" -> {
                        val tid = payload["p_id"]?.toString()?.trim('"')?.toLongOrNull()
                        val lid = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull()
                        if (tid != null && lid != null) eliminadosTarjetas.add(tid to lid)
                    }
                }

                db.accionPendienteDao().actualizar(accion.copy(estado = "sincronizado"))
                exitosas++
            } catch (e: Exception) {
                db.accionPendienteDao().actualizar(
                    accion.copy(intentos = accion.intentos + 1, ultimoError = e.message?.take(300))
                )
                fallidas++
            }
        }

        for ((pid, lid) in eliminadosProductos) {
            db.productoDao().eliminar(pid, lid)
        }
        for ((tid, lid) in eliminadosTarjetas) {
            db.tarjetaDao().eliminar(tid, lid)
        }

        if (session.getLocalId() != null) {
            refrescarProductosYDetectarConflictos(androidId)
            tarjetaRepository.refrescarDesdeServidor(androidId)
            mermaRepository.refrescarDesdeServidor(androidId)
            turnoRepository.refrescarDesdeServidor(androidId)
            aprobacionStockRepository.refrescarDesdeServidor(androidId)
        }

        db.accionPendienteDao().limpiarSincronizadas()
        db.ventaDao().limpiarSincronizadas()
        db.mermaDao().limpiarResueltas()
        db.turnoDao().limpiarCerrados()
        return SyncResultado(exitosas, fallidas, null)
    }

    private suspend fun reemplazarIdTemporal(
        tipo: String,
        idTemporal: Long,
        respuesta: io.github.jan.supabase.postgrest.result.PostgrestResult,
        localId: Long
    ) {
        when (tipo) {
            "crear_producto" -> runCatching { respuesta.decodeAs<Long>() }.getOrNull()
                ?.let { db.productoDao().reemplazarIdTemporal(idTemporal, it, localId) }
            "abrir_turno" -> runCatching { respuesta.decodeAs<Long>() }.getOrNull()
                ?.let { db.turnoDao().reemplazarIdTemporal(idTemporal, it, localId) }
            "crear_tarjeta" -> runCatching { respuesta.decodeAs<Long>() }.getOrNull()
                ?.let { db.tarjetaDao().reemplazarIdTemporal(idTemporal, it, localId) }
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
