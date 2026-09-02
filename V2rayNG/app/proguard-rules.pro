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

-keepattributes SourceFile,LineNumberTable

# --- libv2ray (gomobile Go core) JNI callback bridge ---
# app/libs/libv2ray.aar already ships its own consumer rule keeping the go.**/
# libv2ray.** side of this bridge, but that does NOT cover our own classes that
# implement those Go-defined interfaces (CoreServiceManager.CoreCallback :
# CoreCallbackHandler, CoreServiceManager.XrayProcessFinder : ProcessFinder) —
# the Go runtime calls back into these by exact class/method signature via
# JNI reflection, so renaming or stripping them breaks the tunnel silently
# (or crashes) without R8 ever raising an error.
-keep class * implements libv2ray.** { *; }
-keepclassmembers class * implements libv2ray.** { *; }

# --- hev-socks5-tunnel native JNI bridge (TProxyService) ---
# libhev-socks5-tunnel.so's JNI_OnLoad returned JNI_ERR after enabling R8 even
# with the AGP-default "keep classes with native methods" rule in place —
# meaning the native side does its own additional FindClass/RegisterNatives
# lookups beyond the declared `external fun`s, against names R8 was still
# free to rename. Keep the whole service package verbatim rather than guess
# exactly which lookup broke; it is small and not worth shrinking.
-keep class com.v2ray.ang.service.** { *; }
-keepclassmembers class com.v2ray.ang.service.** { *; }

# --- Gson-reflected model classes ---
# com.v2ray.ang.dto.* (V2rayConfig and friends) is deserialized/serialized by
# field name via Gson reflection, not read field-by-field in code R8's static
# analysis can see — obfuscating or shrinking these fields produces silently
# wrong/null JSON instead of a build-time error.
-keep class com.v2ray.ang.dto.** { *; }
-keepclassmembers class com.v2ray.ang.dto.** { *; }