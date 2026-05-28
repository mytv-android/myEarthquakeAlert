# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.github.mytv.myearthquakealert.**$$serializer { *; }
-keepclassmembers class com.github.mytv.myearthquakealert.** { *** Companion; }
-keepclasseswithmembers class com.github.mytv.myearthquakealert.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
}
