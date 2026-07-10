package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import org.luisito.gestor360.data.models.Tarjeta

/**
 * PK compuesta (id, localId): mismo motivo que ProductoEntity.
 * localId deja de ser nullable: en la práctica TarjetaRepository siempre lo
 * llena con localIdActivo(), que nunca devuelve null (lanza excepción antes).
 * Un campo nullable no puede formar parte de una PK compuesta en Room.
 */
@Entity(tableName = "tarjetas_cache", primaryKeys = ["id", "localId"])
data class TarjetaEntity(
    val id: Long,
    val banco: String,
    val numero: String,
    val titular: String?,
    val activo: Boolean,
    val localId: Long
)

fun TarjetaEntity.toModel() = Tarjeta(id = id, banco = banco, numero = numero, titular = titular, activo = activo, local_id = localId)

fun Tarjeta.toEntity(localId: Long) = TarjetaEntity(id = id, banco = banco, numero = numero, titular = titular, activo = activo, localId = localId)
