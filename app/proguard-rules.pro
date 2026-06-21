# Reglas para que el SDK de Supabase y kotlinx.serialization funcionen
# correctamente con minificación activada en release.

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

-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
