package org.luisito.gestor360.domain.repository

import org.luisito.gestor360.domain.result.Result
import org.luisito.gestor360.data.models.User

interface IUserRepository {
    suspend fun getCurrentUser(): Result<User>
    suspend fun getUserById(id: String): Result<User>
    suspend fun updateUser(user: User): Result<Unit>
}
