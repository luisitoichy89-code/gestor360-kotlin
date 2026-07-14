# Este archivo faltaba en el proyecto aunque build.gradle.kts ya lo
# referenciaba con proguardFiles(...) — el build de "release" (isMinifyEnabled
# = true) iba a fallar o generar un APK roto sin esto.

# ------------------------------------------------------------------
# SEGURIDAD: eliminar logs en compilaciones release.
# ------------------------------------------------------------------
# El código usa android.util.Log.e/.d en varios repositorios (errores de
# sync, fallos de venta, etc). En debug es útil; en un APK de producción que
# puede terminar en un dispositivo inspeccionado por terceros, esos logs son
# información gratis vía `adb logcat` o apps de terceros con permiso de logs
# en versiones viejas de Android. R8 elimina las llamadas completas (con
# -assumenosideeffects) en vez de solo bajarles el nivel, así ni el string
# del mensaje queda en el APK.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# ------------------------------------------------------------------
# kotlinx.serialization (modelos @Serializable: Sale, User, Tarjeta, etc.)
# ------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class org.luisito.gestor360.**$$serializer { *; }
-keepclassmembers class org.luisito.gestor360.** {
    *** Companion;
}
-keepclasseswithmembers class org.luisito.gestor360.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ------------------------------------------------------------------
# Room
# ------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------
# SQLCipher (cifrado de la base local, ver AppDatabase/DbKeyProvider)
# ------------------------------------------------------------------
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# ------------------------------------------------------------------
# Ktor / Supabase (usan reflection para el engine de red)
# ------------------------------------------------------------------
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**
-keep class io.ktor.client.engine.android.** { *; }

# ------------------------------------------------------------------
# Tink (usado por androidx.security:security-crypto para EncryptedSharedPreferences)
# ------------------------------------------------------------------
# Tink referencia anotaciones de error-prone y JSR-305 como dependencias
# opcionales/compileOnly (solo se usan para chequeos en tiempo de
# compilación de Google, nunca en runtime). Como esos jars no están en
# nuestro classpath, R8 los reporta como "Missing class" y con
# isMinifyEnabled=true eso es error fatal, no solo warning. No hay que
# agregar error-prone/jsr305 como dependencia real: basta con decirle a
# R8 que ignore estas referencias porque el código que las usa (anotaciones
# puras, sin lógica) nunca se ejecuta.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
-dontwarn org.checkerframework.**
-dontwarn com.google.j2objc.annotations.**
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
