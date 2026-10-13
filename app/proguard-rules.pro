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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ONNX Runtime Mobile
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Vosk + JNA (offline wake word)
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# ── Phase 5: rules used when building with -PMYRA_MINIFY=true ─────────────────
# Shrink only, never rename: keeps reflection / JSON field names / stack traces intact.
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# WebView JavaScript bridges
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Services / receivers / accessibility are referenced from the manifest (kept automatically); keep entry points explicitly
-keep class com.soltini.app.agent.SoltiniAccessibilityService { *; }
-keep class com.soltini.app.notifications.SoltiniNotificationListener { *; }

# Room, WorkManager workers, and anything constructed by reflection
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class * extends androidx.room.RoomDatabase { *; }

# org.json / coroutines / OkHttp ship their own consumer rules
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
