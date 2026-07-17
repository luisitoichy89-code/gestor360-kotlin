package com.gestor360.core.sync

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Outbox genérico de acciones pendientes de sincronizar.
 *
 * IMPORTANTE: según resumen_implementado.md, esta tabla YA EXISTE (la usa
 * Productos). Esta clase es mi reconstrucción de su forma probable a partir
 * de la descripción ("Cada acción tiene p_accion_id único (UUID) en
 * AccionPendienteEntity"). Si el nombre real de campos difiere, ajustar
 * TarjetaRepository/TarjetaSyncWorker a la firma real — NO crear una tabla
 * duplicada en Room (rompería la migración v11 ya aplicada).
 */
@Entity(
    tableName = "acciones_pendientes",
    indices = [
        Index(value = ["modulo"]),
        Index(value = ["entidadId"])
    ]
)
data class AccionPendienteEntity(
    @PrimaryKey
    val accionId: String, // UUID generado en el dispositivo (RN #1 / RN #2)

    val modulo: String, // "tarjetas", "productos", "ventas", "mermas", ...

    val tipoAccion: String, // "CREAR" | "ACTUALIZAR" | "ELIMINAR"

    val entidadId: String, // id de la fila afectada (tarjeta.id)

    val payloadJson: String, // datos necesarios para reconstruir la llamada RPC

    val createdAt: Long,

    val intentos: Int = 0,

    val ultimoError: String? = null
)
