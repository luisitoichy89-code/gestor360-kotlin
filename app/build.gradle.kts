plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// El force() a kotlin-metadata-jvm:2.3.0-Beta1 se quitó al migrar de kapt a KSP:
// era un parche para el mismo conflicto kapt + plugin de serialización bajo K2.
// Si algo de metadata vuelve a fallar, es la primera línea a revisar.

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
    val releaseStoreFile = project.findProperty("RELEASE_STORE_FILE") as String?
    val releaseStorePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
    val releaseKeyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
    val releaseKeyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?

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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Si no hay keystore configurado (ej. build de CI sin secretos), no rompe el build;
            // simplemente no habrá signingConfig y el APK quedará sin firmar.
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
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
    implementation("com.google.firebase:firebase-messaging-ktx")

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
    implementation("com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
