pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // taskerpluginlibrary 0.4.10 is published to Maven Central (com.joaomgcd).
        // No JitPack needed for this version.
    }
}

rootProject.name = "pebble-tasker"
include(":app")
