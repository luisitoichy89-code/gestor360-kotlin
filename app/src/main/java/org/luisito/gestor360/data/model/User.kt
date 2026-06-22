package org.luisito.gestor360.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class User(
    @PrimaryKey
    val id: Long,
    val auth_id: String?,
    val cliente_id: String,
    val username: String,
    val nombre: String?,
    val rol: String,
    val pin: String,
    val almacen_id: String,
    val activo: Boolean,
    val created_at: String?
)
