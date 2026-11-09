# Release builds are not minified (isMinifyEnabled = false); these rules apply if that changes.

# Kotlin Serialization (phone <-> watch payloads)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.github.mytv.myearthquakealert.wear.**$$serializer { *; }
-keepclassmembers class com.github.mytv.myearthquakealert.wear.** { *** Companion; }
-keepclasseswithmembers class com.github.mytv.myearthquakealert.wear.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
