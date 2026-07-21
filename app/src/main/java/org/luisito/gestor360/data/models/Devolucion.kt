package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

/**
 * Un cliente regresa a devolver un producto: el vendedor solicita (queda
 * "pendiente"), el admin la resuelve — si aprueba, elige a dónde va el
 * producto: "stock" (vuelve a venderse) o "merma" (no sirve, se descarta).
 *
 * id: UUID generado en el dispositivo (igual que Producto/Tarjeta/Merma), ya
 * no bigserial del servidor. Ver DevolucionRepository.solicitar.
 */
@Serializable
data class Devolucion(
    val id: String,
    val producto_id: String? = null,
    val producto_nombre: String,
    val cantidad: Double,
    val metodo: String,
    val motivo: String? = null,
    val estado: String = "pendiente",
    val solicitado_por: Long? = null,
    val solicitado_por_nombre: String? = null,
    val resuelto_por: Long? = null,
    val resuelto_por_nombre: String? = null,
    val local_id: Long? = null,
    val created_at: String? = null,
    val resuelto_at: String? = null
)
