package org.luisito.gestor360.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * IMPORTANTE (seguridad): NUNCA se guarda el PIN en texto plano acá. Lo que
 * llega del servidor en User.pin se hashea (PBKDF2, ver PinSecurity) antes
 * de cachearse — pinHash + pinSalt son lo único que toca disco. Si alguien
 * extrae la base de datos (root, backup, etc.) no obtiene ningún PIN
 * utilizable, solo hashes que no se pueden revertir.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val authId: String,
    val username: String,
    val nombre: String?,
    val rol: String,
    val localId: Long?,
    val activo: Boolean,
    val pinHash: String?,
    val pinSalt: String?
)
