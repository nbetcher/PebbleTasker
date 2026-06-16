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

// Consume the Neon Grid theme as a live composite build (the NeonGrid checkout sits beside this
// repo on disk at ../../NeonGrid/android). This replaces the previously COPIED theme resources with
// a single source of truth and brings the no-code auto-glow along automatically. The explicit
// substitution maps the module coordinate to the included :theme project (which declares no group).
includeBuild("../../NeonGrid/android") {
    dependencySubstitution {
        substitute(module("com.nickbether.neongrid:theme")).using(project(":theme"))
    }
}
