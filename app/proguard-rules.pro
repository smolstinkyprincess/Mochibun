# Preserve Android entry points and system services
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.accessibilityservice.AccessibilityService

# Keep ViewModels and their constructors for reflection instantiation by ViewModelProvider
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class com.crinklecodone.mochibun.ui.LauncherViewModel {
    public <init>(android.app.Application);
}

# Keep Mochibun's Virtual Cursor & Core Services intact
-keep class com.crinklecodone.mochibun.service.VirtualCursorService { *; }
-keep class com.crinklecodone.mochibun.service.MochibunAccessibilityService { *; }

# Quickstep Recents & SystemUI interfaces
-keep class com.android.quickstep.** { *; }
-dontwarn com.android.quickstep.**
-keep class com.android.systemui.shared.** { *; }
-dontwarn com.android.systemui.shared.**

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

# coil (animated cursors)
-keep class coil.decode.GifDecoder { *; }
-keep class coil.decode.AnimatedImageDecoder { *; }
