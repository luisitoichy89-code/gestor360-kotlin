package org.luisito.gestor360.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.luisito.gestor360.data.model.User

@Dao
interface UserDao {
    @Query("SELECT * FROM usuarios WHERE username = :username AND activo = 1 LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("UPDATE usuarios SET pin = :nuevoPin WHERE id = :userId")
    suspend fun updatePin(userId: Long, nuevoPin: String)

    @Query("DELETE FROM usuarios")
    suspend fun deleteAll()
}
