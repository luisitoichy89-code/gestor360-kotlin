package org.luisito.gestor360.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    /**
     * Guarda/reemplaza la foto de perfil (ByteArray ya comprimido 128x128
     * JPEG calidad 70, ver FotoUtils) de un usuario puntual, sin tocar el
     * resto de sus columnas.
     */
    @Query("UPDATE users SET foto = :foto WHERE id = :userId")
    suspend fun actualizarFoto(userId: String, foto: ByteArray)

    /** Lectura liviana: trae solo el BLOB de la foto, sin el resto de la fila. */
    @Query("SELECT foto FROM users WHERE id = :userId LIMIT 1")
    suspend fun getFoto(userId: String): ByteArray?

    @Query("DELETE FROM users")
    suspend fun clearAll()
}
