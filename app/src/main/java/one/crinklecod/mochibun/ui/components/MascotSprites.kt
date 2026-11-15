package one.crinklecod.mochibun.ui.components

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import one.crinklecod.mochibun.R
import one.crinklecod.mochibun.isOneGbClassDevice
import one.crinklecod.mochibun.pet.PetMood
import java.io.File

/** Where one animation slot lives in an atlas: its row and how many frames (columns) it has. */
@Immutable
data class MascotSlotLayout(val row: Int, val frames: Int)

/**
 * The frame grid of a mascot atlas: one row per [MascotAnimSlot], frames left to right.
 * Sizes are in the pixels of the image they describe.
 */
@Immutable
data class MascotAtlasLayout(
    val frameWidth: Int,
    val frameHeight: Int,
    val slots: Map<MascotAnimSlot, MascotSlotLayout>
) {
    /** The same grid for an image decoded with `inSampleSize = [sampleSize]`. */
    fun downsampled(sampleSize: Int): MascotAtlasLayout =
        copy(frameWidth = frameWidth / sampleSize, frameHeight = frameHeight / sampleSize)

    /** Whether every slot's frames fit inside an image of [width] x [height]. */
    fun fitsIn(width: Int, height: Int): Boolean =
        frameWidth > 0 && frameHeight > 0 && slots.values.all { s ->
            s.row >= 0 && s.frames >= 1 &&
                (s.row + 1) * frameHeight <= height &&
                s.frames * frameWidth <= width
        }

    companion object {
        /** Every slot on its [MascotAnimSlot.defaultRow] with [frames] frames. */
        fun uniform(frameWidth: Int, frameHeight: Int, frames: Int) = MascotAtlasLayout(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            slots = MascotAnimSlot.entries.associateWith { MascotSlotLayout(it.defaultRow, frames) }
        )

        /** `R.drawable.mochibunny_atlas`: 1024 x 2048, 8 rows x 4 frames of 256 x 256. */
        val BUILT_IN = uniform(frameWidth = 256, frameHeight = 256, frames = 4)
    }
}

/** Where the mascot's art comes from. Built-in and custom atlases render the same way. */
sealed interface MascotSpriteSource {
    /** The bundled Mochibunny atlas. */
    data object BuiltIn : MascotSpriteSource

    /**
     * A validated user atlas PNG in the app's files dir. Falls back to [BuiltIn] if unreadable.
     * [version] changes on every install, so re-picking into the same file still reloads.
     */
    data class Custom(val file: File, val layout: MascotAtlasLayout, val version: Long = 0L) : MascotSpriteSource
}

/** A decoded atlas ready to draw. [layout] is in the decoded image's pixels. */
class MascotAtlas(val image: ImageBitmap, val layout: MascotAtlasLayout)

/** One frame to draw: where it is in the atlas. */
@Immutable
data class MascotFrame(val srcOffset: IntOffset, val srcSize: IntSize)

/** Frame-picking rules. Pure (no Android), so they are JVM-tested. */
object MascotAtlasMath {
    /**
     * The shared stepped clock wraps here. It is a multiple of every frame count from 1 to 16,
     * so looping animations never jump when the clock wraps.
     */
    const val TICK_WRAP = 720_720

    /** A reaction plays its row this many times before she returns to her mood row (4 frames: 2 s). */
    const val REACTION_LOOPS = 2

    /** How long a reaction lasts on the 4 Hz clock for a 4-frame row: 2 loops = 8 ticks = 2 s. */
    const val REACTION_MS = 4L * REACTION_LOOPS * 250L

    /** The resting slot for her mood. */
    fun moodSlot(mood: PetMood): MascotAnimSlot = when (mood) {
        PetMood.HAPPY -> MascotAnimSlot.HAPPY
        PetMood.CONTENT -> MascotAnimSlot.IDLE
        PetMood.HUNGRY -> MascotAnimSlot.HUNGRY
        PetMood.SULKY -> MascotAnimSlot.SULKY
        PetMood.SLEEPY -> MascotAnimSlot.SLEEPY
    }

    /** Ticks since [startTick], allowing for the clock wrapping at [TICK_WRAP]. */
    fun ticksSince(tick: Int, startTick: Int): Int = Math.floorMod(tick - startTick, TICK_WRAP)

    /**
     * Which slot and frame to show at [tick]:
     * - A [reaction] plays its frames [REACTION_LOOPS] times from [reactionStartTick], then she
     *   returns to [base].
     * - [base] loops with the clock.
     * - [staticFrames] (low-RAM phones) always shows the first frame of whichever slot is current.
     * A slot the layout lacks falls back to IDLE (and then to row 0, one frame).
     */
    fun frameFor(
        layout: MascotAtlasLayout,
        base: MascotAnimSlot,
        reaction: MascotAnimSlot?,
        reactionStartTick: Int,
        tick: Int,
        staticFrames: Boolean = false
    ): MascotFrame {
        if (reaction != null) {
            val reactionLayout = slotLayout(layout, reaction)
            val elapsed = ticksSince(tick, reactionStartTick)
            if (elapsed < reactionLayout.frames * REACTION_LOOPS) {
                return frameRect(layout, reactionLayout, if (staticFrames) 0 else elapsed % reactionLayout.frames)
            }
        }
        val baseLayout = slotLayout(layout, base)
        return frameRect(layout, baseLayout, if (staticFrames) 0 else Math.floorMod(tick, baseLayout.frames))
    }

    fun slotLayout(layout: MascotAtlasLayout, slot: MascotAnimSlot): MascotSlotLayout =
        layout.slots[slot] ?: layout.slots[MascotAnimSlot.IDLE] ?: MascotSlotLayout(row = 0, frames = 1)

    /** The source rectangle of frame [frameIndex] (wrapped into range) of [slot]. */
    fun frameRect(layout: MascotAtlasLayout, slot: MascotSlotLayout, frameIndex: Int): MascotFrame {
        val col = Math.floorMod(frameIndex, slot.frames.coerceAtLeast(1))
        return MascotFrame(
            srcOffset = IntOffset(col * layout.frameWidth, slot.row * layout.frameHeight),
            srcSize = IntSize(layout.frameWidth, layout.frameHeight)
        )
    }
}

/**
 * Holds the single decoded mascot atlas for the whole app (Home, widgets, dialogs all share it).
 * Decoding runs on [Dispatchers.IO]; switching source drops the old bitmap reference.
 */
object MascotSpriteStore {
    private const val TAG = "MascotSpriteStore"

    /** Built-in atlas decode: 1024 x 2048 -> 512 x 1024 (128 px frames, ~2 MB instead of 8 MB). */
    const val BUILT_IN_SAMPLE_SIZE = 2

    /** Largest custom atlas side (matches MascotAtlasValidator). */
    private const val MAX_CUSTOM_ATLAS_PX = 2048

    private val _source = MutableStateFlow<MascotSpriteSource>(MascotSpriteSource.BuiltIn)
    val source: StateFlow<MascotSpriteSource> = _source.asStateFlow()

    private val loadLock = Mutex()

    @Volatile
    private var cached: Pair<MascotSpriteSource, MascotAtlas>? = null

    @Volatile
    private var staticFramesOnly: Boolean? = null

    /** Selects the active art. The old atlas is released when the new one loads. */
    fun setSource(source: MascotSpriteSource) {
        _source.value = source
    }

    /** The already-decoded atlas for [source], if any (no disk access). */
    fun cachedAtlas(source: MascotSpriteSource): MascotAtlas? =
        cached?.takeIf { it.first == source }?.second

    /**
     * ~1 GB phones show the first frame only, matching how they skip animated GIFs elsewhere
     * (see MochibunApp's image loader).
     */
    fun isStaticFramesOnly(context: Context): Boolean {
        staticFramesOnly?.let { return it }
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        return isOneGbClassDevice(info.totalMem).also { staticFramesOnly = it }
    }

    /** Decodes (or reuses) the atlas for [source]. A broken custom atlas falls back to built-in. */
    suspend fun load(context: Context, source: MascotSpriteSource): MascotAtlas? = withContext(Dispatchers.IO) {
        loadLock.withLock {
            cachedAtlas(source)?.let { return@withLock it }
            val atlas = decode(context, source)
                ?: if (source != MascotSpriteSource.BuiltIn) {
                    Log.w(TAG, "Custom mascot atlas unreadable, using built-in Mochibunny")
                    cachedAtlas(MascotSpriteSource.BuiltIn) ?: decode(context, MascotSpriteSource.BuiltIn)
                } else {
                    null
                }
            // Only one atlas is ever held: replacing the pair drops the previous bitmap
            if (atlas != null) cached = source to atlas
            atlas
        }
    }

    private fun decode(context: Context, source: MascotSpriteSource): MascotAtlas? = try {
        when (source) {
            MascotSpriteSource.BuiltIn -> {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = BUILT_IN_SAMPLE_SIZE
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeResource(context.resources, R.drawable.mochibunny_atlas, opts)
                    ?.let { toAtlas(it, MascotAtlasLayout.BUILT_IN.downsampled(BUILT_IN_SAMPLE_SIZE)) }
            }
            is MascotSpriteSource.Custom -> {
                if (!source.file.isFile) {
                    null
                } else {
                    // Bounds first: never decode pixels of something oversized or off-layout
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(source.file.path, bounds)
                    val w = bounds.outWidth
                    val h = bounds.outHeight
                    if (w <= 0 || h <= 0 || w > MAX_CUSTOM_ATLAS_PX || h > MAX_CUSTOM_ATLAS_PX ||
                        !source.layout.fitsIn(w, h)
                    ) {
                        Log.w(TAG, "Custom atlas ${w}x$h rejected before decoding")
                        null
                    } else {
                        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                        BitmapFactory.decodeFile(source.file.path, opts)?.let { toAtlas(it, source.layout) }
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to decode mascot atlas $source", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Out of memory decoding mascot atlas $source", e)
        null
    }

    /** Rejects (and recycles) a bitmap whose frames don't fit the layout. */
    private fun toAtlas(bitmap: Bitmap, layout: MascotAtlasLayout): MascotAtlas? {
        if (!layout.fitsIn(bitmap.width, bitmap.height)) {
            Log.w(TAG, "Atlas ${bitmap.width}x${bitmap.height} doesn't fit its layout $layout")
            bitmap.recycle()
            return null
        }
        return MascotAtlas(bitmap.asImageBitmap(), layout)
    }
}

/** The active mascot atlas, shared app-wide. Null only until the first decode finishes. */
@Composable
fun rememberMascotAtlas(): MascotAtlas? {
    val context = LocalContext.current
    val source by MascotSpriteStore.source.collectAsState()
    val atlas by produceState(MascotSpriteStore.cachedAtlas(source), source) {
        value = MascotSpriteStore.load(context.applicationContext, source)
    }
    return atlas
}
