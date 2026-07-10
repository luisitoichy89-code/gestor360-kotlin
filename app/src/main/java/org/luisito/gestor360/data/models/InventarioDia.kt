package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

/**
 * Toda la "hoja" de un día operativo de un local: arma en una sola llamada
 * (get_inventario_dia) lo que antes eran varias consultas sueltas. Cada
 * sub-lista trae fecha y quién (nombre + rol) hizo cada cosa, porque eso es
 * lo que reemplaza a la traza genérica que se eliminó.
 */
@Serializable
data class InventarioDia(
    val fecha: String,
    val solo_lectura: Boolean = false,
    val turno: TurnoInfo? = null,
    val productos_nuevos: List<ProductoInfo> = emptyList(),
    val productos_modificados: List<ProductoInfo> = emptyList(),
    val devueltos: List<DevueltoInfo> = emptyList(),
    val ventas: List<VentaInfo> = emptyList(),
    val totales_ventas: TotalesVentas = TotalesVentas()
)

@Serializable
data class TurnoInfo(
    val id: Long,
    val apertura: Double = 0.0,
    val cierre: Double? = null,
    val diferencia: Double? = null,
    val created_at: String? = null,
    val usuario_nombre: String? = null,
    val usuario_rol: String? = null
)

@Serializable
data class ProductoInfo(
    val id: Long,
    val nombre: String,
    val precio: Double = 0.0,
    val stock: Double = 0.0,
    val fecha: String? = null
)

@Serializable
data class DevueltoInfo(
    val id: Long,
    val producto_nombre: String,
    val cantidad: Double,
    val metodo: String,
    val estado: String,
    val solicitado_por_nombre: String? = null,
    val resuelto_por_nombre: String? = null,
    val resuelto_por_rol: String? = null,
    val fecha: String? = null
)

@Serializable
data class VentaInfo(
    val id: String,
    val producto_nombre: String,
    val cantidad: Double,
    val total: Double,
    val metodo: String,
    val efectivo: Double = 0.0,
    val transferencia: Double = 0.0,
    val anulada: Boolean = false,
    val usuario_nombre: String? = null,
    val usuario_rol: String? = null,
    val fecha: String? = null
)

@Serializable
data class TotalesVentas(
    val efectivo: Double = 0.0,
    val transferencia: Double = 0.0,
    val cantidad_ventas: Long = 0
)
