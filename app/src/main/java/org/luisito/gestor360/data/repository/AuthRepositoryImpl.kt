package org.luisito.gestor360.data.repository

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import org.luisito.gestor360.data.SupabaseClient
import org.luisito.gestor360.domain.repository.IAuthRepository
import org.luisito.gestor360.domain.result.Result

class AuthRepositoryImpl : IAuthRepository {
    private val supabase = SupabaseClient.instance

    override suspend fun login(email: String, password: String): Result<String> {
        return try {
            val response = supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Result.Success(response.session?.user?.email ?: email)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error de autenticación", e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            supabase.auth.signOut()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al cerrar sesión", e)
        }
    }

    override suspend fun getCurrentUserRole(): Result<String> {
        return try {
            val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return Result.Error("No autenticado")
            val result = supabase.from("users").select {
                filter { eq("id", userId) }
            }.decodeList<Map<String, String>>()
            
            val role = result.firstOrNull()?.get("role") ?: "vendedor"
            Result.Success(role)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al obtener rol", e)
        }
    }

    override suspend fun isAuthenticated(): Boolean {
        return supabase.auth.currentSessionOrNull() != null
    }
}
