android {
    namespace = "com.gestor360.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.gestor360.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(fileTree("${rootProject.projectDir}/maven-local") { include("*.jar") })
}
