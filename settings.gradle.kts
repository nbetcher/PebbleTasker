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
// A real checkout can live elsewhere (CI and workstations need not share this directory layout).
// Example: -PneonGridDir=C:/src/neon_grid_theme/android
val neonGridDir = providers.gradleProperty("neonGridDir")
    .orElse(providers.environmentVariable("NEON_GRID_ANDROID_DIR"))
    .getOrElse("../../NeonGrid/android")
require(file("$neonGridDir/theme/build.gradle.kts").isFile) {
    "Neon Grid theme source is missing. Clone https://github.com/nbetcher/neon_grid_theme " +
        "and pass -PneonGridDir=<checkout>/android (or NEON_GRID_ANDROID_DIR)."
}
includeBuild(neonGridDir) {
    dependencySubstitution {
        substitute(module("com.nickbether.neongrid:theme")).using(project(":theme"))
    }
}
