package org.luisito.gestor360.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth

object SupabaseClient {
    val instance: SupabaseClient by lazy {
        val url = System.getenv("SUPABASE_URL") ?: "https://duspeazziwxptcrignju.supabase.co"
        val anonKey = System.getenv("SUPABASE_ANON_KEY") ?: "sb_publishable_CGLNTn602vd77fEsR7yUYg_3f7eeQVu"
        
        createSupabaseClient(url, anonKey) {
            install(Postgrest)
            install(Auth)
        }
    }
}
