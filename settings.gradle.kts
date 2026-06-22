pluginManagement {
    repositories {
        maven { url = uri("https://mirrors.aliyun.com/google/") }
        maven { url = uri("https://mirrors.aliyun.com/repository/public/") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://mirrors.aliyun.com/google/") }
        maven { url = uri("https://mirrors.aliyun.com/repository/public/") }
        mavenCentral()
    }
}

rootProject.name = "Gestor360"
include(":app")
