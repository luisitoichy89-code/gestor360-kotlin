package org.luisito.gestor360.domain.repository

import org.luisito.gestor360.domain.result.Result

interface IAuthRepository {
    suspend fun login(email: String, password: String): Result<String>
    suspend fun logout(): Result<Unit>
    suspend fun getCurrentUserRole(): Result<String>
    suspend fun isAuthenticated(): Boolean
}
