package org.luisito.gestor360.data.sync

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.ConflictoEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.repository.MermaRepository
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.TarjetaRepository
import org.luisito.gestor360.data.repository.TurnoRepository

data class SyncResultado(val exitosas: Int, val fallidas: Int, val error: String? = null)

/**
 * Motor central de sincronización. Recorre "acciones_pendientes" en orden y
 * reproduce cada llamada RPC exactamente como se habría hecho online. Para las
 * acciones que crean algo (crear_producto, abrir_turno, crear_tarjeta,
 * crear_merma) usa el id real que devuelve el servidor para reemplazar el id
 * temporal negativo que se usó mientras estaba offline. Al terminar, refresca
 * todos los cachés y detecta conflictos (ej. stock negativo).
 */
class SyncManager(private val context: Context) {

    private val db = AppDatabase.obtener(context)
    private val productRepository = ProductRepository(context)
    private val tarjetaRepository = TarjetaRepository(context)
    private val mermaRepository = MermaRepository(context)
    private val turnoRepository = TurnoRepository(context)

    suspend fun sincronizar(androidId: String): SyncResultado {
        if (androidId.isBlank()) return SyncResultado(0, 0, "Sin sesión activa")
        if (!NetworkMonitor.hayInternet(context)) return SyncResultado(0, 0, "Sin conexión")

        val pendientes = db.accionPendienteDao().obtenerPendientes()
        var exitosas = 0
        var fallidas = 0

        for (accion in pendientes) {
            try {
                val payload = Json.parseToJsonElement(accion.payloadJson).jsonObject
                val respuesta = SupabaseClientProvider.client.postgrest.rpc(accion.tipo, payload)

                if (accion.idLocalTemporal != null) {
                    reemplazarIdTemporal(accion.tipo, accion.idLocalTemporal, respuesta)
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

        refrescarProductosYDetectarConflictos(androidId)
        tarjetaRepository.refrescarDesdeServidor(androidId)
        mermaRepository.refrescarDesdeServidor(androidId)
        turnoRepository.refrescarDesdeServidor(androidId)

        db.accionPendienteDao().limpiarSincronizadas()
        db.ventaDao().limpiarSincronizadas()
        db.mermaDao().limpiarResueltas()
        db.turnoDao().limpiarCerrados()

        return SyncResultado(exitosas, fallidas, null)
    }

    private suspend fun reemplazarIdTemporal(
        tipo: String,
        idTemporal: Long,
        respuesta: io.github.jan.supabase.postgrest.result.PostgrestResult
    ) {
        when (tipo) {
            "crear_producto" -> runCatching { respuesta.decodeAs<Long>() }.getOrNull()
                ?.let { db.productoDao().reemplazarIdTemporal(idTemporal, it) }
            "abrir_turno" -> runCatching { respuesta.decodeAs<Long>() }.getOrNull()
                ?.let { db.turnoDao().reemplazarIdTemporal(idTemporal, it) }
            "crear_tarjeta" -> runCatching { respuesta.decodeAs<Long>() }.getOrNull()
                ?.let { db.tarjetaDao().reemplazarIdTemporal(idTemporal, it) }
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
