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
import org.luisito.gestor360.data.local.entities.VentaEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.InventarioDia
import org.luisito.gestor360.data.models.ProductoVendidoInfo
import org.luisito.gestor360.data.models.TotalesVentas
import org.luisito.gestor360.data.models.VentaInfo
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import java.time.LocalDate

/**
 * RPC: get_inventario_dia (trae todo lo del día en una sola llamada) y
 * cerrar_turno (única acción manual que queda; abrir turno es automático
 * del lado del servidor, ver fn_asegurar_turno_abierto en el SQL).
 *
 * Offline-first: get_inventario_dia se cachea completo como JSON por
 * local+fecha (ver InventarioCacheEntity, Opción B del audit). cerrarTurno
 * sigue requiriendo conexión siempre: depende de los totales reales del
 * servidor y es una operación transaccional que no puede arriesgarse a
 * duplicarse si se hace offline.
 *
 * IMPORTANTE — datos creados SIN CONEXIÓN: inventarioCacheDao solo se
 * actualiza cuando refrescarDesdeServidor() corre (es decir, cuando hay
 * internet y el RPC responde). Una venta hecha offline SÍ se guarda al
 * instante en ventas_cache (ver SaleRepository.guardarVenta, sincronizada =
 * false) para que el carrito y el listado de ventas la muestren de
 * inmediato, pero antes NUNCA se reflejaba en getInventarioDia: el usuario
 * vendía sin señal y el reporte del día (totales, productos vendidos,
 * cierre de turno) seguía mostrando el snapshot viejo del servidor como si
 * esa venta no hubiera pasado, hasta que volvía la conexión y se
 * resincronizaba. fusionarVentasLocales() cierra ese hueco: toma el
 * snapshot cacheado (o uno vacío si nunca hubo uno) y le suma encima las
 * ventas de ventas_cache de ese local+fecha que todavía no se sincronizaron.
 */
class InventarioRepository(private val context: Context = AppContextHolder.context) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)
    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getInventarioDia(androidId: String, fecha: LocalDate): Result<InventarioDia> {
        val localId = localIdActivo()
        val cacheado = db.inventarioCacheDao().obtener(localId, fecha.toString())
        if (cacheado != null) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch { refrescarDesdeServidor(androidId, fecha) }
            }
            return Result.success(fusionarVentasLocales(cacheado.toModel(), localId, fecha))
        }
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.success(fusionarVentasLocales(InventarioDia(fecha = fecha.toString()), localId, fecha))
        }
        return refrescarDesdeServidor(androidId, fecha).map { fusionarVentasLocales(it, localId, fecha) }
    }

    /**
     * Suma sobre `dia` las filas de ventas_cache de este local+fecha que
     * todavía no se sincronizaron (sincronizada = false) — esas son
     * exactamente las que se hicieron offline y que get_inventario_dia,
     * corriendo en el servidor, todavía no puede conocer. Idempotente y
     * seguro de llamar tanto online como offline: en cuanto SyncWorker
     * sincroniza esa venta, sincronizada pasa a true (o la fila se reemplaza
     * al llegar la real del servidor, ver SaleRepository.refrescarDesdeServidor)
     * y deja de sumarse acá para no duplicarla.
     */
    private suspend fun fusionarVentasLocales(dia: InventarioDia, localId: Long, fecha: LocalDate): InventarioDia {
        val pendientes = db.ventaDao().obtenerTodas(localId)
            .filter { !it.sincronizada && it.createdAt?.startsWith(fecha.toString()) == true }
        if (pendientes.isEmpty()) return dia

        val nombreUsuarioLocal = session.getNombre().takeIf { it.isNotBlank() }
        val ventasLocales = pendientes.map { venta -> venta.toVentaInfo(localId, nombreUsuarioLocal) }

        val productosVendidosFusionados = fusionarProductosVendidos(dia.productos_vendidos, pendientes)

        val totalesFusionados = dia.totales_ventas.let { t ->
            t.copy(
                efectivo = t.efectivo + pendientes.sumOf { it.efectivo },
                transferencia = t.transferencia + pendientes.sumOf { it.transferencia },
                cantidad_ventas = t.cantidad_ventas + pendientes.size
            )
        }

        return dia.copy(
            ventas = dia.ventas + ventasLocales,
            productos_vendidos = productosVendidosFusionados,
            totales_ventas = totalesFusionados
        )
    }

    private suspend fun VentaEntity.toVentaInfo(localId: Long, nombreUsuarioLocal: String?): VentaInfo {
        val producto = db.productoDao().obtenerPorId(productoId, localId)
        return VentaInfo(
            id = id,
            producto_nombre = producto?.nombre ?: "Producto #$productoId",
            cantidad = cantidad,
            total = total,
            metodo = metodo,
            efectivo = efectivo,
            transferencia = transferencia,
            anulada = false,
            usuario_nombre = nombreUsuarioLocal,
            usuario_rol = null,
            fecha = createdAt,
            cliente_ci = clienteCi,
            cliente_tel = clienteTel,
            cliente_nombre = clienteNombre
        )
    }

    /** Agrega la cantidad de cada venta pendiente al total_vendido del producto que le corresponde (por id, resuelto contra el caché). */
    private suspend fun fusionarProductosVendidos(existentes: List<ProductoVendidoInfo>, pendientes: List<VentaEntity>): List<ProductoVendidoInfo> {
        val porNombre = existentes.associateBy { it.nombre }.toMutableMap()
        val localId = pendientes.firstOrNull()?.localId ?: return existentes
        for (venta in pendientes) {
            val nombre = db.productoDao().obtenerPorId(venta.productoId, localId)?.nombre ?: "Producto #${venta.productoId}"
            val actual = porNombre[nombre] ?: ProductoVendidoInfo(nombre = nombre)
            porNombre[nombre] = actual.copy(total_vendido = actual.total_vendido + venta.cantidad)
        }
        return porNombre.values.toList()
    }

    /** Trae la verdad del servidor (ya filtrada por local_id) y actualiza el caché de ese día. */
    suspend fun refrescarDesdeServidor(androidId: String, fecha: LocalDate): Result<InventarioDia> {
        return try {
            val localId = localIdActivo()
            val resultado = SupabaseClientProvider.client.postgrest
                .rpc("get_inventario_dia", buildJsonObject {
                    put("p_android_id", androidId); put("p_local_id", localId); put("p_fecha", fecha.toString())
                })
                .decodeAs<InventarioDia>()
            db.inventarioCacheDao().guardar(resultado.toEntity(localId, fecha.toString()))
            Result.success(resultado)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cerrarTurno(androidId: String, turnoId: Long, cierre: Double): Result<Unit> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para cerrar el turno (hay que confirmar el total vendido con el servidor)"))
        }
        return try {
            SupabaseClientProvider.client.postgrest.rpc("cerrar_turno", buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localIdActivo()); put("p_turno_id", turnoId); put("p_cierre", cierre)
            })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Precarga el inventario del día de HOY para un local específico (no necesariamente el activo). */
    suspend fun precargarLocal(androidId: String, localId: Long, fecha: LocalDate = LocalDate.now()): Result<Unit> {
        return try {
            val resultado = SupabaseClientProvider.client.postgrest
                .rpc("get_inventario_dia", buildJsonObject {
                    put("p_android_id", androidId); put("p_local_id", localId); put("p_fecha", fecha.toString())
                })
                .decodeAs<InventarioDia>()
            db.inventarioCacheDao().guardar(resultado.toEntity(localId, fecha.toString()))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
