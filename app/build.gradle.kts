import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// El force() a kotlin-metadata-jvm:2.3.0-Beta1 se quitó al migrar de kapt a KSP:
// era un parche para el mismo conflicto kapt + plugin de serialización bajo K2.
// Si algo de metadata vuelve a fallar, es la primera línea a revisar.

// local.properties NO se carga automáticamente como Gradle properties: el AGP solo
// lee de ahí "sdk.dir". Por eso RELEASE_STORE_FILE/RELEASE_STORE_PASSWORD/etc. que el
// workflow de CI escribe en local.properties nunca llegaban a project.findProperty(),
// signingConfig quedaba null, y assembleRelease generaba un APK SIN FIRMAR con otro
// nombre (app-release-unsigned.apk) en vez de app-release.apk. El paso "Upload Release APK"
// del workflow buscaba exactamente app-release.apk, no lo encontraba, y por eso no
// aparecía ningún artifact para descargar. Se carga local.properties manualmente aquí
// como respaldo para que sirva tanto en CI (repo checkout limpio) como en local.
// Nota: se referencia como "Properties()" (import arriba), NO como "java.util.Properties()":
// el plugin de Android registra un accessor de Gradle llamado "java" en el scope del
// script, que tapa el paquete java.util al escribirlo inline y rompe la compilación
// con "Unresolved reference: util".
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) localFile.inputStream().use { load(it) }
}

android {
    namespace = "org.luisito.gestor360"
    compileSdk = 36

    val supabaseUrl = (project.findProperty("SUPABASE_URL") as String?) ?: ""
    val supabaseKey = (project.findProperty("SUPABASE_KEY") as String?) ?: ""

    defaultConfig {
        applicationId = "org.luisito.gestor360"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$supabaseKey\"")
    }

    // ============================================================
    // FIRMA RELEASE
    // ============================================================
    // 1. Genera el keystore UNA sola vez (en tu PC o Termux con keytool):
    //
    //    keytool -genkeypair -v -keystore gestor360-release.keystore \
    //      -alias gestor360 -keyalg RSA -keysize 2048 -validity 10000
    //
    // 2. Guárdalo fuera del repo (NUNCA lo subas a GitHub). Ponlo por ejemplo
    //    en la raíz del proyecto y agrega "*.keystore" a tu .gitignore.
    //
    // 3. Las contraseñas van en local.properties (que ya deberías tener en
    //    .gitignore), NO en este archivo, para no subirlas a git:
    //
    //    RELEASE_STORE_FILE=gestor360-release.keystore
    //    RELEASE_STORE_PASSWORD=tu_password_del_keystore
    //    RELEASE_KEY_ALIAS=gestor360
    //    RELEASE_KEY_PASSWORD=tu_password_de_la_key
    //
    // OJO: se usa localProperties["KEY"] (operador de Map, devuelve Any? -> String?)
    // y no localProperties.getProperty("KEY"), porque getProperty() es un método Java
    // que Kotlin ve como tipo plataforma "String!" (no marcado nullable), y entonces
    // el compilador infiere que releaseStoreFile nunca puede ser null y marca como
    // "siempre true" cualquier chequeo posterior (aunque si falta la propiedad sí lo es).
    val releaseStoreFile = (project.findProperty("RELEASE_STORE_FILE") as String?)
        ?: (localProperties["RELEASE_STORE_FILE"] as String?)
    val releaseStorePassword = (project.findProperty("RELEASE_STORE_PASSWORD") as String?)
        ?: (localProperties["RELEASE_STORE_PASSWORD"] as String?)
    val releaseKeyAlias = (project.findProperty("RELEASE_KEY_ALIAS") as String?)
        ?: (localProperties["RELEASE_KEY_ALIAS"] as String?)
    val releaseKeyPassword = (project.findProperty("RELEASE_KEY_PASSWORD") as String?)
        ?: (localProperties["RELEASE_KEY_PASSWORD"] as String?)

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Si no hay keystore configurado (ej. build de CI sin secretos), no rompe el build;
            // simplemente no habrá signingConfig y el APK quedará sin firmar.
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.4.3")
    implementation("io.ktor:ktor-client-core:3.4.3")
    implementation("io.ktor:ktor-utils:3.4.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.russhwolf:multiplatform-settings:1.2.0")
    implementation("com.russhwolf:multiplatform-settings-coroutines:1.2.0")

    // Impresión térmica Bluetooth (ESC/POS)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

