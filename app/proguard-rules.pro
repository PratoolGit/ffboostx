# FF BoostX release rules.
#
# Compose, AndroidX and kotlinx-coroutines all ship consumer ProGuard rules, so
# only this app's own entry points and reflective surfaces need keeping.

# Activities are referenced from the manifest, not from code.
-keep public class com.ffboostx.MainActivity { public *; }

# Enum valueOf/values are used when restoring settings from SharedPreferences.
-keepclassmembers enum com.ffboostx.core.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Strip debug logging from the release binary. Every call site is already behind
# BuildConfig.DEBUG, but this removes any that slip through a library.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep line numbers for readable crash reports while still obfuscating names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
