# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================
# REMUSIC PROJECT SPECIFIC RULES
# ============================================

# Keep all data models to prevent serialization issues
-keep class com.example.remusic.data.model.** { *; }

# Keep Kotlinx Serialization annotations and runtime
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Serializers
-keep,includedescriptorclasses class com.example.remusic.**$$serializer { *; }
-keepclassmembers class com.example.remusic.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.remusic.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Supabase SDK
-keep class io.github.jan.supabase.** { *; }
-keep interface io.github.jan.supabase.** { *; }

# Ktor (used by Supabase)
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }

# Gson (if used)
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Keep generic signature of Call, Response (Retrofit)
-keepattributes Signature
-keep class retrofit2.** { *; }

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**