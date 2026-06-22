package org.luisito.gestor360.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val password: String = "",
    val nombre: String = "",
    val rol: String = "seller",
    val pin: String = "",
    val almacenId: String = "1",
    val activo: Boolean = true,
    val authId: String = "",
    val lastOnlineLogin: String = ""
)
