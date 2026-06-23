package org.luisito.gestor360.data.repository

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import org.luisito.gestor360.data.SupabaseClientProvider

class AuthRepository {

    suspend fun login(username: String, password: String): LoginResult {
        val supabase = SupabaseClientProvider.client
        val email = "$username@gestor360.local"

        return runCatching {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            // En la versión 3.5.0, el usuario se obtiene de la sesión actual
            val session = supabase.auth.currentSessionOrNull()
            val userId = session?.user?.id ?: throw Exception("No se pudo obtener el usuario")

            LoginResult.Success(userId)
        }.getOrElse { exception ->
            LoginResult.Error(exception.message ?: "Error de conexión")
        }
    }
}

sealed class LoginResult {
    data class Success(val userId: String) : LoginResult()
    data class Error(val message: String) : LoginResult()
}
