package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.SupabaseClient
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.domain.repository.IUserRepository
import org.luisito.gestor360.domain.result.Result

class UserRepositoryImpl : IUserRepository {
    private val supabase = SupabaseClient.instance

    override suspend fun getCurrentUser(): Result<User> {
        return try {
            val userId = supabase.auth.currentSessionOrNull()?.user?.id
                ?: return Result.Error("No autenticado")
            
            val user = supabase.from("users")
                .select { filter { eq("id", userId) } }
                .decodeList<User>()
                .firstOrNull()
            
            if (user != null) Result.Success(user)
            else Result.Error("Usuario no encontrado")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al obtener usuario", e)
        }
    }

    override suspend fun getUserById(id: String): Result<User> {
        return try {
            val user = supabase.from("users")
                .select { filter { eq("id", id) } }
                .decodeList<User>()
                .firstOrNull()
            
            if (user != null) Result.Success(user)
            else Result.Error("Usuario no encontrado")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al obtener usuario", e)
        }
    }

    override suspend fun updateUser(user: User): Result<Unit> {
        return try {
            supabase.from("users")
                .update(user) { filter { eq("id", user.id) } }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al actualizar usuario", e)
        }
    }
}
