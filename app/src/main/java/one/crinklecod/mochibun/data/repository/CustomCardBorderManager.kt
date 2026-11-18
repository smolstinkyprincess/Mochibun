package one.crinklecod.mochibun.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import one.crinklecod.mochibun.data.model.CardBorderStyle
import one.crinklecod.mochibun.data.model.MascotAtlasValidator
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.components.decodeImageBounds
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min

/**
 * Validated metadata for a custom 9-slice card border PNG.
 */
@Immutable
data class CardBorderSpec(
    val width: Int,
    val height: Int,
    val slicePx: Int
)

/**
 * Source state for the active custom 9-slice card border.
 */
sealed interface CustomCardBorderSource {
    data object None : CustomCardBorderSource

    data class Custom(
        val file: File,
        val spec: CardBorderSpec,
        val version: Long = 0L
    ) : CustomCardBorderSource
}

/**
 * Precomputed 9-slice source rectangles (4 corners + 4 edges; center patch is skipped) and the
 * single reusable [ImageBitmap] shared across all Mochibun cards and dialogs.
 */
@Immutable
class CustomCardBorderFrame(
    val image: ImageBitmap,
    val width: Int,
    val height: Int,
    val slicePx: Int
) {
    private val s = slicePx.coerceIn(1, min(width, height) / 2 - 1)
    private val edgeW = (width - s * 2).coerceAtLeast(1)
    private val edgeH = (height - s * 2).coerceAtLeast(1)
    private val rightX = width - s
    private val bottomY = height - s

    val cornerSrcSize = IntSize(s, s)
    val topBottomSrcSize = IntSize(edgeW, s)
    val leftRightSrcSize = IntSize(s, edgeH)

    val topLeftSrcOffset = IntOffset(0, 0)
    val topRightSrcOffset = IntOffset(rightX, 0)
    val bottomLeftSrcOffset = IntOffset(0, bottomY)
    val bottomRightSrcOffset = IntOffset(rightX, bottomY)

    val topEdgeSrcOffset = IntOffset(s, 0)
    val bottomEdgeSrcOffset = IntOffset(s, bottomY)
    val leftEdgeSrcOffset = IntOffset(0, s)
    val rightEdgeSrcOffset = IntOffset(rightX, s)
}

/**
 * Manages the user's custom 9-slice `card_border.png` frame and procedural [CardBorderStyle]:
 * - Stores validated PNG at `File(context.filesDir, "custom_card_border.png")`.
 * - Enforces strict MT6761 memory bounds: PNG signature + `inJustDecodeBounds`, `16x16` to `256x256` px,
 *   `slicePx` in `4..64` with `slicePx * 2 < min(width, height)`, and `<= 512 KB` file size.
 * - Holds at most one decoded `ARGB_8888` [ImageBitmap] in memory across the entire app.
 */
object CustomCardBorderManager {
    private const val TAG = "CustomCardBorderManager"
    const val BORDER_FILE_NAME = "custom_card_border.png"
    const val MAX_BORDER_BYTES = 512L * 1024L // 512 KB
    private const val PREFS_NAME = "mochibun_card_border"
    private const val KEY_SLICE_PX = "slice_px"
    private const val KEY_WIDTH = "width"
    private const val KEY_HEIGHT = "height"
    private const val KEY_VERSION = "version"

    /**
     * Image size as Android's decoder sees it (`inJustDecodeBounds`: no pixels decoded).
     * Swappable so JVM unit tests run without Robolectric.
     */
    internal var platformBounds: (File) -> Pair<Int, Int>? = ::decodeImageBounds

    private val _source = MutableStateFlow<CustomCardBorderSource>(CustomCardBorderSource.None)
    val source: StateFlow<CustomCardBorderSource> = _source.asStateFlow()

    private val _activeBorderStyle = MutableStateFlow(CardBorderStyle.SOLID)
    val activeBorderStyle: StateFlow<CardBorderStyle> = _activeBorderStyle.asStateFlow()

    private val loadLock = Mutex()

    @Volatile
    private var cached: Pair<CustomCardBorderSource.Custom, CustomCardBorderFrame>? = null

    fun borderFile(context: Context): File = File(context.filesDir, BORDER_FILE_NAME)

    /** Whether a custom 9-slice PNG border is currently installed and active. */
    fun isCustomBorderActive(): Boolean = _source.value is CustomCardBorderSource.Custom

    /** Active 9-slice corner inset in pixels (defaults to 16). */
    fun activeSlicePx(context: Context? = null): Int {
        val current = (_source.value as? CustomCardBorderSource.Custom)?.spec?.slicePx
        if (current != null) return current
        if (context != null) {
            return CardBorderStyle.clampSlicePx(
                prefs(context).getInt(KEY_SLICE_PX, CardBorderStyle.DEFAULT_SLICE_PX)
            )
        }
        return CardBorderStyle.DEFAULT_SLICE_PX
    }

    /** Current procedural [CardBorderStyle]. */
    fun currentBorderStyle(context: Context? = null): CardBorderStyle {
        if (context != null) {
            val fromPrefs = CardBorderStyle.fromId(PreferencesManager.getThemeBorderStyle(context))
            _activeBorderStyle.value = fromPrefs
            return fromPrefs
        }
        return _activeBorderStyle.value
    }

    /** Updates the active procedural [CardBorderStyle] and persists it in [PreferencesManager]. */
    fun setBorderStyle(context: Context, style: CardBorderStyle) {
        PreferencesManager.setThemeBorderStyle(context, style.id)
        _activeBorderStyle.value = style
    }

    /** Updates the active procedural border style by string ID (case-insensitive, defaults to `solid`). */
    fun setBorderStyle(context: Context, styleId: String?) {
        setBorderStyle(context, CardBorderStyle.fromId(styleId))
    }

    /**
     * Validates raw PNG dimensions and `slicePx` according to MT6761 safety bounds:
     * - `width` and `height` in `16..256` px
     * - `slicePx` clamped to `4..64` px
     * - `slicePx * 2 < min(width, height)` so center/edge patches are at least 1 px
     */
    fun validateDimensions(
        width: Int,
        height: Int,
        requestedSlicePx: Int = CardBorderStyle.DEFAULT_SLICE_PX
    ): Result<CardBorderSpec> {
        if (width < CardBorderStyle.MIN_IMAGE_DIM_PX || height < CardBorderStyle.MIN_IMAGE_DIM_PX) {
            return Result.failure(
                IllegalArgumentException(
                    "Border PNG must be at least ${CardBorderStyle.MIN_IMAGE_DIM_PX}×${CardBorderStyle.MIN_IMAGE_DIM_PX} px"
                )
            )
        }
        if (width > CardBorderStyle.MAX_IMAGE_DIM_PX || height > CardBorderStyle.MAX_IMAGE_DIM_PX) {
            return Result.failure(
                IllegalArgumentException(
                    "Border PNG must be at most ${CardBorderStyle.MAX_IMAGE_DIM_PX}×${CardBorderStyle.MAX_IMAGE_DIM_PX} px"
                )
            )
        }
        val clampedSlice = CardBorderStyle.clampSlicePx(requestedSlicePx)
        val minDim = min(width, height)
        if (clampedSlice * 2 >= minDim) {
            return Result.failure(
                IllegalArgumentException(
                    "9-slice corner inset (${clampedSlice}px) is too large for ${width}×${height}px image"
                )
            )
        }
        return Result.success(
            CardBorderSpec(
                width = width,
                height = height,
                slicePx = clampedSlice
            )
        )
    }

    /**
     * Restores the saved custom border PNG and procedural border style at startup.
     */
    fun restore(context: Context) {
        _activeBorderStyle.value = CardBorderStyle.fromId(PreferencesManager.getThemeBorderStyle(context))
        val file = borderFile(context)
        if (!file.isFile) {
            _source.value = CustomCardBorderSource.None
            cached = null
            return
        }
        val p = prefs(context)
        val savedSlice = CardBorderStyle.clampSlicePx(
            p.getInt(KEY_SLICE_PX, CardBorderStyle.DEFAULT_SLICE_PX)
        )
        val pngSize = MascotAtlasValidator.readPngSize(file)
        if (pngSize == null) {
            Log.w(TAG, "Saved custom card border unreadable; clearing")
            resetCustomBorder(context)
            return
        }
        val (w, h) = pngSize
        val effectiveSlice = adjustOrValidateSlice(w, h, savedSlice)
        val spec = validateDimensions(w, h, effectiveSlice).getOrNull()
        if (spec == null) {
            Log.w(TAG, "Saved custom card border failed bounds check; clearing")
            resetCustomBorder(context)
            return
        }
        val version = p.getLong(KEY_VERSION, 1L)
        _source.value = CustomCardBorderSource.Custom(file, spec, version)
    }

    /**
     * Imports a custom 9-slice PNG from a SAF [Uri].
     */
    fun importPng(
        context: Context,
        uri: Uri,
        slicePx: Int = CardBorderStyle.DEFAULT_SLICE_PX
    ): Result<CardBorderSpec> {
        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        } ?: return Result.failure(IllegalArgumentException("Couldn't open selected PNG file"))
        return stream.use { importPng(context, it, slicePx, autoAdjustSliceForSmallImage = true) }
    }

    /**
     * Imports a custom 9-slice PNG from an [InputStream] (bounded to [MAX_BORDER_BYTES]).
     */
    fun importPng(
        context: Context,
        input: InputStream,
        slicePx: Int = CardBorderStyle.DEFAULT_SLICE_PX,
        autoAdjustSliceForSmallImage: Boolean = false
    ): Result<CardBorderSpec> {
        val cacheDir = context.cacheDir ?: context.filesDir
        cacheDir.mkdirs()
        val staged = File.createTempFile("card_border_pick_", ".png", cacheDir)
        return try {
            val withinLimit = FileOutputStream(staged).use { out ->
                MascotAtlasValidator.copyAtMost(input, out, MAX_BORDER_BYTES)
            }
            if (!withinLimit) {
                Result.failure(IllegalArgumentException("Border PNG must be 512 KB or smaller"))
            } else {
                installStaged(context, staged, slicePx, autoAdjustSliceForSmallImage)
            }
        } finally {
            staged.delete()
        }
    }

    /**
     * Installs a custom 9-slice PNG from in-memory bytes (e.g. extracted from a `.mochibun` bundle).
     */
    fun installFromBytes(
        context: Context,
        bytes: ByteArray,
        slicePx: Int = CardBorderStyle.DEFAULT_SLICE_PX
    ): Result<CardBorderSpec> {
        if (bytes.isEmpty() || bytes.size > MAX_BORDER_BYTES) {
            return Result.failure(IllegalArgumentException("Border PNG must be 1 B – 512 KB"))
        }
        return ByteArrayInputStream(bytes).use { stream ->
            importPng(context, stream, slicePx, autoAdjustSliceForSmallImage = false)
        }
    }

    /**
     * Validates a staged PNG file using PNG IHDR header + `platformBounds` (`inJustDecodeBounds`)
     * before copying to `filesDir/custom_card_border.png` and updating the reactive source.
     */
    fun installStaged(
        context: Context,
        staged: File,
        requestedSlicePx: Int = CardBorderStyle.DEFAULT_SLICE_PX,
        autoAdjustSliceForSmallImage: Boolean = false
    ): Result<CardBorderSpec> {
        if (!staged.isFile || staged.length() <= 0L || staged.length() > MAX_BORDER_BYTES) {
            return Result.failure(IllegalArgumentException("Border PNG must be 512 KB or smaller"))
        }
        val pngSize = MascotAtlasValidator.readPngSize(staged)
            ?: return Result.failure(IllegalArgumentException("That file isn't a valid PNG image"))
        val decoderSize = platformBounds(staged)
        if (decoderSize != pngSize) {
            return Result.failure(IllegalArgumentException("Couldn't decode PNG image bounds"))
        }
        val (w, h) = pngSize
        val sliceToUse = if (autoAdjustSliceForSmallImage) {
            adjustOrValidateSlice(w, h, requestedSlicePx)
        } else {
            requestedSlicePx
        }
        val spec = validateDimensions(w, h, sliceToUse).getOrElse { return Result.failure(it) }

        return try {
            context.filesDir.mkdirs()
            val target = borderFile(context)
            val tmp = File(context.filesDir, "$BORDER_FILE_NAME.tmp")
            staged.copyTo(tmp, overwrite = true)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            val version = System.currentTimeMillis()
            prefs(context).edit()
                .putInt(KEY_SLICE_PX, spec.slicePx)
                .putInt(KEY_WIDTH, spec.width)
                .putInt(KEY_HEIGHT, spec.height)
                .putLong(KEY_VERSION, version)
                .apply()
            PreferencesManager.getPrefs(context).edit()
                .putInt(PreferencesManager.KEY_CUSTOM_CARD_BORDER_SLICE_PX, spec.slicePx)
                .apply()
            cached = null
            _source.value = CustomCardBorderSource.Custom(target, spec, version)
            Result.success(spec)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to install custom card border PNG", e)
            Result.failure(IllegalArgumentException("Couldn't save custom card border"))
        }
    }

    /**
     * Clears any custom 9-slice PNG border and releases the cached [ImageBitmap] reference.
     */
    fun resetCustomBorder(context: Context) {
        cached = null
        _source.value = CustomCardBorderSource.None
        borderFile(context).delete()
        prefs(context).edit()
            .remove(KEY_SLICE_PX)
            .remove(KEY_WIDTH)
            .remove(KEY_HEIGHT)
            .remove(KEY_VERSION)
            .apply()
        PreferencesManager.getPrefs(context).edit()
            .remove(PreferencesManager.KEY_CUSTOM_CARD_BORDER_SLICE_PX)
            .apply()
    }

    /** Returns the already-decoded [CustomCardBorderFrame] for [source] without disk I/O. */
    fun cachedFrame(source: CustomCardBorderSource): CustomCardBorderFrame? {
        val custom = source as? CustomCardBorderSource.Custom ?: return null
        return cached?.takeIf { it.first == custom }?.second
    }

    /**
     * Decodes (or reuses) the single shared `ARGB_8888` [CustomCardBorderFrame] on [Dispatchers.IO].
     */
    suspend fun loadFrame(
        context: Context,
        source: CustomCardBorderSource
    ): CustomCardBorderFrame? = withContext(Dispatchers.IO) {
        val custom = source as? CustomCardBorderSource.Custom ?: run {
            cached = null
            return@withContext null
        }
        loadLock.withLock {
            cachedFrame(custom)?.let { return@withLock it }
            val decoded = decodeCustomFrame(custom)
            if (decoded != null) {
                cached = custom to decoded
            } else {
                Log.w(TAG, "Custom card border unreadable on decode; falling back to procedural border")
                cached = null
            }
            decoded
        }
    }

    private fun decodeCustomFrame(custom: CustomCardBorderSource.Custom): CustomCardBorderFrame? = try {
        if (!custom.file.isFile) {
            null
        } else {
            val bounds = decodeImageBounds(custom.file) ?: (0 to 0)
            val (w, h) = bounds
            val validSpec = validateDimensions(w, h, custom.spec.slicePx).getOrNull()
            if (validSpec == null) {
                Log.w(TAG, "Custom card border ${w}x$h failed bounds check before decode")
                null
            } else {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeFile(custom.file.path, opts)
                if (bmp == null) {
                    null
                } else if (bmp.width != validSpec.width || bmp.height != validSpec.height) {
                    bmp.recycle()
                    null
                } else {
                    CustomCardBorderFrame(
                        image = bmp.asImageBitmap(),
                        width = bmp.width,
                        height = bmp.height,
                        slicePx = validSpec.slicePx
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to decode custom card border", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "OOM decoding custom card border", e)
        null
    }

    private fun adjustOrValidateSlice(width: Int, height: Int, requestedSlicePx: Int): Int {
        val clamped = CardBorderStyle.clampSlicePx(requestedSlicePx)
        val maxAllowed = ((min(width, height) - 1) / 2).coerceAtLeast(CardBorderStyle.MIN_SLICE_PX)
        return clamped.coerceAtMost(maxAllowed)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * Remembers the single app-wide [CustomCardBorderFrame] when a custom 9-slice `card_border.png` is active,
 * or `null` when procedural borders should be rendered.
 */
@Composable
fun rememberCustomCardBorderFrame(): CustomCardBorderFrame? {
    val context = LocalContext.current
    val source by CustomCardBorderManager.source.collectAsState()
    val frame by produceState(CustomCardBorderManager.cachedFrame(source), source) {
        value = if (source is CustomCardBorderSource.Custom) {
            CustomCardBorderManager.loadFrame(context.applicationContext, source)
        } else {
            null
        }
    }
    return if (source is CustomCardBorderSource.Custom) frame else null
}
