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
import org.luisito.gestor360.data.models.*
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import java.time.LocalDate

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
            return Result.success(completarDesdeRoom(cacheado.toModel(), localId, fecha))
        }
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.success(completarDesdeRoom(InventarioDia(fecha = fecha.toString()), localId, fecha))
        }
        return refrescarDesdeServidor(androidId, fecha).map { completarDesdeRoom(it, localId, fecha) }
    }

    /** Completa las secciones offline que el RPC no devuelve: ventas locales, tarjetas, modificados, eliminados, devueltos. */
    private suspend fun completarDesdeRoom(dia: InventarioDia, localId: Long, fecha: LocalDate): InventarioDia {
        var resultado = fusionarVentasLocales(dia, localId, fecha)
        resultado = fusionarModificados(resultado, localId, fecha)
        resultado = fusionarEliminados(resultado, localId, fecha)
        resultado = fusionarDevueltos(resultado, localId, fecha)
        return resultado
    }

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

    private suspend fun fusionarModificados(dia: InventarioDia, localId: Long, fecha: LocalDate): InventarioDia {
        val fechaStr = fecha.toString()
        val productosModificadosHoy = db.productoDao().obtenerTodos(localId).filter { p ->
            p.updatedAt?.startsWith(fechaStr) == true && p.createdAt?.startsWith(fechaStr) != true
        }
        if (productosModificadosHoy.isEmpty()) return dia

        val localesComoInfo = productosModificadosHoy.map { p ->
            ProductoInfo(
                id = p.id, nombre = p.nombre, precio = p.precio, stock = p.stock,
                ubicacion = p.ubicacion, categoria = p.categoria, fecha = p.updatedAt,
                solicitado_por_nombre = null, resuelto_por_nombre = null
            )
        }
        return dia.copy(productos_modificados = dia.productos_modificados + localesComoInfo)
    }

    private suspend fun fusionarEliminados(dia: InventarioDia, localId: Long, fecha: LocalDate): InventarioDia {
        val fechaStr = fecha.toString()
        val eliminadosHoy = db.accionPendienteDao().obtenerPendientes().filter {
            it.tipo == "eliminar_producto" && it.estado == "sincronizado" && it.payloadJson.contains(fechaStr)
        }
        if (eliminadosHoy.isEmpty()) return dia

        val eliminadosInfo = eliminadosHoy.map { accion ->
            val json = kotlinx.serialization.json.Json.parseToJsonElement(accion.payloadJson).jsonObject
            val id = json["p_id"]?.toString()?.trim('"')?.toLongOrNull() ?: 0L
            val nombre = db.productoDao().obtenerPorId(id, localId)?.nombre ?: "Producto #$id"
            ProductoEliminadoInfo(
                id = id, nombre = nombre, stock = 0.0, resuelto_por_nombre = null, fecha = fechaStr
            )
        }
        return dia.copy(productos_eliminados = dia.productos_eliminados + eliminadosInfo)
    }

    private suspend fun fusionarDevueltos(dia: InventarioDia, localId: Long, fecha: LocalDate): InventarioDia {
        val fechaStr = fecha.toString()
        val devolucionesCache = db.devolucionCacheDao().obtener(localId)
        if (devolucionesCache == null) return dia

        val devueltasHoy = devolucionesCache.toModel().filter { d ->
            d.created_at?.startsWith(fechaStr) == true || d.resuelto_at?.startsWith(fechaStr) == true
        }
        if (devueltasHoy.isEmpty()) return dia

        val localesComoInfo = devueltasHoy.map { d ->
            DevueltoInfo(
                id = d.id ?: 0L, producto_nombre = d.producto_nombre, cantidad = d.cantidad,
                metodo = d.metodo, estado = d.estado,
                solicitado_por_nombre = d.solicitado_por_nombre,
                resuelto_por_nombre = d.resuelto_por_nombre, resuelto_por_rol = null,
                fecha = d.resuelto_at ?: d.created_at
            )
        }
        return dia.copy(devueltos = dia.devueltos + localesComoInfo)
    }

    private suspend fun VentaEntity.toVentaInfo(localId: Long, nombreUsuarioLocal: String?): VentaInfo {
        val productoNombreLocal = productoNombre ?: db.productoDao().obtenerPorId(productoId, localId)?.nombre ?: "Producto #$productoId"
        var tarjetaBanco: String? = null
        var tarjetaNumero: String? = null
        var tarjetaTitular: String? = null
        if (tarjetaId != null) {
            val tarjeta = db.tarjetaDao().obtenerTodas(localId).find { it.id == tarjetaId }
            tarjetaBanco = tarjeta?.banco
            tarjetaNumero = tarjeta?.numero
            tarjetaTitular = tarjeta?.titular
        }

        return VentaInfo(
            id = id, producto_nombre = productoNombreLocal,
            cantidad = cantidad, total = total, metodo = metodo,
            efectivo = efectivo, transferencia = transferencia,
            anulada = false, usuario_nombre = nombreUsuarioLocal, usuario_rol = null,
            fecha = createdAt, cliente_ci = clienteCi, cliente_tel = clienteTel, cliente_nombre = clienteNombre,
            tarjeta_banco = tarjetaBanco, tarjeta_numero = tarjetaNumero, tarjeta_titular = tarjetaTitular
        )
    }

    private suspend fun fusionarProductosVendidos(existentes: List<ProductoVendidoInfo>, pendientes: List<VentaEntity>): List<ProductoVendidoInfo> {
        val porNombre = existentes.associateBy { it.nombre }.toMutableMap()
        val localId = pendientes.firstOrNull()?.localId ?: return existentes
        for (venta in pendientes) {
            val nombre = venta.productoNombre ?: db.productoDao().obtenerPorId(venta.productoId, localId)?.nombre ?: "Producto #${venta.productoId}"
            val actual = porNombre[nombre] ?: ProductoVendidoInfo(nombre = nombre)
            porNombre[nombre] = actual.copy(total_vendido = actual.total_vendido + venta.cantidad)
        }
        return porNombre.values.toList()
    }

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
            return Result.failure(IllegalStateException("Necesitas conexión para cerrar el turno"))
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
