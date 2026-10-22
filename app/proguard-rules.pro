# Preserve Android entry points and system services
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.accessibilityservice.AccessibilityService

# Keep Mochibun's Virtual Cursor & Core Services intact
-keep class com.crinklecodone.mochibun.service.VirtualCursorService { *; }
-keep class com.crinklecodone.mochibun.service.MochibunAccessibilityService { *; }

# Preserve Compose runtime lambdas & inspection
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-dontwarn androidx.compose.**

# Keep reflection hooks used for launcher shortcuts and package querying
-keepclassmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# libsu: preserve shell internals and reflection entry points
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# kotlinx.serialization
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    <fields>;
}
-keepclassmembers class * {
    static ** Companion;
}
-keepclassmembers class * {
    synthetic <init>(...);
}
-keepattributes *Annotation*, InnerClasses

# kaml (YAML parser)
-dontwarn com.charleskorn.kaml.**
-keep class com.charleskorn.kaml.** { *; }
