package org.luisito.gestor360.data.repository

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import org.luisito.gestor360.data.SupabaseClientProvider

class AuthRepository {

    suspend fun login(username: String, password: String): LoginResult {
        return try {
            val supabase = SupabaseClientProvider.client

            val email = "$username@gestor360.local"

            val response = supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            // ✅ CORRECTO: data contiene la sesión, y data.user es el usuario
            val session = response.data
            val user = session?.user

            if (user != null) {
                LoginResult.Success(user.id)
            } else {
                LoginResult.Error("Credenciales inválidas")
            }
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "Error de conexión")
        }
    }
}

sealed class LoginResult {
    data class Success(val userId: String) : LoginResult()
    data class Error(val message: String) : LoginResult()
}
