package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.luisito.gestor360.data.models.Tarjeta

@Entity(tableName = "tarjetas_cache")
data class TarjetaEntity(
    @PrimaryKey val id: Long,
    val banco: String,
    val numero: String,
    val titular: String?,
    val activo: Boolean
)

fun TarjetaEntity.toModel() = Tarjeta(id = id, banco = banco, numero = numero, titular = titular, activo = activo)
fun Tarjeta.toEntity() = TarjetaEntity(id = id, banco = banco, numero = numero, titular = titular, activo = activo)
