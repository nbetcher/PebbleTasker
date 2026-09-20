plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
}

// Release builds use the real signing key unless LOCAL_RELEASE_BUILD=true (then debug-signed, so a
// human can build/verify a release variant without the keystore/env). Mirrors the fork (composeApp).
val localReleaseBuild = project.findProperty("LOCAL_RELEASE_BUILD")?.toString()?.toBooleanStrictOrNull() ?: false
// Trusted CI opts in so debug artifacts can replace release installs without a
// certificate change. Local development never requires the distribution key.
val releaseSignDebug = providers.gradleProperty("RELEASE_SIGN_DEBUG")
    .map { it.toBooleanStrictOrNull() ?: throw GradleException("RELEASE_SIGN_DEBUG must be true or false") }
    .getOrElse(false)

android {
    namespace = "com.nickbether.pebbletasker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nickbether.pebbletasker"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.9.0"

        // Use the Kotlin source root (matches our package tree under src/main/kotlin).
        vectorDrawables.useSupportLibrary = true
    }

    // AGP owns the default debug keystore location and creates it when needed.
    // The plugin is *independently* signed; for distribution the
    // signer must NEVER rotate the key without re-consent (the bridge TOFU-pins the
    // plugin cert with exact contentEquals — see FINAL DESIGN §3.6 / FIX D2) and must
    // NOT set android:sharedUserId (consent requires a single-package uid).
    signingConfigs {
        if (!localReleaseBuild || releaseSignDebug) {
            // Real release key — the SAME cert the host app is signed with, which the bridge
            // TOFU-pins (FINAL DESIGN §3.6 / FIX D2). storeFile is the repo-root keystore.jks
            // (gitignored via *.jks); passwords come from the RELEASE_* env (~/.pebble-signing).
            create("release") {
                storeFile = file("../keystore.jks")
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEYSTORE_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName(if (releaseSignDebug) "release" else "debug")
            isMinifyEnabled = false
        }
        release {
            // R8 ON for release. The taskerpluginlibrary reflects over @TaskerInputField at
            // runtime to build VARIABLE_REPLACE_KEYS; proguard-rules.pro keeps *Annotation*
            // and the annotated members, or release builds silently stop substituting %vars
            // (FINAL DESIGN §0 FIX #4 / §7).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Real release signing (see signingConfigs above); LOCAL_RELEASE_BUILD=true falls back to
            // debug-signing so a release variant can still be built without the keystore/env.
            signingConfig = if (localReleaseBuild) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        viewBinding = true
        // AIDL is required: the plugin compiles the byte-identical bridge AIDL package so
        // the generated Stub/Proxy match the bridge process (FINAL DESIGN §1).
        aidl = true
        buildConfig = true
    }

    // Our sources live under src/main/kotlin (not the default src/main/java).
    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDir("src/test/kotlin")
        }
    }
}

// Kotlin 2.x: jvmTarget moved from android.kotlinOptions to the compilerOptions DSL.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // --- Unit tests: Robolectric supplies the Context the DataStore-backed cache needs, so the
    //     delivery logic (high-water, dedupe, gap synthesis) is testable on the JVM. ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.3.10")
    testImplementation("io.mockk:mockk:1.13.13")

    // --- AndroidX core / UI (AppCompat so Material3 + Theme.NeonGrid renders in config
    //     activities; FINAL DESIGN §0 FIX C2 re-implements TaskerPluginConfig on AppCompatActivity) ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // --- Neon Grid theme (composite build): provides Theme.NeonGrid + all ng_* resources AND the
    //     no-code auto-glow (its androidx.startup provider merges in and attaches the focus/error
    //     glow to every TextInputLayout). Replaces the formerly copied theme resources. ---
    implementation("com.nickbether.neongrid:theme")

    // --- Material 1.12.0: also pulled transitively by :theme (api); kept explicit for the version pin ---
    implementation("com.google.android.material:material:1.12.0")

    // --- Tasker plugin library 0.4.10 (Maven Central, Kotlin). Library manifest already
    //     ships foregroundServiceType=specialUse + canBind on its runner services; the only
    //     app-side requirement is the two justification strings (FINAL DESIGN §0 FIX C12 / §8) ---
    implementation("com.joaomgcd:taskerpluginlibrary:0.4.10")

    // --- kotlinx.serialization: JSON DTOs mirroring the bridge Contract.kt (lenient JSON) ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // --- Coroutines: all AIDL IPC is serialized on a single-threaded bridge dispatcher ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // --- DataStore: last-event-per-type cache + high-water seq/bootId (FINAL DESIGN §3.7) ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- Lifecycle: lifecycleScope + repeatOnLifecycle for the app-shell UI to observe
    //     BridgeClient.status (StateFlow) safely across STARTED/STOPPED. ---
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
