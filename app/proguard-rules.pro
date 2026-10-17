# ffmpeg-kit uses JNI; keep its public API surface.
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smart-exception.** { *; }
-dontwarn com.arthenica.**

# Native entry points resolved reflectively by the FFmpegKit JNI bridge.
-keepclasseswithmembernames class * {
    native <methods>;
}
