package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.luisito.gestor360.data.models.Turno

@Entity(tableName = "turno_cache")
data class TurnoEntity(
    @PrimaryKey val id: Long,
    val usuarioId: Long?,
    val apertura: Double,
    val cierre: Double?,
    val diferencia: Double?,
    val createdAt: String?,
    val localId: Long
)

fun TurnoEntity.toModel() = Turno(
    id = id, usuario_id = usuarioId, apertura = apertura, cierre = cierre,
    diferencia = diferencia, created_at = createdAt, local_id = localId
)

fun Turno.toEntity(localId: Long) = TurnoEntity(
    id = id, usuarioId = usuario_id, apertura = apertura, cierre = cierre,
    diferencia = diferencia, createdAt = created_at, localId = localId
)
