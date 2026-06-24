package org.luisito.gestor360.data.repository

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import org.luisito.gestor360.data.SupabaseClient

class AuthRepository {
    private val supabase = SupabaseClient.instance
    
    suspend fun login(email: String, password: String): Result<String> {
        return try {
            val response = supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success(response.session?.user?.email ?: email)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun logout() {
        supabase.auth.signOut()
    }
}
