# Keep Scout public API + JNI entry points
-keep class io.base14.scout.android.Scout { *; }
-keep class io.base14.scout.android.ScoutConfig { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
