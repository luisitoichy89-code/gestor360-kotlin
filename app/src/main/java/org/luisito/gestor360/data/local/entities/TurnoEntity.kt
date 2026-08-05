package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import org.luisito.gestor360.data.models.Turno

@Entity(tableName = "turno_cache", primaryKeys = ["id", "localId"])
data class TurnoEntity(
    val id: Long,
    val usuarioId: Long?,
    val apertura: Double,
    val cierre: Double?,
    val diferencia: Double?,
    val createdAt: String?,
    val localId: Long,
    val numeroTurno: Int = 0
)

fun TurnoEntity.toModel() = Turno(
    id = id, usuario_id = usuarioId, apertura = apertura, cierre = cierre,
    diferencia = diferencia, created_at = createdAt, local_id = localId,
    numero_turno = numeroTurno
)

fun Turno.toEntity(localId: Long) = TurnoEntity(
    id = id, usuarioId = usuario_id, apertura = apertura, cierre = cierre,
    diferencia = diferencia, createdAt = created_at, localId = localId,
    numeroTurno = numero_turno
)
