package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cola genérica de sincronización: cada fila es UNA llamada RPC pendiente.
 * "tipo" es el nombre exacto de la función en Postgres (ej. "registrar_venta"),
 * y "payloadJson" es el mismo JsonObject que ya arma cada repositorio antes de
 * llamar a .rpc(...), guardado como texto. Así el motor de sync no necesita
 * saber nada específico de productos/ventas/etc: solo reproduce la llamada.
 */
@Entity(tableName = "acciones_pendientes")
data class AccionPendienteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val payloadJson: String,
    val creadoEn: Long = System.currentTimeMillis(),
    val intentos: Int = 0,
    val ultimoError: String? = null,
    val estado: String = "pendiente", // pendiente | sincronizado | error_permanente
    /** Para acciones que crean algo (ej. crear_producto): el id temporal local, para reemplazarlo cuando el servidor devuelva el id real. */
    val idLocalTemporal: Long? = null
)
