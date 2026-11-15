package one.crinklecod.mochibun.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val bundleJsonFormat = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

@Serializable
data class MochibunBundleManifest(
    val bundleVersion: Int = 1,
    val bundleName: String,
    val author: String = "Mochi User",
    val createdAtEpochSec: Long,
    val greeting: BundleGreetingConfig,
    val palette: BundlePaletteConfig,
    val cursor: BundleCursorConfig,
    val hasWallpaper: Boolean = false,
    val hasCustomCursor: Boolean = false,
    // Custom mascot atlas (zip entry "mascot_atlas.png"). Older app versions ignore both fields
    // (ignoreUnknownKeys) and skip the unknown entry, so these bundles still import there.
    val hasCustomMascot: Boolean = false,
    val mascot: BundleMascotConfig? = null
)

/**
 * Layout of a bundled mascot atlas. There is deliberately no file name: the atlas is always the
 * zip entry named exactly `mascot_atlas.png`. Slot keys are lowercase [MascotAnimSlot] names
 * ("idle", "talk", ...); unknown keys are ignored and missing slots fall back to "idle". With no
 * slots at all, every slot uses its default row and as many frames as fit the width.
 */
@Serializable
data class BundleMascotConfig(
    val name: String = "Mochibunny",
    val frameWidth: Int,
    val frameHeight: Int,
    val slots: Map<String, BundleMascotSlot> = emptyMap()
)

@Serializable
data class BundleMascotSlot(
    val row: Int,
    val frames: Int
)

@Serializable
data class BundleGreetingConfig(
    val userName: String,
    val showSparkles: Boolean = true
)

@Serializable
data class BundlePaletteConfig(
    val activePresetId: String,
    val isCustom: Boolean,
    val bgColorHex: String,
    val cardColorHex: String,
    val greetingColorHex: String,
    val actionColorHex: String,
    val cardRoundnessDp: Float
)

@Serializable
data class BundleCursorConfig(
    // baseVelocity / accelerationMulti are legacy physics values the cursor no longer uses;
    // they stay required so older app versions can still read new bundles
    val baseVelocity: Float,
    val accelerationMulti: Float,
    val pointerSizeDp: Float,
    val clickMethod: String,
    val hotspot: String,
    // Kinetic physics; null in bundles made before these settings existed
    val tapStepPx: Int? = null,
    val maxSpeedPxPerSec: Int? = null,
    val accelRampMs: Int? = null
)
