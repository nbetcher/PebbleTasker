# ── Pebble x Tasker plugin — R8/ProGuard rules ──────────────────────────────
# CRITICAL: the taskerpluginlibrary reflects over @TaskerInputField at runtime to build
# VARIABLE_REPLACE_KEYS. Without *Annotation* + RuntimeVisible*Annotations kept, release
# builds SILENTLY stop substituting %vars (FINAL DESIGN §0 FIX #4 / §7).
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations

# ── Tasker plugin library: keep the whole library + reflectively-instantiated types ──
-keep class com.joaomgcd.taskerpluginlibrary.** { *; }

# Keep every @TaskerInputRoot / @TaskerOutputObject class and ALL their members — the
# library reflectively instantiates inputs/outputs with generated partial constructors
# (@JvmOverloads) and reads annotated fields/getters.
-keep @com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot class * { *; }
-keep @com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject class * { *; }

# Keep individual annotated members even if their class isn't matched above.
-keepclassmembers class ** {
    @com.joaomgcd.taskerpluginlibrary.input.TaskerInputField *;
    @com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable *;
}

# Keep all our plugin runner/helper/config classes referenced by the manifest + reflection.
-keep class com.nickbether.pebbletasker.tasker.** { *; }

# ── AIDL Stub/Proxy: byte-identical bridge package must survive shrinking ──
-keep class coredevices.coreapp.automation.** { *; }

# ── kotlinx.serialization: keep @Serializable classes + generated serializers ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclasseswithmembers class com.nickbether.pebbletasker.bridge.dto.** {
    *** Companion;
}
-keepclassmembers class com.nickbether.pebbletasker.bridge.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nickbether.pebbletasker.bridge.dto.**$$serializer { *; }

# CachedEvent is @Serializable but lives in cache/ (persisted in the EventCache DataStore blob).
-keepclasseswithmembers class com.nickbether.pebbletasker.cache.CachedEvent {
    *** Companion;
}
-keepclassmembers class com.nickbether.pebbletasker.cache.CachedEvent {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nickbether.pebbletasker.cache.CachedEvent$$serializer { *; }

# PbVars.ALL reflects over its own String constant fields to build the relevant-variable list.
-keepclassmembers class com.nickbether.pebbletasker.tasker.vars.PbVars {
    java.lang.String *;
}

# ── Coroutines ──
-dontwarn kotlinx.coroutines.**
