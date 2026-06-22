# Gestor360 - Reglas de ofuscación y protección

# Mantener la clase Application y sus métodos
-keep class com.gestor360.app.** { *; }

# Mantener las clases de Room
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase$** { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keep @androidx.room.Database class *

# Mantener las clases de modelo (no ofuscar)
-keep class org.luisito.gestor360.data.model.** { *; }

# Mantener las clases de Supabase y Retrofit
-keep class io.github.jan.supabase.** { *; }
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# Mantener los nombres de los métodos de las interfaces de ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Mantener las clases de Compose
-keep class androidx.compose.** { *; }

# Ofuscar todo lo demás al máximo
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes InnerClasses

# Mantener los nombres de las clases que se usan en XML (si las hay)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Mantener los nombres de las clases que usan el sistema Android
-keep class android.** { *; }

# Mantener los métodos nativos (si los hay)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Mantener los métodos que se invocan desde XML
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

# Mantener los Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
