// Root build file for the Pebble x Tasker plugin (standalone Gradle project).
// Pins the toolchain to match the NeonGrid template: AGP 8.13.2 on Gradle 8.14.4.
// Plugin versions are declared here `apply false`; the :app module applies them.
//
// Kotlin 2.3.10 — matches the forked Pebble app (Watch App/gradle/libs.versions.toml) and is
// already in the local Gradle cache, so it resolves with no network. (The scaffolder wrongly
// assumed 2.3.10 didn't exist and downgraded to 2.0.21, which isn't cached and can't be fetched.)
plugins {
    id("com.android.application") version "8.13.2" apply false
    kotlin("android") version "2.3.10" apply false
    kotlin("plugin.serialization") version "2.3.10" apply false
}
