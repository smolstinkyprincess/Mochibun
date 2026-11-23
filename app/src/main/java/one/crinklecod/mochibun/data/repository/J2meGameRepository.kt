package one.crinklecod.mochibun.data.repository

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.collection.LruCache
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import one.crinklecod.mochibun.core.system.RootShell
import one.crinklecod.mochibun.core.util.compactIfOpaque
import one.crinklecod.mochibun.data.model.LauncherApp
import one.crinklecod.mochibun.data.model.j2mePackageKey
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

internal data class J2meManifestMetadata(
    val name: String,
    val iconPath: String?
)

internal data class ParsedJarGame(
    val metadata: J2meManifestMetadata,
    val iconBytes: ByteArray?,
    val needsSecondPass: Boolean = false
)

/**
 * Indexes user-selected SAF folders for J2ME `.jar` games, parses `META-INF/MANIFEST.MF`
 * metadata and PNG icons into a bounded memory cache, and launches `.jar` documents via
 * Android's `ACTION_VIEW` (`application/java-archive`) for AbyssME or J2ME Loader.
 */
object J2meGameRepository {

    private const val TAG = "J2meGameRepository"
    private const val J2ME_MIME_TYPE = "application/java-archive"
    private const val MAX_ICON_SIZE_PX = 96
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_ICON_ENTRY_BYTES = 256 * 1024
    private const val MAX_CANDIDATE_IMAGE_BYTES = 128 * 1024
    private const val MAX_CANDIDATE_TOTAL_BYTES = 512 * 1024
    private const val JAR_CACHE_MAX_BYTES = 2 * 1024 * 1024
    const val DEFAULT_ABYSSME_CONFIGS_PATH =
        "/storage/emulated/0/Android/data/io.github.eriark.abyssme/files/configs"
    private const val ABYSSME_CONFIGS_DOCUMENT_ID =
        "primary:Android/data/io.github.eriark.abyssme/files/configs"

    val TEMPLATE_SYNC_KEYS: Set<String> = linkedSetOf(
        // Controls & Virtual Overlay
        "ControlMode",
        "KeyMappings",
        "ActiveKeyMappingProfile",
        "KeyMappingProfiles",
        "TouchInput",
        "ShowKeyboard",
        "VirtualKeyboardType",
        "TouchVisibility",
        "touchLayout",
        "touchKeypadOverlay",
        "touchSize",
        "touchTheme",
        "touchOpacity",
        "gameHandleVisible",
        "gameShortcutOpacity",
        "VirtualKeyboardAlpha",
        "VirtualKeyboardForceOpacity",
        "VirtualKeyboardFeedback",
        "VirtualKeyboardDelay",
        "VirtualKeyboardColorBackground",
        "VirtualKeyboardColorBackgroundSelected",
        "VirtualKeyboardColorForeground",
        "VirtualKeyboardColorForegroundSelected",
        "VirtualKeyboardColorOutline",
        // Display & Aspect-Fit Scaling
        "ForceFullscreen",
        "Orientation",
        "ScreenRotation",
        "ScreenScaleToFit",
        "ScreenKeepAspectRatio",
        "ScreenScaleType",
        "ScreenScaleRatio",
        "ScreenGravity",
        "ScreenFilter",
        "ScreenBackgroundColor",
        "RetroShader",
        "FsrUpscaling",
        "FsrSharpness"
    )

    data class DetectedJ2meConfig(
        val gameId: String,
        val documentUri: String? = null,
        val rootFilePath: String? = null
    )

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val launchSyncInFlight = AtomicBoolean(false)

    private data class CachedJarEntry(
        val name: String,
        val iconBitmap: Bitmap
    )

    private val cacheLock = Any()
    private val jarCache = object : LruCache<String, CachedJarEntry>(JAR_CACHE_MAX_BYTES) {
        override fun sizeOf(key: String, value: CachedJarEntry): Int =
            value.iconBitmap.allocationByteCount.coerceAtLeast(1)
    }

    @Volatile
    private var cachedFallbackIcon: Bitmap? = null

    fun clearCache() {
        synchronized(cacheLock) {
            jarCache.evictAll()
        }
    }

    /**
     * Takes a persistable read grant on [newTreeUri] (or clears when null), releases any
     * previously persisted J2ME folder grant that differs, and persists the preference.
     */
    fun saveFolderTreeUri(context: Context, newTreeUri: Uri?) {
        val resolver = context.contentResolver
        val oldUriString = PreferencesManager.getJ2meFolderUri(context)
        val newUriString = newTreeUri?.toString()

        if (newTreeUri != null) {
            try {
                resolver.takePersistableUriPermission(
                    newTreeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed taking persistable URI permission for $newTreeUri: ${e.message}")
            }
        }

        if (!oldUriString.isNullOrBlank() && oldUriString != newUriString) {
            try {
                resolver.releasePersistableUriPermission(
                    Uri.parse(oldUriString),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }

        if (oldUriString != newUriString) {
            clearCache()
        }
        PreferencesManager.setJ2meFolderUri(context, newUriString)
    }

    /**
     * Resolves a human-readable folder name for [treeUriString], returning `null` when unset.
     */
    fun getFolderDisplayName(context: Context, treeUriString: String?): String? {
        if (treeUriString.isNullOrBlank()) return null
        val treeUri = try {
            Uri.parse(treeUriString)
        } catch (_: Exception) {
            return null
        }

        try {
            val treeId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val displayName = if (idx >= 0) cursor.getString(idx) else null
                    if (!displayName.isNullOrBlank()) return displayName
                }
            }
        } catch (_: Exception) {
            // Fall through to URI segment parsing
        }

        return fallbackTreeDisplayName(treeUri)
    }

    internal fun fallbackTreeDisplayName(treeUri: Uri): String? {
        val rawId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            treeUri.lastPathSegment
        } ?: return treeUri.lastPathSegment
        val afterColon = rawId.substringAfter(':', rawId)
        val trimmed = afterColon.trimEnd('/')
        return trimmed.substringAfterLast('/', trimmed).takeIf { it.isNotBlank() } ?: rawId
    }

    /**
     * Scans the persisted SAF tree [treeUriString] on [Dispatchers.IO] for `.jar` games.
     * Gracefully returns an empty list if unset, deleted, or permission was revoked.
     */
    suspend fun scanFolder(
        context: Context,
        preferences: SharedPreferences,
        treeUriString: String?
    ): List<LauncherApp> = withContext(Dispatchers.IO) {
        if (treeUriString.isNullOrBlank()) return@withContext emptyList()
        val treeUri = try {
            Uri.parse(treeUriString)
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        val treeDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val entries = ArrayList<LauncherApp>()
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modifiedCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idCol < 0 || nameCol < 0) return@use

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol) ?: continue
                    val displayName = cursor.getString(nameCol) ?: continue
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    if (!displayName.endsWith(".jar", ignoreCase = true)) continue

                    val lastModified = if (modifiedCol >= 0 && !cursor.isNull(modifiedCol)) {
                        cursor.getLong(modifiedCol)
                    } else {
                        0L
                    }
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val docUriString = docUri.toString()
                    val customIconName = preferences.getString(
                        "custom_icon_${j2mePackageKey(docUriString)}",
                        null
                    )
                    val app = loadJarGame(
                        context = context,
                        docUri = docUri,
                        docUriString = docUriString,
                        fileName = displayName,
                        lastModified = lastModified,
                        customIconName = customIconName
                    )
                    if (app != null) entries.add(app)
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Lost SAF read permission for $treeUriString: ${e.message}")
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed scanning J2ME folder $treeUriString: ${e.message}")
            return@withContext emptyList()
        }

        entries.sortedBy { it.lowercaseName }
            .also {
                val appContext = context.applicationContext ?: context
                val hasTemplate = !PreferencesManager.getJ2meTemplateConfigJson(appContext).isNullOrBlank()
                val hasConfigsUri = !PreferencesManager.getJ2meConfigsFolderUri(appContext).isNullOrBlank()
                if (hasTemplate && (hasConfigsUri || RootShell.isRootAvailable())) {
                    syncScope.launch {
                        syncConfigTemplateToAllGames(appContext)
                    }
                }
            }
    }

    // ── J2ME Config Template Sync (AbyssME / J2ME Loader) ───────────────────

    fun buildAbyssmeConfigsInitialUri(): Uri =
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            ABYSSME_CONFIGS_DOCUMENT_ID
        )

    /**
     * Persists a read/write SAF tree grant for the emulator's `configs` directory, releasing any
     * replaced `configs` grant (unless still used as the `.jar` folder URI).
     */
    fun saveConfigsFolderTreeUri(context: Context, newTreeUri: Uri?) {
        val resolver = context.contentResolver
        val oldUriString = PreferencesManager.getJ2meConfigsFolderUri(context)
        val newUriString = newTreeUri?.toString()

        if (newTreeUri != null) {
            try {
                resolver.takePersistableUriPermission(
                    newTreeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed taking RW persistable URI permission for $newTreeUri: ${e.message}")
                try {
                    resolver.takePersistableUriPermission(
                        newTreeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (readEx: Exception) {
                    Log.w(TAG, "Failed taking read persistable URI permission: ${readEx.message}")
                }
            }
        }

        if (!oldUriString.isNullOrBlank() &&
            oldUriString != newUriString &&
            oldUriString != PreferencesManager.getJ2meFolderUri(context)
        ) {
            try {
                resolver.releasePersistableUriPermission(
                    oldUriString.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
                try {
                    resolver.releasePersistableUriPermission(
                        oldUriString.toUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
        }

        PreferencesManager.setJ2meConfigsFolderUri(context, newUriString)
    }

    fun clearTemplateSync(context: Context) {
        PreferencesManager.setJ2meTemplateConfigJson(context, null)
        saveConfigsFolderTreeUri(context, null)
    }

    /**
     * Copies only [TEMPLATE_SYNC_KEYS] present in [templateJsonText] onto [targetJsonText],
     * preserving per-game properties such as `detectedScreenWidth`, `detectedScreenHeight`,
     * `CompatibilityProfile`, `compatibilityReasons`, and `SystemProperties`.
     */
    fun mergeConfigTemplate(targetJsonText: String, templateJsonText: String): String {
        val targetJson = JSONObject(targetJsonText)
        val templateJson = JSONObject(templateJsonText)
        for (key in TEMPLATE_SYNC_KEYS) {
            if (templateJson.has(key)) {
                targetJson.put(key, templateJson.get(key))
            }
        }
        return targetJson.toString(2)
    }

    suspend fun listDetectedGameConfigs(context: Context): List<DetectedJ2meConfig> =
        withContext(Dispatchers.IO) {
            val configsUri = PreferencesManager.getJ2meConfigsFolderUri(context)
            if (!configsUri.isNullOrBlank()) {
                return@withContext try {
                    listSafGameConfigs(context, configsUri)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed listing SAF J2ME configs: ${e.message}")
                    emptyList()
                }
            }
            if (RootShell.isRootAvailable()) {
                return@withContext listRootGameConfigs()
            }
            emptyList()
        }

    suspend fun readDetectedGameConfig(
        context: Context,
        entry: DetectedJ2meConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!entry.documentUri.isNullOrBlank()) {
                val text = context.contentResolver.openInputStream(entry.documentUri.toUri())
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: return@withContext Result.failure(
                        IllegalStateException("Could not open ${entry.gameId}/config.json")
                    )
                return@withContext Result.success(text)
            }
            if (!entry.rootFilePath.isNullOrBlank()) {
                val res = RootShell.execute("cat ${shellQuote(entry.rootFilePath)}")
                if (!res.isSuccess) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            res.errorString.ifBlank { "Root read failed for ${entry.gameId}" }
                        )
                    )
                }
                return@withContext Result.success(res.outputString)
            }
            Result.failure(IllegalArgumentException("No config source specified"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncConfigTemplateToAllGames(context: Context): Result<Int> =
        withContext(Dispatchers.IO) {
            syncMutex.withLock {
                val templateJsonText = PreferencesManager.getJ2meTemplateConfigJson(context)
                if (templateJsonText.isNullOrBlank()) {
                    return@withLock Result.failure(
                        IllegalStateException("No J2ME template saved yet")
                    )
                }
                try {
                    JSONObject(templateJsonText)
                } catch (e: Exception) {
                    return@withLock Result.failure(
                        IllegalStateException("Saved template JSON is invalid: ${e.message}", e)
                    )
                }

                val configsUri = PreferencesManager.getJ2meConfigsFolderUri(context)
                if (!configsUri.isNullOrBlank()) {
                    return@withLock syncViaSaf(context, configsUri, templateJsonText)
                }
                if (RootShell.isRootAvailable()) {
                    return@withLock syncViaRoot(templateJsonText)
                }
                Result.failure(
                    IllegalStateException("Link AbyssME configs folder first")
                )
            }
        }

    private fun listSafGameConfigs(
        context: Context,
        treeUriString: String
    ): List<DetectedJ2meConfig> {
        val treeUri = treeUriString.toUri()
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val dirProjection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val results = ArrayList<DetectedJ2meConfig>()
        context.contentResolver.query(rootChildrenUri, dirProjection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            if (idCol < 0 || nameCol < 0) return@use

            while (cursor.moveToNext()) {
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                if (mime != DocumentsContract.Document.MIME_TYPE_DIR) continue
                val gameDirId = cursor.getString(idCol) ?: continue
                val gameDirName = cursor.getString(nameCol) ?: continue
                val gameChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, gameDirId)
                context.contentResolver.query(gameChildrenUri, dirProjection, null, null, null)
                    ?.use { childCursor ->
                        val childIdCol =
                            childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val childNameCol =
                            childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val childMimeCol =
                            childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        if (childIdCol < 0 || childNameCol < 0) return@use

                        while (childCursor.moveToNext()) {
                            val childMime =
                                if (childMimeCol >= 0) childCursor.getString(childMimeCol) else null
                            if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                            val childName = childCursor.getString(childNameCol) ?: continue
                            if (childName.equals("config.json", ignoreCase = true)) {
                                val configDocId = childCursor.getString(childIdCol) ?: continue
                                val configDocUri =
                                    DocumentsContract.buildDocumentUriUsingTree(treeUri, configDocId)
                                results.add(
                                    DetectedJ2meConfig(
                                        gameId = gameDirName,
                                        documentUri = configDocUri.toString()
                                    )
                                )
                                break
                            }
                        }
                    }
            }
        }
        return results.sortedBy { it.gameId.lowercase(Locale.ROOT) }
    }

    private suspend fun listRootGameConfigs(): List<DetectedJ2meConfig> {
        val cmd =
            "for f in \"$DEFAULT_ABYSSME_CONFIGS_PATH\"/*/config.json; do " +
                "if [ -f \"\$f\" ]; then printf '%s\\n' \"\$f\"; fi; " +
                "done"
        val res = RootShell.execute(cmd)
        if (!res.isSuccess) return emptyList()
        return res.stdout
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { path ->
                val gameId = File(path).parentFile?.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                DetectedJ2meConfig(gameId = gameId, rootFilePath = path)
            }
            .sortedBy { it.gameId.lowercase(Locale.ROOT) }
    }

    private fun syncViaSaf(
        context: Context,
        treeUriString: String,
        templateJsonText: String
    ): Result<Int> {
        val entries = try {
            listSafGameConfigs(context, treeUriString)
        } catch (e: SecurityException) {
            Log.w(TAG, "Lost SAF permission for configs folder $treeUriString: ${e.message}")
            return Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "Failed scanning J2ME configs folder $treeUriString: ${e.message}")
            return Result.failure(e)
        }

        var updated = 0
        var lastWriteSecurityError: SecurityException? = null
        for (entry in entries) {
            val docUriString = entry.documentUri ?: continue
            val docUri = docUriString.toUri()
            try {
                val existing = context.contentResolver.openInputStream(docUri)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: continue
                val merged = mergeConfigTemplate(existing, templateJsonText)
                writeSafTextFile(context, docUri, merged)
                updated++
            } catch (e: SecurityException) {
                Log.w(TAG, "No SAF write permission for ${entry.gameId}/config.json: ${e.message}")
                lastWriteSecurityError = e
            } catch (e: Exception) {
                Log.w(TAG, "Skipping J2ME config ${entry.gameId}: ${e.message}")
            }
        }
        if (updated == 0 && lastWriteSecurityError != null) {
            return Result.failure(lastWriteSecurityError)
        }
        return Result.success(updated)
    }

    private suspend fun syncViaRoot(templateJsonText: String): Result<Int> {
        val entries = listRootGameConfigs()
        var updated = 0
        for (entry in entries) {
            val path = entry.rootFilePath ?: continue
            try {
                val readRes = RootShell.execute("cat ${shellQuote(path)}")
                if (!readRes.isSuccess) continue
                val existing = readRes.outputString
                val merged = mergeConfigTemplate(existing, templateJsonText)
                val marker = "__MOCHIBUN_CFG_${UUID.randomUUID().toString().replace("-", "")}__"
                val writeCmd = "cat <<'$marker' > ${shellQuote(path)}\n$merged\n$marker"
                val writeRes = RootShell.execute(writeCmd)
                if (writeRes.isSuccess) {
                    updated++
                } else {
                    Log.w(TAG, "Root write failed for ${entry.gameId}: ${writeRes.errorString}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping root J2ME config ${entry.gameId}: ${e.message}")
            }
        }
        return Result.success(updated)
    }

    private fun writeSafTextFile(context: Context, docUri: Uri, content: String) {
        val resolver = context.contentResolver
        val bytes = content.toByteArray(Charsets.UTF_8)
        val stream = try {
            resolver.openOutputStream(docUri, "wt")
        } catch (_: Exception) {
            null
        } ?: resolver.openOutputStream(docUri, "w")
            ?: throw IllegalStateException("Unable to open output stream for $docUri")
        stream.use { out ->
            out.write(bytes)
            out.flush()
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun loadJarGame(
        context: Context,
        docUri: Uri,
        docUriString: String,
        fileName: String,
        lastModified: Long,
        customIconName: String?
    ): LauncherApp? {
        val cacheKey = "$docUriString#$lastModified"
        val customBitmap = if (!customIconName.isNullOrEmpty()) {
            IconPackManager.getCachedImageBitmap(context, customIconName, MAX_ICON_SIZE_PX)
                ?.asAndroidBitmap()
        } else {
            null
        }

        val cached = synchronized(cacheLock) { jarCache.get(cacheKey) }
        if (cached != null) {
            return LauncherApp(
                resolveInfo = null,
                name = cached.name,
                iconBitmap = customBitmap ?: cached.iconBitmap,
                installTime = lastModified,
                j2meDocumentUri = docUriString
            )
        }

        val parsed = try {
            context.contentResolver.openInputStream(docUri)?.use { stream ->
                parseJarStream(stream, fileName)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No read permission for $docUriString: ${e.message}")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading JAR $fileName: ${e.message}")
            null
        }

        val finalMeta = parsed?.metadata ?: J2meManifestMetadata(
            name = stripJarExtension(fileName),
            iconPath = null
        )

        var iconBytes = parsed?.iconBytes
        if (iconBytes == null && parsed?.needsSecondPass == true && finalMeta.iconPath != null) {
            iconBytes = try {
                context.contentResolver.openInputStream(docUri)?.use { stream ->
                    extractEntryBytes(stream, finalMeta.iconPath)
                }
            } catch (_: Exception) {
                null
            }
        }

        val decodedIcon = iconBytes?.let(::decodeIconBitmap) ?: getFallbackIconBitmap()
        synchronized(cacheLock) {
            jarCache.put(cacheKey, CachedJarEntry(finalMeta.name, decodedIcon))
        }

        return LauncherApp(
            resolveInfo = null,
            name = finalMeta.name,
            iconBitmap = customBitmap ?: decodedIcon,
            installTime = lastModified,
            j2meDocumentUri = docUriString
        )
    }

    /**
     * Launches a J2ME `.jar` document via `ACTION_VIEW` (`application/java-archive`) with
     * read URI grant. When `Auto-Sync on Shortcut Launch` is enabled and a template is saved,
     * syncs the master template on [Dispatchers.IO] before starting the emulator.
     */
    fun launchGame(context: Context, documentUri: String): Boolean {
        val appContext = context.applicationContext ?: context
        val shouldAutoSync = PreferencesManager.isJ2meAutoSyncOnLaunchEnabled(appContext) &&
            !PreferencesManager.getJ2meTemplateConfigJson(appContext).isNullOrBlank() &&
            (!PreferencesManager.getJ2meConfigsFolderUri(appContext).isNullOrBlank() ||
                RootShell.isRootAvailable())

        if (!shouldAutoSync) {
            return startJ2meIntent(context, documentUri)
        }

        if (!launchSyncInFlight.compareAndSet(false, true)) {
            return true
        }
        syncScope.launch {
            try {
                syncConfigTemplateToAllGames(appContext)
                withContext(Dispatchers.Main) {
                    startJ2meIntent(appContext, documentUri)
                }
            } finally {
                launchSyncInFlight.set(false)
            }
        }
        return true
    }

    private fun startJ2meIntent(context: Context, documentUri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(documentUri.toUri(), J2ME_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "Install AbyssME or J2ME Loader to play .jar games ♡",
                Toast.LENGTH_LONG
            ).show()
            false
        } catch (_: SecurityException) {
            Toast.makeText(
                context,
                "Couldn't read .jar — re-select J2ME folder in Behavior settings",
                Toast.LENGTH_LONG
            ).show()
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed launching J2ME game $documentUri: ${e.message}")
            false
        }
    }

    /**
     * Unified launcher dispatch for both normal Android apps and J2ME `.jar` entries.
     */
    fun launchLauncherApp(context: Context, app: LauncherApp): Boolean {
        val j2meUri = app.j2meDocumentUri
        if (j2meUri != null) {
            return launchGame(context, j2meUri)
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return false
        return try {
            context.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed launching ${app.packageName}: ${e.message}")
            false
        }
    }

    // ── Pure JAR & MANIFEST.MF Parsers (unit-tested on JVM) ───────────────────

    internal fun stripJarExtension(fileName: String): String {
        val trimmed = fileName.trim()
        return if (trimmed.endsWith(".jar", ignoreCase = true) && trimmed.length > 4) {
            trimmed.dropLast(4)
        } else {
            trimmed.ifEmpty { "J2ME Game" }
        }
    }

    internal fun normalizeArchivePath(path: String): String =
        path.trim().replace('\\', '/').trimStart('/')

    internal fun parseManifest(
        inputStream: InputStream,
        fallbackFileName: String
    ): J2meManifestMetadata {
        val bytes = readBoundedBytes(inputStream, MAX_MANIFEST_BYTES) ?: ByteArray(0)
        var rawText = bytes.toString(Charsets.UTF_8)
        if (rawText.startsWith("\uFEFF")) {
            rawText = rawText.substring(1)
        }
        val normalized = rawText.replace("\r\n", "\n").replace('\r', '\n')
        val logicalLines = ArrayList<String>()
        for (line in normalized.split('\n')) {
            if (line.startsWith(" ") && logicalLines.isNotEmpty()) {
                val prevIdx = logicalLines.lastIndex
                logicalLines[prevIdx] = logicalLines[prevIdx] + line.substring(1)
            } else if (line.isNotEmpty()) {
                logicalLines.add(line)
            }
        }

        val attrs = HashMap<String, String>()
        for (line in logicalLines) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim().lowercase(Locale.ROOT)
            val value = line.substring(colon + 1).trim()
            if (key.isNotEmpty() && !attrs.containsKey(key)) {
                attrs[key] = value
            }
        }

        val midletName = attrs["midlet-name"]?.takeIf { it.isNotBlank() }
        val midletIcon = attrs["midlet-icon"]?.takeIf { it.isNotBlank() }

        val midlet1Parts = attrs["midlet-1"]?.split(',', limit = 3)
        val midlet1Name = midlet1Parts?.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() }
        val midlet1Icon = midlet1Parts?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

        val resolvedName = midletName ?: midlet1Name ?: stripJarExtension(fallbackFileName)
        val rawIconPath = midletIcon ?: midlet1Icon
        val normalizedIconPath = rawIconPath?.let(::normalizeArchivePath)?.takeIf { it.isNotBlank() }

        return J2meManifestMetadata(
            name = resolvedName,
            iconPath = normalizedIconPath
        )
    }

    internal fun parseJarStream(
        inputStream: InputStream,
        fileName: String
    ): ParsedJarGame {
        var metadata: J2meManifestMetadata? = null
        var matchedIconBytes: ByteArray? = null
        val candidateImages = HashMap<String, ByteArray>()
        var candidateTotalBytes = 0
        var skippedCandidates = false

        try {
            ZipInputStream(inputStream).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val normName = normalizeArchivePath(entry.name)

                    if (normName.equals("META-INF/MANIFEST.MF", ignoreCase = true)) {
                        metadata = parseManifest(zis, fileName)
                        val iconPath = metadata?.iconPath
                        if (iconPath != null) {
                            matchedIconBytes = findMatchingCandidate(candidateImages, iconPath)
                            candidateImages.clear()
                            if (matchedIconBytes != null) break
                        } else {
                            candidateImages.clear()
                            break
                        }
                        continue
                    }

                    val currentMeta = metadata
                    if (currentMeta == null) {
                        if (looksLikeIconAsset(normName)) {
                            val bytes = readBoundedBytes(zis, MAX_CANDIDATE_IMAGE_BYTES)
                            if (bytes != null && candidateTotalBytes + bytes.size <= MAX_CANDIDATE_TOTAL_BYTES) {
                                candidateImages[normName] = bytes
                                candidateTotalBytes += bytes.size
                            } else {
                                skippedCandidates = true
                            }
                        }
                    } else {
                        val iconPath = currentMeta.iconPath
                        if (iconPath == null) break
                        if (matchesArchivePath(normName, iconPath)) {
                            matchedIconBytes = readBoundedBytes(zis, MAX_ICON_ENTRY_BYTES)
                            if (matchedIconBytes != null) break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing JAR stream for $fileName: ${e.message}")
        }

        val resolvedMeta = metadata ?: J2meManifestMetadata(
            name = stripJarExtension(fileName),
            iconPath = null
        )
        val needsSecondPass = resolvedMeta.iconPath != null &&
            matchedIconBytes == null &&
            skippedCandidates
        return ParsedJarGame(
            metadata = resolvedMeta,
            iconBytes = matchedIconBytes,
            needsSecondPass = needsSecondPass
        )
    }

    private fun extractEntryBytes(inputStream: InputStream, targetPath: String): ByteArray? {
        return try {
            ZipInputStream(inputStream).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val normName = normalizeArchivePath(entry.name)
                    if (matchesArchivePath(normName, targetPath)) {
                        return@use readBoundedBytes(zis, MAX_ICON_ENTRY_BYTES)
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun looksLikeIconAsset(normPath: String): Boolean {
        val lower = normPath.lowercase(Locale.ROOT)
        return lower.endsWith(".png") ||
            lower.endsWith(".gif") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".bmp")
    }

    private fun matchesArchivePath(entryPath: String, targetPath: String): Boolean {
        if (entryPath == targetPath || entryPath.equals(targetPath, ignoreCase = true)) {
            return true
        }
        if (!targetPath.contains('/')) {
            val baseName = entryPath.substringAfterLast('/')
            if (baseName.equals(targetPath, ignoreCase = true)) return true
        }
        return false
    }

    private fun findMatchingCandidate(
        candidates: Map<String, ByteArray>,
        targetPath: String
    ): ByteArray? {
        candidates[targetPath]?.let { return it }
        candidates.entries.firstOrNull { it.key.equals(targetPath, ignoreCase = true) }
            ?.let { return it.value }
        if (!targetPath.contains('/')) {
            candidates.entries.firstOrNull {
                it.key.substringAfterLast('/').equals(targetPath, ignoreCase = true)
            }?.let { return it.value }
        }
        return null
    }

    private fun readBoundedBytes(input: InputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            total += read
            if (total > maxBytes) return null
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }

    private fun decodeIconBitmap(bytes: ByteArray): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_ICON_SIZE_PX ||
                bounds.outHeight / sampleSize > MAX_ICON_SIZE_PX
            ) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            val fitted = if (decoded.width > MAX_ICON_SIZE_PX || decoded.height > MAX_ICON_SIZE_PX) {
                val w: Int
                val h: Int
                if (decoded.width >= decoded.height) {
                    w = MAX_ICON_SIZE_PX
                    h = ((decoded.height * MAX_ICON_SIZE_PX) / decoded.width).coerceAtLeast(1)
                } else {
                    h = MAX_ICON_SIZE_PX
                    w = ((decoded.width * MAX_ICON_SIZE_PX) / decoded.height).coerceAtLeast(1)
                }
                val scaled = Bitmap.createScaledBitmap(decoded, w, h, false)
                if (scaled !== decoded) decoded.recycle()
                scaled
            } else {
                decoded
            }
            fitted.compactIfOpaque().also { it.prepareToDraw() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed decoding J2ME icon PNG: ${e.message}")
            null
        }
    }

    /**
     * Crisp 24x24 pixel-art pastel game cartridge fallback icon for `.jar` files without a
     * readable manifest icon. Cached once so all fallback games share the same tiny bitmap.
     */
    private fun getFallbackIconBitmap(): Bitmap {
        cachedFallbackIcon?.let { return it }
        synchronized(cacheLock) {
            cachedFallbackIcon?.let { return it }
            val size = 24
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint().apply { isAntiAlias = false }

            // Cartridge outer border (#6E4B7F)
            paint.color = 0xFF6E4B7F.toInt()
            canvas.drawRect(3f, 2f, 21f, 22f, paint)
            // Pastel lavender shell (#EADDFB)
            paint.color = 0xFFEADDFB.toInt()
            canvas.drawRect(4f, 3f, 20f, 21f, paint)
            // Top grip ribs (#C7B1EC)
            paint.color = 0xFFC7B1EC.toInt()
            canvas.drawRect(5f, 4f, 19f, 5f, paint)
            canvas.drawRect(5f, 6f, 19f, 7f, paint)
            // Kawaii pink cartridge label (#FFB6C9)
            paint.color = 0xFFFFB6C9.toInt()
            canvas.drawRect(6f, 9f, 18f, 17f, paint)
            // Pixel heart / diamond emblem on label (#FFF7FA)
            paint.color = 0xFFFFF7FA.toInt()
            canvas.drawRect(10f, 11f, 12f, 13f, paint)
            canvas.drawRect(12f, 11f, 14f, 13f, paint)
            canvas.drawRect(11f, 13f, 13f, 15f, paint)
            // Bottom connector notch (#8E6BA6)
            paint.color = 0xFF8E6BA6.toInt()
            canvas.drawRect(8f, 19f, 16f, 21f, paint)

            val compacted = bmp.compactIfOpaque().also { it.prepareToDraw() }
            cachedFallbackIcon = compacted
            return compacted
        }
    }
}
