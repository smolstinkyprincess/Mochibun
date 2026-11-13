# Preserve Android entry points and system services
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver { *; }
-keep public class * extends android.inputmethodservice.InputMethodService

# Keep ViewModels and their constructors for reflection instantiation by ViewModelProvider
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Mochibun's services (VirtualCursorService, IME, notification listener) and LSPosed hooks
# (MochibunKeyHook, MochibunSystemUiHook, ActiveHookTheme) intact
-keep class com.crinklecodone.mochibun.service.** { *; }
-keep class com.crinklecodone.mochibun.xposed.** { *; }
-keep class com.crinklecodone.mochibun.data.repository.HardwareRemapManager { *; }
-keep class com.crinklecodone.mochibun.core.util.MochibunSystemActions { *; }

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
-keep class com.crinklecodone.mochibun.data.model.MochibunBundleManifest { *; }
-keep class com.crinklecodone.mochibun.data.model.BundleGreetingConfig { *; }
-keep class com.crinklecodone.mochibun.data.model.BundlePaletteConfig { *; }
-keep class com.crinklecodone.mochibun.data.model.BundleCursorConfig { *; }
-keep class com.crinklecodone.mochibun.data.model.YamlTheme { *; }
-keep class com.crinklecodone.mochibun.data.model.YamlThemeColors { *; }
-keep class com.crinklecodone.mochibun.data.model.YamlThemeDecorations { *; }
-keep class com.crinklecodone.mochibun.data.repository.BackupPreviewSummary { *; }

# Annotations, generics and inner classes are needed by serialization and by reflection
# across processes (SystemUI theme bridge, LSPosed hooks)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# kaml (YAML parser)
-dontwarn com.charleskorn.kaml.**
-keep class com.charleskorn.kaml.** { *; }

# coil (animated cursors)
-keep class coil.decode.GifDecoder { *; }
-keep class coil.decode.AnimatedImageDecoder { *; }

# Preserve LSPosed / Xposed Entry Points and Hook Classes
-keep interface de.robv.android.xposed.** { *; }
-keep class de.robv.android.xposed.** { *; }

# Preserve SystemUI Theme Bridge
-keep class com.crinklecodone.mochibun.ui.theme.SystemUiThemeBridge { *; }
