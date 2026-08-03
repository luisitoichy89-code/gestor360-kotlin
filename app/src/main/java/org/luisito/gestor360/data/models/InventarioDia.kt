package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class InventarioDia(
    val fecha: String? = null,
    val solo_lectura: Boolean = false,
    val turno: TurnoInfo? = null,
    val productos_nuevos: List<ProductoInfo> = emptyList(),
    val productos_modificados: List<ProductoInfo> = emptyList(),
    val productos_eliminados: List<ProductoEliminadoInfo> = emptyList(),
    val devueltos: List<DevueltoInfo> = emptyList(),
    val mermas: List<MermaInfo> = emptyList(),
    val ventas: List<VentaInfo> = emptyList(),
    val productos_vendidos: List<ProductoVendidoInfo> = emptyList(),
    val totales_ventas: TotalesVentas = TotalesVentas(),
    val totales_por_tarjeta: List<TotalTarjetaInfo> = emptyList()
)

@Serializable
data class TotalTarjetaInfo(
    val nombre: String,
    val total: Double = 0.0
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
    val id: String,
    val nombre: String,
    val precio: Double = 0.0,
    val stock: Double = 0.0,
    val ubicacion: String? = null,
    val fecha: String? = null,
    val solicitado_por_nombre: String? = null,
    val resuelto_por_nombre: String? = null
)

@Serializable
data class ProductoEliminadoInfo(
    val id: String,
    val nombre: String,
    val stock: Double = 0.0,
    val fecha: String? = null,
    val resuelto_por_nombre: String? = null
)

@Serializable
data class MermaInfo(
    val id: String,
    val producto_nombre: String,
    val cantidad: Double,
    val motivo: String,
    val estado: String,
    val solicitado_por_nombre: String? = null,
    val resuelto_por_nombre: String? = null,
    val fecha: String? = null
)

@Serializable
data class DevueltoInfo(
    val id: String,
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
    val fecha: String? = null,
    val cliente_ci: String? = null,
    val cliente_tel: String? = null,
    val cliente_nombre: String? = null,
    val tarjeta_banco: String? = null,
    val tarjeta_numero: String? = null,
    val tarjeta_titular: String? = null
)

@Serializable
data class ProductoVendidoInfo(
    val nombre: String,
    val total_vendido: Double = 0.0,
    val total_actual: Double = 0.0,
    val total_agregado: Double = 0.0,
    val total_merma: Double = 0.0,
    val total_inicial: Double = 0.0
)

@Serializable
data class TotalesVentas(
    val efectivo: Double = 0.0,
    val transferencia: Double = 0.0,
    val tarjeta: Double = 0.0,
    val total: Double = 0.0,
    val cantidad_ventas: Long = 0
)
