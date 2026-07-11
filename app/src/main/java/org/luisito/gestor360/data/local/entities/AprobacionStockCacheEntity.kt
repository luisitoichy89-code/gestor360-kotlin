package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.luisito.gestor360.data.repository.AprobacionStock

/**
 * Caché de la lista completa de aprobaciones pendientes de UN local, guardada
 * como JSON — mismo patrón que DevolucionCacheEntity/InventarioCacheEntity:
 * esta pantalla siempre muestra "todo lo pendiente ahora", no hace falta
 * una tabla fila por fila.
 */
@Entity(tableName = "aprobaciones_cache")
data class AprobacionStockCacheEntity(
    @PrimaryKey val localId: Long,
    val json: String,
    val actualizadoEn: Long = System.currentTimeMillis()
)

private val aprobacionJson = Json { ignoreUnknownKeys = true }

fun List<AprobacionStock>.toEntity(localId: Long): AprobacionStockCacheEntity =
    AprobacionStockCacheEntity(localId = localId, json = aprobacionJson.encodeToString(this))

fun AprobacionStockCacheEntity.toModel(): List<AprobacionStock> =
    aprobacionJson.decodeFromString(json)
