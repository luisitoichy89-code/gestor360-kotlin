package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import kotlinx.serialization.json.Json
import org.luisito.gestor360.data.models.InventarioDia

/**
 * Caché de get_inventario_dia. Igual que LocalEntity: se guarda el JSON
 * completo en vez de columna por columna, porque es un agregado (turno +
 * ventas + mermas + devoluciones + productos) que no amerita su propia
 * tabla relacional (ver log de auditoría, Opción B). Clave: local + fecha,
 * un día que ya pasó no vuelve a cambiar salvo que se refresque a propósito.
 */
@Entity(tableName = "inventario_cache", primaryKeys = ["localId", "fecha"])
data class InventarioCacheEntity(
    val localId: Long,
    val fecha: String,
    val json: String
)

private val jsonParser = Json { ignoreUnknownKeys = true }

fun InventarioDia.toEntity(localId: Long, fecha: String): InventarioCacheEntity =
    InventarioCacheEntity(localId = localId, fecha = fecha, json = jsonParser.encodeToString(InventarioDia.serializer(), this))

fun InventarioCacheEntity.toModel(): InventarioDia =
    jsonParser.decodeFromString(InventarioDia.serializer(), json)
