// Top-level build file - configuración global del proyecto
//
// AGP 8.12.0 soporta compileSdk hasta API 36 (requerido por androidx.browser,
// dependencia transitiva del SDK de Supabase Auth) sin necesitar el salto
// a AGP 9.x, que trae cambios de breaking (Kotlin integrado obligatorio).
plugins {
    id("com.android.application") version "8.12.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.25" apply false
}
