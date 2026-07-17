package com.gestor360.tarjetas.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tarjeta = medio/cuenta de cobro informativa (ej. "BCP Ahorros", "Yape").
 * Pertenece a UN local. Se usa para etiquetar en Ventas por qué medio
 * entró el dinero cuando el pago es transferencia o mixto.
 *
 * A diferencia de mi primera versión, esta entity NO guarda accionId ni
 * syncStatus embebidos: el estado de sync vive en AccionPendienteEntity
 * (outbox genérico, ya usado por Productos). `pendienteSync` acá es solo
 * un flag denormalizado para que la UI pinte "sincronizando..." sin tener
 * que hacer join contra el outbox en cada render de lista.
 */
@Entity(
    tableName = "tarjetas",
    indices = [
        Index(value = ["localId"]),
        Index(value = ["localId", "activo"])
    ]
)
data class TarjetaEntity(
    @PrimaryKey
    val id: String, // UUID generado en el dispositivo (RN #1)

    val localId: String, // FK lógica a Local. Aislamiento estricto (RN #4)

    val nombre: String, // ej: "BCP Ahorros", "Yape", "Interbank Transferencia"

    val tipo: String?, // "banco" | "billetera_digital" | "otro"

    val numeroCuenta: String? = null,

    val activo: Boolean = true,

    val creadoPor: String, // UUID del usuario (admin) que la creó

    val createdAt: Long,

    val updatedAt: Long,

    val deletedAt: Long? = null, // soft delete; nunca se borra físicamente (puede estar en ventas)

    val version: Int = 1, // optimista, para resolver conflictos last-write-wins

    val pendienteSync: Boolean = true // true mientras exista alguna AccionPendienteEntity para este id
)
