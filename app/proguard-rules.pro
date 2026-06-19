# Proteger clases críticas de ingeniería inversa
-keep class com.tu.paquete.** { *; }
-dontwarn javax.annotation.**
-dontwarn sun.misc.Unsafe
