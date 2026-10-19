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
