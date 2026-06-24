package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.SupabaseClient
import org.luisito.gestor360.data.models.User

class UserRepository {
    private val supabase = SupabaseClient.instance
    
    suspend fun getCurrentUser(): User? {
        return try {
            val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return null
            supabase.from("users")
                .select {
                    filter { eq("id", userId) }
                }
                .decodeList<User>()
                .firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
