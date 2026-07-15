package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
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
        val fechaStr = fecha.toString()
        
        // 1. Intentar caché del servidor
        val cacheado = db.inventarioCacheDao().obtener(localId, fechaStr)
        
        // 2. Construir desde Room SIEMPRE (offline-first real)
        val desdeRoom = construirDesdeRoom(localId, fecha)
        
        // 3. Si hay caché del servidor, fusionar con datos locales
        val base = if (cacheado != null) {
            val servidor = cacheado.toModel()
            completarDesdeRoom(servidor, localId, fecha)
        } else {
            desdeRoom
        }
        
        // 4. Refrescar en segundo plano si hay internet
        if (NetworkMonitor.hayInternet(context)) {
            CoroutineScope(Dispatchers.IO).launch {
                refrescarDesdeServidor(androidId, fecha)
            }
        }
        
        return Result.success(base)
    }

    private suspend fun construirDesdeRoom(localId: Long, fecha: LocalDate): InventarioDia {
        val fechaStr = fecha.toString()
        
        // Ventas locales del día (sincronizadas y no sincronizadas)
        val ventasHoy = db.ventaDao().obtenerTodas(localId)
            .filter { it.createdAt?.startsWith(fechaStr) == true }
        
        val nombreUsuarioLocal = session.getNombre().takeIf { it.isNotBlank() }
        val ventasInfo = ventasHoy.map { it.toVentaInfo(localId, nombreUsuarioLocal) }
        
        // Productos vendidos
        val productosVendidos = fusionarProductosVendidos(emptyList(), ventasHoy)
        
        // Totales
        val totales = TotalesVentas(
            efectivo = ventasHoy.sumOf { it.efectivo },
            transferencia = ventasHoy.sumOf { it.transferencia },
            cantidad_ventas = ventasHoy.size
        )
        
        // Productos modificados hoy
        val modificados = db.productoDao().obtenerTodos(localId).filter { p ->
            p.updatedAt?.startsWith(fechaStr) == true
        }.map { p ->
            ProductoInfo(
                id = p.id, nombre = p.nombre, precio = p.precio, stock = p.stock,
                ubicacion = p.ubicacion, fecha = p.updatedAt,
                solicitado_por_nombre = null, resuelto_por_nombre = null
            )
        }
        
        // Devoluciones del día
        val devolucionesCache = db.devolucionCacheDao().obtener(localId)
        val devueltos = if (devolucionesCache != null) {
            devolucionesCache.toModel().filter { d ->
                d.created_at?.startsWith(fechaStr) == true || d.resuelto_at?.startsWith(fechaStr) == true
            }.map { d ->
                DevueltoInfo(
                    id = d.id ?: 0L, producto_nombre = d.producto_nombre, cantidad = d.cantidad,
                    metodo = d.metodo, estado = d.estado,
                    solicitado_por_nombre = d.solicitado_por_nombre,
                    resuelto_por_nombre = d.resuelto_por_nombre, resuelto_por_rol = null,
                    fecha = d.resuelto_at ?: d.created_at
                )
            }
        } else emptyList()
        
        // Productos eliminados hoy (desde acciones pendientes y sincronizadas recientes)
        val eliminados = db.accionPendienteDao().obtenerPendientes()
            .filter { it.tipo == "eliminar_producto" }
            .mapNotNull { accion ->
                try {
                    val json = Json.parseToJsonElement(accion.payloadJson).jsonObject
                    val pid = json["p_id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                    val nombre = db.productoDao().obtenerPorId(pid, localId)?.nombre ?: "Producto #$pid"
                    ProductoEliminadoInfo(id = pid, nombre = nombre, stock = 0.0, resuelto_por_nombre = null, fecha = fechaStr)
                } catch (e: Exception) {
                    null
                }
            }
        
        return InventarioDia(
            fecha = fechaStr,
            ventas = ventasInfo,
            productos_vendidos = productosVendidos,
            productos_modificados = modificados,
            productos_eliminados = eliminados,
            devueltos = devueltos,
            totales_ventas = totales,
        )
    }

    private suspend fun completarDesdeRoom(dia: InventarioDia, localId: Long, fecha: LocalDate): InventarioDia {
        val local = construirDesdeRoom(localId, fecha)
        return dia.copy(
            ventas = (dia.ventas + local.ventas).distinctBy { it.id },
            productos_vendidos = fusionarListasProductosVendidos(dia.productos_vendidos, local.productos_vendidos),
            productos_modificados = (dia.productos_modificados + local.productos_modificados).distinctBy { it.id },
            productos_eliminados = (dia.productos_eliminados + local.productos_eliminados).distinctBy { it.id },
            devueltos = (dia.devueltos + local.devueltos).distinctBy { it.id },
            totales_ventas = TotalesVentas(
                efectivo = (dia.totales_ventas?.efectivo ?: 0.0) + (local.totales_ventas?.efectivo ?: 0.0),
                transferencia = (dia.totales_ventas?.transferencia ?: 0.0) + (local.totales_ventas?.transferencia ?: 0.0),
                cantidad_ventas = (dia.totales_ventas?.cantidad_ventas ?: 0) + (local.totales_ventas?.cantidad_ventas ?: 0)
            )
        )
    }

    private fun fusionarListasProductosVendidos(
        servidor: List<ProductoVendidoInfo>,
        locales: List<ProductoVendidoInfo>
    ): List<ProductoVendidoInfo> {
        val mapa = servidor.associateBy { it.nombre }.toMutableMap()
        for (local in locales) {
            val existente = mapa[local.nombre]
            if (existente != null) {
                mapa[local.nombre] = existente.copy(total_vendido = existente.total_vendido + local.total_vendido)
            } else {
                mapa[local.nombre] = local
            }
        }
        return mapa.values.toList()
    }

    private suspend fun VentaEntity.toVentaInfo(localId: Long, nombreUsuarioLocal: String?): VentaInfo {
        val productoNombreLocal = productoNombre
            ?: db.productoDao().obtenerPorId(productoId, localId)?.nombre
            ?: "Producto #$productoId"
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

    private suspend fun fusionarProductosVendidos(
        existentes: List<ProductoVendidoInfo>,
        pendientes: List<VentaEntity>
    ): List<ProductoVendidoInfo> {
        val porNombre = existentes.associateBy { it.nombre }.toMutableMap()
        val localId = pendientes.firstOrNull()?.localId ?: return existentes
        for (venta in pendientes) {
            val nombre = venta.productoNombre
                ?: db.productoDao().obtenerPorId(venta.productoId, localId)?.nombre
                ?: "Producto #${venta.productoId}"
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
