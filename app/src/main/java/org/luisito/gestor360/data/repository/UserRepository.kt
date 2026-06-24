package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.User
import kotlinx.serialization.json.Json

class UserRepository {
    suspend fun getUserByAuthId(authId: String): User? {
        return try {
            val supabase = SupabaseClientProvider.client
            val result = supabase.from("usuarios")
                .select {
                    filter {
                        eq("auth_id", authId)
                    }
                }
                .decodeAs<List<User>>()
            result.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
