package org.luisito.gestor360.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.luisito.gestor360.data.model.User

@Dao
interface UserDao {
    @Query("SELECT * FROM usuarios WHERE username = :username AND activo = 1 LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Insert
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("UPDATE usuarios SET password = :password WHERE id = :userId")
    suspend fun updatePassword(userId: Long, password: String)

    @Query("UPDATE usuarios SET lastOnlineLogin = :lastLogin WHERE id = :userId")
    suspend fun updateLastOnlineLogin(userId: Long, lastLogin: String)
}
