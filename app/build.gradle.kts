plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.nickbether.pebbletasker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nickbether.pebbletasker"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Use the Kotlin source root (matches our package tree under src/main/kotlin).
        vectorDrawables.useSupportLibrary = true
    }

    // Debug signing config so the standalone project produces an installable debug APK
    // without external key setup. The plugin is *independently* signed; for release the
    // signer must NEVER rotate the key without re-consent (the bridge TOFU-pins the
    // plugin cert with exact contentEquals — see FINAL DESIGN §3.6 / FIX D2) and must
    // NOT set android:sharedUserId (consent requires a single-package uid).
    signingConfigs {
        getByName("debug") {
            // Standard AGP debug keystore (~/.android/debug.keystore) is used by default;
            // this block exists so a release-style flow can point at it explicitly if needed.
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
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
            // Debug-signed for now so a human can build/verify a release variant in WSL
            // without provisioning a release keystore. Swap to a real release signingConfig
            // before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    }
}

// Kotlin 2.x: jvmTarget moved from android.kotlinOptions to the compilerOptions DSL.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // --- AndroidX core / UI (AppCompat so Material3 + Theme.NeonGrid renders in config
    //     activities; FINAL DESIGN §0 FIX C2 re-implements TaskerPluginConfig on AppCompatActivity) ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // --- Material 1.12.0: required by the copied Neon Grid theme (Material3 widgets/attrs) ---
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
