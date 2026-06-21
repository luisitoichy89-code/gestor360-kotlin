// Top-level build file - configuración global del proyecto
//
// Kotlin 2.3.21: el SDK de Supabase (supabase-kt, postgrest-kt, auth-kt) está
// compilado contra Kotlin 2.3.0+, así que no es compatible con Kotlin 1.9.x.
// Desde Kotlin 2.0+, el Compose Compiler se mueve a su propio plugin
// (org.jetbrains.kotlin.plugin.compose), con la misma versión que Kotlin.
plugins {
    id("com.android.application") version "8.12.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
}
