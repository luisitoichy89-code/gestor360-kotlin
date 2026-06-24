package org.luisito.gestor360.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val authId: String,
    val username: String,
    val nombre: String?,
    val rol: String,
    val almacenId: String,
    val activo: Boolean
)
