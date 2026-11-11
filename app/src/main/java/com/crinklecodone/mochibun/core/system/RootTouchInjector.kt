// SPDX-License-Identifier: Apache-2.0
package com.crinklecodone.mochibun.core.system

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.system.Os
import android.util.Log
import android.view.Display
import android.view.Surface
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile
import kotlin.math.roundToInt

/**
 * Root touch fallback for windows that block AccessibilityService gestures.
 *
 * Fast path: a tiny root helper (libevinject.so, see cpp/evinject) that writes evdev frames
 * straight into the touchscreen node. We talk to it through an anonymous pipe (stdin), so a tap
 * costs one pipe write instead of booting `input` (app_process) every time.
 *
 * Slow path: the old `input tap` / `input swipe` shell commands. We drop to it whenever the
 * helper can't run (no root, helper failed, display rotated), so callers never notice.
 *
 * Lifecycle: call [attach] when the cursor turns on and [release] when it turns off. The helper
 * is only launched lazily on the first native tap/swipe/drag, quits on [release], and also exits
 * by itself if the app dies (EOF on stdin).
 */
@SuppressLint("StaticFieldLeak")
object RootTouchInjector {

    private const val TAG = "RootTouchInjector"

    // ---- wire format: keep in sync with evinject.c (24 bytes, little-endian) ----
    private const val PKT_SIZE = 24
    private const val PKT_MAGIC = 0xB7
    private const val OP_DOWN = 1
    private const val OP_MOVE = 2
    private const val OP_UP = 3
    private const val OP_TAP = 4
    private const val OP_SWIPE = 5
    private const val OP_RESET = 6
    private const val OP_QUIT = 8
    private const val OP_KEY = 10
    private const val OP_PASTE = 11

    private const val TAP_HOLD_MS = 20         // finger-on-glass time; DOWN itself lands instantly
    private const val MAX_LAUNCH_FAILURES = 3
    private const val RETRY_AFTER_MS = 60_000L
    private const val NO_MOVE = -1L

    /** Pin a node here ("/dev/input/event3") if auto-detect ever picks the wrong one. */
    private val NODE_OVERRIDE: String? = "/dev/input/event3"

    // One worker, no core threads: the thread exits ~10 s after the last command, so an idle
    // launcher carries no extra thread.
    private val io = ThreadPoolExecutor(
        0, 1, 10L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(),
        ThreadFactory { r -> Thread(r, "mochibun-evi").apply { isDaemon = true } }
    )

    @SuppressLint("StaticFieldLeak")
    @Volatile private var appCtx: Context? = null
    @Volatile var isNativeActive: Boolean = false
        private set

    // ---- io-thread-only state ----
    private var helperProcess: Process? = null
    private var helperStream: OutputStream? = null
    private var failures = 0
    private var blockedUntilMs = 0L
    private val pkt = ByteArray(PKT_SIZE)

    // drag coalescing: only the newest MOVE matters, so keep one slot and enqueue at most one task
    private val moveSlot = AtomicLong(NO_MOVE)
    private val moveTask = Runnable {
        val v = moveSlot.getAndSet(NO_MOVE)
        if (v != NO_MOVE) {
            val x = (v shr 32).toInt()
            val y = v.toInt()
            if (!nativeSend(OP_MOVE, x, y)) legacyMove(x, y)
        }
    }

    // legacy drag emulation (io thread only): remember the press, replay it as one swipe on release
    private var legacyX0 = 0f; private var legacyY0 = 0f
    private var legacyX1 = 0f; private var legacyY1 = 0f
    private var legacyT0 = 0L

    // ------------------------------------------------------------------ public API

    /** Cursor turned on. Cheap: stores a context, spawns nothing, asks for no root. */
    fun attach(context: Context) {
        appCtx = context.applicationContext
    }

    /** Optional: bring the helper up now so the first click is instant. Not called by default. */
    fun prewarm() {
        io.execute { ensureHelper() }
    }

    /** Cursor turned off (or service going away): let go of any finger and stop the helper. */
    fun release() {
        appCtx = null
        moveSlot.set(NO_MOVE)
        io.execute {
            trySend(OP_RESET)
            trySend(OP_QUIT)
            closeHelper()
        }
    }

    fun tapAsync(x: Int, y: Int) {
        tapAsync(x.toFloat(), y.toFloat(), TAP_HOLD_MS)
    }

    fun tapAsync(x: Float, y: Float, holdMs: Int = TAP_HOLD_MS) {
        io.execute {
            if (!nativeSend(OP_TAP, px(x), px(y), holdMs)) legacyTap(x, y)
        }
    }

    fun swipeAsync(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 200L) {
        swipeAsync(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), durationMs)
    }

    fun swipeAsync(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 200L) {
        io.execute {
            val ok = nativeSend(OP_SWIPE, px(x1), px(y1), px(x2), px(y2), durationMs.toInt())
            if (!ok) legacySwipe(x1, y1, x2, y2, durationMs)
        }
    }

    fun touchDown(x: Float, y: Float) {
        io.execute { if (!nativeSend(OP_DOWN, px(x), px(y))) legacyDown(x, y) }
    }

    /** Safe to call every frame: calls are coalesced, only the newest position is sent. */
    fun touchMove(x: Float, y: Float) {
        val prev = moveSlot.getAndSet((px(x).toLong() shl 32) or (px(y).toLong() and 0xFFFFFFFFL))
        if (prev == NO_MOVE) io.execute(moveTask)
    }

    fun touchUp(x: Float, y: Float) {
        io.execute {
            moveSlot.set(NO_MOVE)
            val ok = nativeSend(OP_MOVE, px(x), px(y)) && nativeSend(OP_UP)
            if (!ok) legacyUp(x, y)
        }
    }

    /** Injects a Linux evdev key code (e.g. 523 for KEY_NUMERIC_POUND, 28 for KEY_ENTER). */
    fun keyAsync(linuxCode: Int, holdMs: Int = 20) {
        io.execute {
            if (!nativeSend(OP_KEY, linuxCode, holdMs)) {
                val bin = appCtx?.let { installHelper(it) }
                if (bin != null && RootShell.isSuBinaryPresent()) {
                    RootShell.submit("exec ${q(bin.path)} key $linuxCode $holdMs")
                } else {
                    // Fallback to input keyevent if known
                    val androidKey = when (linuxCode) {
                        523, 228 -> 18 // KEYCODE_POUND
                        522 -> 17 // KEYCODE_STAR
                        28 -> 66 // KEYCODE_ENTER
                        158 -> 4 // KEYCODE_BACK
                        else -> linuxCode
                    }
                    RootShell.submit("input keyevent $androidKey")
                }
            }
        }
    }

    /** Injects KEY_PASTE (135) to paste current clipboard contents. */
    fun pasteAsync() {
        io.execute {
            if (!nativeSend(OP_PASTE)) {
                val bin = appCtx?.let { installHelper(it) }
                if (bin != null && RootShell.isSuBinaryPresent()) {
                    RootShell.submit("exec ${q(bin.path)} paste")
                } else {
                    RootShell.submit("input keyevent 279") // KEYCODE_PASTE
                }
            }
        }
    }

    /** Lift whatever finger we have down (stuck-drag escape hatch). */
    fun cancelTouch() {
        io.execute { moveSlot.set(NO_MOVE); trySend(OP_RESET) }
    }

    fun shutdown() {
        release()
    }

    // ------------------------------------------------------------------ native path

    private fun px(v: Float): Int = v.roundToInt().coerceAtLeast(0)

    private fun nativeSend(op: Int, a: Int = 0, b: Int = 0, c: Int = 0, d: Int = 0, e: Int = 0): Boolean {
        if (appCtx == null || !isNaturalRotation()) return false
        if (trySend(op, a, b, c, d, e)) return true
        // never started, idled out, or died: bring it up once and retry
        if (!ensureHelper()) return false
        return trySend(op, a, b, c, d, e)
    }

    private fun trySend(op: Int, a: Int = 0, b: Int = 0, c: Int = 0, d: Int = 0, e: Int = 0): Boolean {
        val stream = helperStream ?: return false
        encodePacket(pkt, op, a, b, c, d, e)
        val t0 = System.nanoTime()
        return try {
            stream.write(pkt, 0, PKT_SIZE)
            stream.flush()
            val dtUs = (System.nanoTime() - t0) / 1000L
            Log.d(TAG, "pipe write op=$op took ${dtUs}µs")
            true
        } catch (ex: IOException) {
            Log.w(TAG, "helper pipe write failed (helper gone): ${ex.message}")
            closeHelper()
            false
        }
    }

    private fun ensureHelper(): Boolean {
        if (helperStream != null) return true
        val ctx = appCtx ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now < blockedUntilMs) return false
        val ok = try {
            launchHelper(ctx)
        } catch (t: Throwable) {
            Log.w(TAG, "helper launch blew up", t)
            false
        }
        if (ok) {
            failures = 0
        } else if (++failures >= MAX_LAUNCH_FAILURES) {
            failures = 0
            blockedUntilMs = now + RETRY_AFTER_MS    // stay on the legacy path for a minute, don't hammer su
            Log.w(TAG, "native touch helper unavailable, using input tap/swipe for the next minute")
        }
        return ok
    }

    private fun launchHelper(ctx: Context): Boolean {
        if (Shell.isAppGrantedRoot() != true) {
            Log.d(TAG, "root not granted yet, skipping native helper spawn")
            return false
        }
        val bin = installHelper(ctx) ?: return false
        val (w, h) = naturalSize(ctx)

        val cmd = buildString {
            append("exec ").append(q(bin.path)).append(" daemon --stdin")
            append(" --screen ").append(w).append('x').append(h)
            NODE_OVERRIDE?.let { append(" --device ").append(q(it)) }
        }

        val proc = try {
            ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            Log.w(TAG, "failed to spawn evinject process: ${e.message}")
            return false
        }

        // Wait up to ~3 s for the initial "evi-ok" line from stdout without blocking forever
        val reader = proc.inputStream.bufferedReader()
        val deadline = SystemClock.elapsedRealtime() + 3000L
        var banner: String? = null

        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                if (proc.inputStream.available() > 0) {
                    val line = reader.readLine()
                    if (line != null) {
                        banner = line
                        break
                    }
                }
            } catch (_: Exception) {
                break
            }
            if (!proc.isAlive) break
            SystemClock.sleep(20)
        }

        if (banner == null || !banner.contains("evi-ok")) {
            val exitCode = if (!proc.isAlive) proc.exitValue() else -1
            Log.w(TAG, "evinject didn't start (exit=$exitCode, banner=$banner)")
            try { proc.destroy() } catch (_: Throwable) {}
            return false
        }

        Log.i(TAG, banner)
        helperProcess = proc
        helperStream = proc.outputStream
        isNativeActive = true
        return true
    }

    private fun closeHelper() {
        try { helperStream?.close() } catch (_: Throwable) {}
        helperStream = null
        try { helperProcess?.destroy() } catch (_: Throwable) {}
        helperProcess = null
        isNativeActive = false
    }

    /**
     * Root can't exec a .so that's still inside the APK (extractNativeLibs=false), so copy it to
     * filesDir once per install. lastUpdateTime changes on every reinstall, so dev builds never
     * run a stale helper.
     */
    private fun installHelper(ctx: Context): File? {
        // prefer evinject binary in /data/local/tmp
        val tmpBin = File("/data/local/tmp/evinject")
        if (tmpBin.isFile && tmpBin.canExecute()) {
            return tmpBin
        }
        // fallback to apk extraction
        val dir = File(ctx.filesDir, "evinject").apply { mkdirs() }
        val stamp = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime
        val bin = File(dir, "evinject-$stamp")
        if (bin.isFile && bin.length() > 0 && bin.canExecute()) return bin

        val info = ctx.applicationInfo
        val apks = listOf(info.sourceDir) + (info.splitSourceDirs?.toList() ?: emptyList())
        for (abi in Build.SUPPORTED_ABIS) {
            for (apk in apks) {
                ZipFile(apk).use { zip ->
                    val entry = zip.getEntry("lib/$abi/libevinject.so") ?: return@use
                    val tmp = File(dir, "evinject.tmp")
                    zip.getInputStream(entry).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    Os.chmod(tmp.path, 0b111_101_101)           // 0755
                    dir.listFiles { f -> f.name.startsWith("evinject-") }?.forEach { it.delete() }
                    if (tmp.renameTo(bin)) return bin
                }
            }
        }
        Log.w(TAG, "libevinject.so not found in the APK for ${Build.SUPPORTED_ABIS.joinToString()}")
        return null
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun display(ctx: Context): Display? =
        ctx.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)

    // Raw digitizer coordinates live in the panel's natural orientation. Rotated? Old path for now.
    private fun isNaturalRotation(): Boolean {
        val ctx = appCtx ?: return false
        return display(ctx)?.rotation == Surface.ROTATION_0
    }

    @Suppress("DEPRECATION")
    private fun naturalSize(ctx: Context): Pair<Int, Int> {
        val dsp = display(ctx) ?: return 480 to 800
        val p = Point().also { dsp.getRealSize(it) }
        val turned = dsp.rotation == Surface.ROTATION_90 || dsp.rotation == Surface.ROTATION_270
        return if (turned) p.y to p.x else p.x to p.y
    }

    // ------------------------------------------------------------------ legacy path
    // These are the original `input` shell commands. Keep the exact strings/behavior the file had.

    private fun legacyTap(x: Float, y: Float) {
        val bin = "/data/local/tmp/evinject"
        val xi = x.roundToInt().coerceIn(0, 480)
        val yi = y.roundToInt().coerceIn(0, 800)
        RootShell.submit("if [ -x $bin ]; then $bin tap $xi $yi 50 --device /dev/input/event3; else input tap $xi $yi; fi")
    }

    private fun legacySwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val bin = "/data/local/tmp/evinject"
        val xi1 = x1.roundToInt().coerceIn(0, 480)
        val yi1 = y1.roundToInt().coerceIn(0, 800)
        val xi2 = x2.roundToInt().coerceIn(0, 480)
        val yi2 = y2.roundToInt().coerceIn(0, 800)
        RootShell.submit("if [ -x $bin ]; then $bin swipe $xi1 $yi1 $xi2 $yi2 $durationMs --device /dev/input/event3; else input swipe $xi1 $yi1 $xi2 $yi2 $durationMs; fi")
    }

    private fun legacyDown(x: Float, y: Float) {
        legacyX0 = x; legacyY0 = y; legacyX1 = x; legacyY1 = y
        legacyT0 = SystemClock.uptimeMillis()
    }

    private fun legacyMove(x: Int, y: Int) {
        legacyX1 = x.toFloat(); legacyY1 = y.toFloat()
    }

    private fun legacyUp(x: Float, y: Float) {
        legacyX1 = x; legacyY1 = y
        val dx = legacyX1 - legacyX0
        val dy = legacyY1 - legacyY0
        if (dx * dx + dy * dy < 100f) {
            legacyTap(legacyX0, legacyY0)
        } else {
            val dur = (SystemClock.uptimeMillis() - legacyT0).coerceIn(50L, 2000L)
            legacySwipe(legacyX0, legacyY0, legacyX1, legacyY1, dur)
        }
    }

    // ------------------------------------------------------------------ wire encoding

    /** 24-byte little-endian packet, mirrored by `struct evi_pkt` in evinject.c. */
    internal fun encodePacket(buf: ByteArray, op: Int, a: Int, b: Int, c: Int, d: Int, e: Int) {
        buf[0] = PKT_MAGIC.toByte()
        buf[1] = op.toByte()
        buf[2] = 0
        buf[3] = 0
        putLe(buf, 4, a); putLe(buf, 8, b); putLe(buf, 12, c); putLe(buf, 16, d); putLe(buf, 20, e)
    }

    private fun putLe(buf: ByteArray, off: Int, v: Int) {
        buf[off] = v.toByte()
        buf[off + 1] = (v shr 8).toByte()
        buf[off + 2] = (v shr 16).toByte()
        buf[off + 3] = (v shr 24).toByte()
    }
}
