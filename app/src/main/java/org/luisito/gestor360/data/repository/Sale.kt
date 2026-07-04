package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

/**
 * Una fila por producto vendido. Con el RPC registrar_venta, el servidor arma las N filas
 * (una por ítem del carrito) repartiendo efectivo/transferencia proporcionalmente; el cliente
 * solo manda el carrito completo en una sola llamada.
 */
@Serializable
data class Sale(
    val id: Long? = null,
    val producto_id: Long,
    val producto_nombre: String,
    val cantidad: Double,
    val precio_unit: Double,
    val total: Double,
    val metodo: String,
    val efectivo: Double = 0.0,
    val transferencia: Double = 0.0,
    val usuario_id: Long? = null,
    val almacen_id: String? = null,
    val cliente_ci: String? = null,
    val cliente_tel: String? = null,
    val cliente_nombre: String? = null,
    val created_at: String? = null
)

/** Métodos de pago soportados, iguales al backend Flask (cash / transfer / mixed). */
enum class MetodoPago(val valor: String, val etiqueta: String) {
    EFECTIVO("cash", "Efectivo"),
    TRANSFERENCIA("transfer", "Transferencia"),
    MIXTO("mixed", "Mixto")
}
