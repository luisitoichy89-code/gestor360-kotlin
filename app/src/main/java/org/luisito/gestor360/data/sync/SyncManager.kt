package org.luisito.gestor360.data.sync

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.ConflictoEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.repository.ProductRepository

data class SyncResultado(val exitosas: Int, val fallidas: Int, val error: String? = null)

/**
 * Motor central de sincronización. Recorre "acciones_pendientes" en orden y
 * reproduce cada llamada RPC exactamente como se habría hecho online. Al
 * terminar, refresca el caché de productos desde el servidor y compara: si
 * algo quedó con stock negativo, es señal de que dos dispositivos vendieron
 * lo mismo estando ambos sin conexión — se guarda como "conflicto" para que
 * un humano lo revise, en vez de corregirlo solo en silencio.
 */
class SyncManager(private val context: Context) {

    private val db = AppDatabase.obtener(context)
    private val productRepository = ProductRepository(context)

    suspend fun sincronizar(androidId: String): SyncResultado {
        if (androidId.isBlank()) return SyncResultado(0, 0, "Sin sesión activa")
        if (!NetworkMonitor.hayInternet(context)) return SyncResultado(0, 0, "Sin conexión")

        val pendientes = db.accionPendienteDao().obtenerPendientes()
        var exitosas = 0
        var fallidas = 0

        for (accion in pendientes) {
            try {
                val payload = Json.parseToJsonElement(accion.payloadJson).jsonObject
                SupabaseClientProvider.client.postgrest.rpc(accion.tipo, payload)
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
        db.accionPendienteDao().limpiarSincronizadas()
        db.ventaDao().limpiarSincronizadas()

        return SyncResultado(exitosas, fallidas, null)
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
