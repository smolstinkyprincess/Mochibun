package com.crinklecodone.mochibun.data.model

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
    val hasCustomCursor: Boolean = false
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
