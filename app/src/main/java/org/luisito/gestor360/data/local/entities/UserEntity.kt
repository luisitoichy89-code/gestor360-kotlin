package org.luisito.gestor360.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * IMPORTANTE (seguridad): NUNCA se guarda el PIN en texto plano acá. Lo que
 * llega del servidor en User.pin se hashea (PBKDF2, ver PinSecurity) antes
 * de cachearse — pinHash + pinSalt son lo único que toca disco. Si alguien
 * extrae la base de datos (root, backup, etc.) no obtiene ningún PIN
 * utilizable, solo hashes que no se pueden revertir.
 *
 * `foto`: foto de perfil del usuario, SOLO local (nunca se sube a Supabase
 * Storage). Ya viene procesada antes de llegar acá: recortada cuadrada,
 * redimensionada a 128x128 y comprimida a JPEG calidad 70 (ver FotoUtils),
 * así que el BLOB que Room guarda pesa unos pocos KB, no la foto original.
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
    val pinSalt: String?,
    val foto: ByteArray? = null
)
