package com.gestor360.tarjetas.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tarjeta = medio/cuenta de cobro informativa. Pertenece a UN local.
 * Se usa para etiquetar en Ventas por qué medio entró el dinero cuando
 * el pago es transferencia o mixto.
 *
 * Alineada al patrón real de Productos:
 * - id: UUID generado en el dispositivo (RN #1)
 * - localId: Long, igual que productos.local_id (bigint autoincremental
 *   de Supabase, NO uuid)
 * - Sin created_at/updated_at/version: Productos tampoco los tiene.
 * - `pendienteSync` es solo un flag local para la UI, no viaja a Supabase.
 */
@Entity(
    tableName = "tarjetas",
    indices = [Index(value = ["localId"])]
)
data class TarjetaEntity(
    @PrimaryKey
    val id: String, // UUID como String en Room; se envía como uuid al RPC

    val localId: Long, // FK lógica a Local (bigint, igual que Productos)

    val nombre: String,

    val tipo: String?,

    val numeroCuenta: String? = null,

    val activo: Boolean = true,

    val pendienteSync: Boolean = true // true mientras haya una AccionPendienteEntity para este id
)
