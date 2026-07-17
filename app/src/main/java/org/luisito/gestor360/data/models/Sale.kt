package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

/**
 * id ya era uuid. producto_id pasa de Long a String acá porque productos.id
 * ya es uuid en Supabase (lo migramos en el módulo Productos) — un
 * producto_id bigint ya no tiene con qué hacer join/FK contra productos.
 *
 * ATENCIÓN — esto todavía NO está resuelto del lado del servidor: mientras
 * no migremos el módulo Ventas, "registrar_venta" y "get_ventas" en Supabase
 * probablemente sigan esperando/devolviendo p_producto_id como bigint. Este
 * cambio destraba la COMPILACIÓN en Android, pero vender algo puede seguir
 * fallando en tiempo de ejecución contra esos RPCs viejos hasta que hagamos
 * el módulo Ventas (que debería incluir el ALTER de esta columna en la tabla
 * "ventas" de Supabase, igual que hicimos con productos).
 */
@Serializable
data class Sale(
    val id: String? = null,
    val producto_id: String,
    val producto_nombre: String? = null,
    val cantidad: Double,
    val total: Double,
    val metodo: String,
    val efectivo: Double = 0.0,
    val transferencia: Double = 0.0,
    val usuario_id: Long? = null,
    val local_id: Long? = null,
    val cliente_ci: String? = null,
    val cliente_tel: String? = null,
    val cliente_nombre: String? = null,
    // tarjeta_id se queda en Long: Tarjetas todavía no se migró a uuid.
    val tarjeta_id: Long? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

enum class MetodoPago(val valor: String, val etiqueta: String) {
    EFECTIVO("cash", "Efectivo"),
    TRANSFERENCIA("transfer", "Transferencia"),
    MIXTO("mixed", "Mixto")
}
