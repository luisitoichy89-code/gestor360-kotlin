buildscript {
    repositories {
        flatDir { dirs("${projectDir}/maven-local") }
    }
    dependencies {
        classpath(fileTree("${projectDir}/maven-local") { include("*.jar") })
    }
}
