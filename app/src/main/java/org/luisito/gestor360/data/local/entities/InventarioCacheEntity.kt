package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import kotlinx.serialization.json.Json
import org.luisito.gestor360.data.models.InventarioDia

@Entity(tableName = "inventario_cache", primaryKeys = ["localId", "turnoId"])
data class InventarioCacheEntity(
    val localId: Long,
    val turnoId: Long,
    val json: String
)

private val jsonParser = Json { ignoreUnknownKeys = true }

fun InventarioDia.toEntity(localId: Long, turnoId: Long): InventarioCacheEntity =
    InventarioCacheEntity(localId = localId, turnoId = turnoId, json = jsonParser.encodeToString(InventarioDia.serializer(), this))

fun InventarioCacheEntity.toModel(): InventarioDia =
    jsonParser.decodeFromString(InventarioDia.serializer(), json)
