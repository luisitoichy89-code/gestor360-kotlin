package org.luisito.gestor360.data.repository

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import org.luisito.gestor360.data.SupabaseClientProvider

class AuthRepository {

    suspend fun login(username: String, password: String): LoginResult {
        val supabase = SupabaseClientProvider.client
        val email = "$username@gestor360.local"

        return runCatching {
            val response = supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            // El usuario autenticado está en la sesión
            val user = response.user
            if (user != null) {
                LoginResult.Success(user.id)
            } else {
                LoginResult.Error("Credenciales inválidas")
            }
        }.getOrElse { exception ->
            LoginResult.Error(exception.message ?: "Error de conexión")
        }
    }
}

sealed class LoginResult {
    data class Success(val userId: String) : LoginResult()
    data class Error(val message: String) : LoginResult()
}
