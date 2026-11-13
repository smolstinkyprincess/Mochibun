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
-keep class one.crinklecod.mochibun.service.** { *; }
-keep class one.crinklecod.mochibun.xposed.** { *; }
-keep class one.crinklecod.mochibun.data.repository.HardwareRemapManager { *; }
-keep class one.crinklecod.mochibun.core.util.MochibunSystemActions { *; }

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
-keep class one.crinklecod.mochibun.data.model.MochibunBundleManifest { *; }
-keep class one.crinklecod.mochibun.data.model.BundleGreetingConfig { *; }
-keep class one.crinklecod.mochibun.data.model.BundlePaletteConfig { *; }
-keep class one.crinklecod.mochibun.data.model.BundleCursorConfig { *; }
-keep class one.crinklecod.mochibun.data.model.YamlTheme { *; }
-keep class one.crinklecod.mochibun.data.model.YamlThemeColors { *; }
-keep class one.crinklecod.mochibun.data.model.YamlThemeDecorations { *; }
-keep class one.crinklecod.mochibun.data.repository.BackupPreviewSummary { *; }

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
-keep class one.crinklecod.mochibun.ui.theme.SystemUiThemeBridge { *; }
