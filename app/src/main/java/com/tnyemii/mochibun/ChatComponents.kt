package com.tnyemii.mochibun

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.tnyemii.mochibun.ui.theme.destructiveBgColor
import com.tnyemii.mochibun.ui.theme.destructiveColor
import com.tnyemii.mochibun.ui.theme.softSurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// main composable screen for offline llm chat, ollama, and web search :3
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatPlaceholderPage(
    isActivePage: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE) }

    var ollamaConnected by remember { mutableStateOf<Boolean?>(null) }

    var messageText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var activeStatus by remember { mutableStateOf<String?>(null) }

    // Selected message for options dialog (long-press or OK on focused message)
    var selectedMessageForOptions by remember { mutableStateOf<ChatMessage?>(null) }

    // Chat settings state
    var settingsMenuOpen by remember { mutableStateOf(false) }
    var settingsDialogOpen by remember { mutableStateOf(false) }

    var selectedBackend by remember {
        mutableStateOf(prefs.getString("backend", "Auto") ?: "Auto")
    }
    var selectedOllamaModel by remember {
        mutableStateOf(prefs.getString("ollama_model", "qwen3-fast:latest") ?: "qwen3-fast:latest")
    }

    var ollamaModels by remember {
        mutableStateOf(listOf(selectedOllamaModel))
    }

    var isRefreshingModels by remember { mutableStateOf(false) }
    var modelFetchStatus by remember { mutableStateOf<String?>(null) }

    var chatFontSize by remember {
        mutableStateOf(prefs.getFloat("font_size", 14f))
    }
    var enableReasoning by remember {
        mutableStateOf(prefs.getBoolean("enable_reasoning", false))
    }
    var showReasoning by remember {
        mutableStateOf(prefs.getBoolean("show_reasoning", false))
    }

    // preference update helpers <3
    val updateBackend: (String) -> Unit = { newVal ->
        selectedBackend = newVal
        prefs.edit().putString("backend", newVal).apply()
    }
    val updateOllamaModel: (String) -> Unit = { newVal ->
        selectedOllamaModel = newVal
        prefs.edit().putString("ollama_model", newVal).apply()
    }
    val updateFontSize: (Float) -> Unit = { newVal ->
        chatFontSize = newVal
        prefs.edit().putFloat("font_size", newVal).apply()
    }
    val updateEnableReasoning: (Boolean) -> Unit = { newVal ->
        enableReasoning = newVal
        prefs.edit().putBoolean("enable_reasoning", newVal).apply()
    }
    val updateShowReasoning: (Boolean) -> Unit = { newVal ->
        showReasoning = newVal
        prefs.edit().putBoolean("show_reasoning", newVal).apply()
    }

    val refreshModels: () -> Unit = {
        if (!isRefreshingModels) {
            isRefreshingModels = true
            modelFetchStatus = "Fetching models from PC..."
            coroutineScope.launch(Dispatchers.IO) {
                val fetched = fetchOllamaModels()
                withContext(Dispatchers.Main) {
                    isRefreshingModels = false
                    if (fetched.isNotEmpty()) {
                        ollamaModels = fetched.distinct()
                        if (selectedOllamaModel !in ollamaModels) {
                            val replacement = when {
                                "qwen3-fast:latest" in ollamaModels -> "qwen3-fast:latest"
                                else -> ollamaModels.first()
                            }
                            updateOllamaModel(replacement)
                            modelFetchStatus = "${ollamaModels.size} model(s) found • selected $replacement ♡"
                        } else {
                            modelFetchStatus = "Connected • ${ollamaModels.size} model(s) found ♡"
                        }
                    } else {
                        ollamaModels = emptyList()
                        modelFetchStatus = "No models found, or PC is offline/unreachable ♡"
                    }
                }
            }
        }
    }

    // load saved chat history and check model paths off the main thread <3
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var resolvedModelPath by remember { mutableStateOf("") }

    val chatListState = rememberLazyListState()

    // auto scroll to bottom when new messages arrive or when sending
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            chatListState.animateScrollToItem(messages.size - 1)
        }
    }

    var mascotExpression by remember { mutableStateOf(MascotExpression.IDLE) }

    LaunchedEffect(resolvedModelPath, isGenerating, messages.size) {
        mascotExpression = when {
            resolvedModelPath.isEmpty() && selectedBackend == "Local SmolLM2" -> MascotExpression.OFFLINE
            isGenerating -> MascotExpression.THINKING
            else -> {
                val lastMsg = messages.lastOrNull()
                if (lastMsg != null && !lastMsg.isUser && !isGenerating) {
                    MascotExpression.RESPONDING
                } else {
                    MascotExpression.IDLE
                }
            }
        }
    }

    LaunchedEffect(mascotExpression) {
        if (mascotExpression == MascotExpression.RESPONDING) {
            delay(3000)
            if (mascotExpression == MascotExpression.RESPONDING) {
                mascotExpression = MascotExpression.IDLE
            }
        }
    }

    // release any loaded native model when leaving the chat page <3
    DisposableEffect(Unit) {
        onDispose {
            NativeLlm.unloadModelIfLoaded()
        }
    }

    // load saved history, resolve model path, and auto copy model file from sdcard on IO thread <3
    LaunchedEffect(Unit) {
        val (loadedHistory, initialPath) = withContext(Dispatchers.IO) {
            val history = loadChatHistory(context)
            val internalFile = java.io.File(context.filesDir, "smol_model.gguf")
            val legacyInternal = java.io.File(context.filesDir, "SmolLM2-360M-Instruct.Q4_K_M.gguf")
            val externalPath = "/sdcard/SmolLM2/SmolLM2-360M-Instruct.Q4_K_M.gguf"
            val path = if (internalFile.exists() && internalFile.length() > 0) {
                internalFile.absolutePath
            } else if (legacyInternal.exists() && legacyInternal.length() > 0) {
                legacyInternal.absolutePath
            } else {
                val extFile = java.io.File(externalPath)
                if (extFile.exists() && extFile.canRead()) {
                    externalPath
                } else {
                    ""
                }
            }
            history to path
        }
        if (messages.isEmpty() && loadedHistory.isNotEmpty()) {
            messages.addAll(loadedHistory)
        }
        resolvedModelPath = initialPath

        val needCopy = withContext(Dispatchers.IO) {
            val internalFile = java.io.File(context.filesDir, "smol_model.gguf")
            val extFile = java.io.File("/sdcard/SmolLM2/SmolLM2-360M-Instruct.Q4_K_M.gguf")
            (!internalFile.exists() || internalFile.length() == 0L) && extFile.exists()
        }
        if (needCopy) {
            val internalFile = java.io.File(context.filesDir, "smol_model.gguf")
            val extFile = java.io.File("/sdcard/SmolLM2/SmolLM2-360M-Instruct.Q4_K_M.gguf")
            isGenerating = true
            activeStatus = "Auto-loading SmolLM2 model..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    extFile.inputStream().use { input ->
                        internalFile.outputStream().use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                    resolvedModelPath = internalFile.absolutePath
                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage(isUser = false, text = "Model auto-loaded successfully! ♡ Ready to chat."))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage(isUser = false, text = "Auto-load failed: ${e.localizedMessage ?: e.toString()} ♡"))
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isGenerating = false
                        activeStatus = null
                    }
                }
            }
        }
    }

    // file picker launcher if model needs manual selection :3
    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isGenerating = true
            activeStatus = "Copying model file..."
            coroutineScope.launch(Dispatchers.IO) {
                val destFile = java.io.File(context.filesDir, "smol_model.gguf")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                    resolvedModelPath = destFile.absolutePath
                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage(isUser = false, text = "Model loaded successfully! ♡ You can chat now."))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage(isUser = false, text = "Failed to load model: ${e.localizedMessage ?: e.toString()} ♡"))
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isGenerating = false
                        activeStatus = null
                    }
                }
            }
        }
    }

    // periodic background connection check only while resumed
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val connected = withContext(Dispatchers.IO) {
                    checkOllamaConnection()
                }
                ollamaConnected = connected
                delay(5 * 60 * 1000L)
            }
        }
    }

    // fetch models when opening settings dialog if needed
    LaunchedEffect(settingsDialogOpen) {
        if (settingsDialogOpen && (selectedBackend == "Auto" || selectedBackend == "PC Ollama")) {
            refreshModels()
        }
    }

    val sendMessageAction: () -> Unit = {
        val sent = messageText.trim()
        if (sent.isNotEmpty() && !isGenerating) {
            messages.add(ChatMessage(isUser = true, text = sent))
            val userMessageSnapshot = messages.toList()
            coroutineScope.launch(Dispatchers.IO) {
                saveChatHistory(context, userMessageSnapshot)
            }
            messageText = ""
            isGenerating = true

            val needSearch = WebSearchClassifier.shouldSearchWeb(sent)
            val isOnline = WebSearchClient.isNetworkAvailable(context)

            coroutineScope.launch(Dispatchers.IO) {
                var searchResults = emptyList<SearchResult>()
                var webStatus: String? = null
                var historySnapshot: List<ChatMessage> = emptyList()

                try {
                    if (needSearch) {
                        if (isOnline) {
                            withContext(Dispatchers.Main) { activeStatus = "Searching the web..." }
                            val query = WebSearchClassifier.extractSearchQuery(sent)
                            searchResults = WebSearchClient.search(query)
                            if (searchResults.isNotEmpty()) {
                                webStatus = "Web results used"
                            } else {
                                webStatus = "Search unavailable"
                            }
                        } else {
                            webStatus = "Offline answer"
                        }
                    }

                    var reply: String? = null
                    var backendTag = "PC · Ollama"

                    when (selectedBackend) {
                        "Auto" -> {
                            withContext(Dispatchers.Main) { activeStatus = "Connecting to PC..." }
                            val ollamaRes = tryOllama(
                                messages = messages,
                                searchEvidence = searchResults,
                                modelName = selectedOllamaModel,
                                enableReasoning = enableReasoning,
                                showReasoning = showReasoning
                            )
                            if (!ollamaRes.content.isNullOrBlank()) {
                                reply = ollamaRes.content
                                backendTag = "PC · Ollama ($selectedOllamaModel)"
                                withContext(Dispatchers.Main) {
                                    activeStatus = "Writing a reply..."
                                    ollamaConnected = true
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    activeStatus = "Using local SmolLM2..."
                                    ollamaConnected = false
                                }
                                if (resolvedModelPath.isNotBlank()) {
                                    val file = java.io.File(resolvedModelPath)
                                    if (file.exists() && file.length() > 0) {
                                        val fullPrompt = buildChatMLPrompt(messages, searchResults)
                                        reply = try {
                                            NativeLlm.generateText(resolvedModelPath, fullPrompt)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            null
                                        }
                                        backendTag = "Offline · SmolLM2"
                                    }
                                }
                                if (reply == null) {
                                    backendTag = "Offline · Error"
                                    val err = ollamaRes.error ?: "Could not reach Ollama on PC"
                                    reply = if (resolvedModelPath.isEmpty()) {
                                        "Ollama PC error: $err. No local SmolLM2 model selected. ♡"
                                    } else {
                                        "Ollama PC error: $err. Local SmolLM2 inference also failed. ♡"
                                    }
                                }
                            }
                        }
                        "PC Ollama" -> {
                            withContext(Dispatchers.Main) { activeStatus = "Connecting to PC..." }
                            val ollamaRes = tryOllama(
                                messages = messages,
                                searchEvidence = searchResults,
                                modelName = selectedOllamaModel,
                                enableReasoning = enableReasoning,
                                showReasoning = showReasoning
                            )
                            if (!ollamaRes.content.isNullOrBlank()) {
                                reply = ollamaRes.content
                                backendTag = "PC · Ollama ($selectedOllamaModel)"
                                withContext(Dispatchers.Main) {
                                    activeStatus = "Writing a reply..."
                                    ollamaConnected = true
                                }
                            } else {
                                withContext(Dispatchers.Main) { ollamaConnected = false }
                                backendTag = "PC · Error"
                                val err = ollamaRes.error ?: "Could not reach Ollama on PC at 100.103.158.68:11434"
                                reply = "Ollama error ($selectedOllamaModel):\n$err\n\nPlease verify Tailscale is connected and Ollama is running. ♡"
                            }
                        }
                        "Local SmolLM2" -> {
                            withContext(Dispatchers.Main) { activeStatus = "Using local SmolLM2..." }
                            if (resolvedModelPath.isNotBlank()) {
                                val file = java.io.File(resolvedModelPath)
                                if (file.exists() && file.length() > 0) {
                                    val fullPrompt = buildChatMLPrompt(messages, searchResults)
                                    reply = try {
                                        NativeLlm.generateText(resolvedModelPath, fullPrompt)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        null
                                    }
                                    backendTag = "Offline · SmolLM2"
                                }
                            }
                            if (reply == null) {
                                backendTag = "Offline · Error"
                                reply = if (resolvedModelPath.isEmpty()) {
                                    "No local SmolLM2 model file selected. Please tap above to select a model file. ♡"
                                } else {
                                    "Local SmolLM2 model file not found or inference failed. ♡"
                                }
                            }
                        }
                    }

                    val finalStatus = if (webStatus != null) "$backendTag • $webStatus" else backendTag

                    withContext(Dispatchers.Main) {
                        messages.add(
                            ChatMessage(
                                isUser = false,
                                text = reply ?: "An error occurred while generating response. ♡",
                                status = finalStatus,
                                sources = searchResults
                            )
                        )
                    }
                } finally {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.Main) {
                            activeStatus = null
                            isGenerating = false
                            historySnapshot = messages.toList()
                        }
                        saveChatHistory(context, historySnapshot)
                    }
                }
            }
        }
    }

    // D-pad focus zones on ChatPlaceholderPage:
    // 0 = Header (headerCol: 0 = "◀ Home", 1 = "⋮" Menu)
    // 1 = Select model file prompt (when shown)
    // 2 = Messages list (selectedMsgIndex)
    // 3 = Message Input TextField
    // 4 = Send button
    val showModelPrompt = resolvedModelPath.isEmpty() && selectedBackend == "Local SmolLM2"
    var focusZone by remember { mutableIntStateOf(3) }
    var headerCol by remember { mutableIntStateOf(1) }
    var selectedMsgIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val pageFocusRequester = remember { FocusRequester() }
    val inputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(messages.size) {
        selectedMsgIndex = (messages.size - 1).coerceAtLeast(0)
    }

    LaunchedEffect(selectedMsgIndex, messages.size, focusZone) {
        if (focusZone == 2 && messages.isNotEmpty() && selectedMsgIndex in messages.indices) {
            val isVisible = chatListState.layoutInfo.visibleItemsInfo.any {
                it.index == selectedMsgIndex
            }
            if (!isVisible) {
                chatListState.animateScrollToItem(selectedMsgIndex)
            }
        }
    }

    val anyChatDialogOpen = selectedMessageForOptions != null || settingsDialogOpen

    LaunchedEffect(isActivePage, focusZone, anyChatDialogOpen) {
        if (!isActivePage || anyChatDialogOpen) return@LaunchedEffect
        if (focusZone == 3 && !isGenerating) {
            repeat(6) {
                try {
                    inputFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        } else {
            keyboardController?.hide()
            repeat(6) {
                try {
                    pageFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(theme.cardRoundnessDp.dp))
            .background(theme.cardBgColor.copy(alpha = 0.85f))
            .focusRequester(pageFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!isActivePage || anyChatDialogOpen) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent

                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        if (settingsMenuOpen) {
                            settingsMenuOpen = false
                        } else {
                            keyboardController?.hide()
                            onBack()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                // When input TextField is focused, allow normal typing!
                // Only intercept UP/DOWN or ENTER (to move to Send button).
                if (focusZone == 3) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (native.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                focusZone = when {
                                    messages.isNotEmpty() -> {
                                        selectedMsgIndex = messages.lastIndex
                                        2
                                    }
                                    showModelPrompt -> 1
                                    else -> 0
                                }
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                focusZone = 4
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                if (messageText.isNotBlank() && !isGenerating) {
                                    focusZone = 4
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusZone) {
                            0 -> {
                                if (headerCol == 0) {
                                    onBack()
                                } else {
                                    settingsDialogOpen = true
                                }
                            }
                            1 -> {
                                try {
                                    modelPicker.launch(arrayOf("*/*"))
                                } catch (e: Exception) {
                                    messages.add(ChatMessage(isUser = false, text = "Could not open file picker: ${e.localizedMessage} ♡"))
                                }
                            }
                            2 -> {
                                messages.getOrNull(selectedMsgIndex)?.let { msg ->
                                    selectedMessageForOptions = msg
                                }
                            }
                            4 -> {
                                sendMessageAction()
                                focusZone = 3
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        when (focusZone) {
                            0 -> focusZone = 4
                            1 -> focusZone = 0
                            2 -> {
                                if (selectedMsgIndex > 0) {
                                    selectedMsgIndex--
                                } else if (showModelPrompt) {
                                    focusZone = 1
                                } else {
                                    focusZone = 0
                                }
                            }
                            4 -> focusZone = 3
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when (focusZone) {
                            0 -> {
                                focusZone = when {
                                    showModelPrompt -> 1
                                    messages.isNotEmpty() -> {
                                        selectedMsgIndex = 0
                                        2
                                    }
                                    else -> 3
                                }
                            }
                            1 -> {
                                focusZone = if (messages.isNotEmpty()) {
                                    selectedMsgIndex = 0
                                    2
                                } else {
                                    3
                                }
                            }
                            2 -> {
                                if (selectedMsgIndex < messages.lastIndex) {
                                    selectedMsgIndex++
                                } else {
                                    focusZone = 3
                                }
                            }
                            4 -> focusZone = 0
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when (focusZone) {
                            0 -> {
                                if (headerCol == 1) {
                                    headerCol = 0
                                } else {
                                    onBack()
                                }
                            }
                            4 -> focusZone = 3
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when (focusZone) {
                            0 -> headerCol = 1
                        }
                        true
                    }
                    else -> false
                }
            }
            .padding(14.dp)
    ) {
        // top header with Home button, mascot, title, and settings button <3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KawaiiDialogPillButton(
                text = "◀ Home",
                isFocused = focusZone == 0 && headerCol == 0,
                onClick = {
                    focusZone = 0
                    headerCol = 0
                    onBack()
                }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PixelArtMascot(expression = mascotExpression, size = 48.dp)
                Text(
                    text = when (selectedBackend) {
                        "PC Ollama" -> "✧ remote llm ✧"
                        "Local SmolLM2" -> "✧ local llm ✧"
                        else -> if (ollamaConnected == true) "✧ remote llm ✧" else "✧ local llm ✧"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.fgTextColor
                )
            }

            // Three-dot menu / settings button
            Box {
                KawaiiDialogPillButton(
                    text = "⚙",
                    isFocused = focusZone == 0 && headerCol == 1,
                    onClick = {
                        focusZone = 0
                        headerCol = 1
                        settingsMenuOpen = true
                    }
                )

                DropdownMenu(
                    expanded = settingsMenuOpen,
                    onDismissRequest = { settingsMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Chat settings ♡", color = theme.fgTextColor) },
                        onClick = {
                            settingsMenuOpen = false
                            settingsDialogOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Clear chat ♡",
                                color = if (!isGenerating && messages.isNotEmpty()) destructiveColor else Color.LightGray
                            )
                        },
                        enabled = !isGenerating && messages.isNotEmpty(),
                        onClick = {
                            settingsMenuOpen = false
                            messages.clear()
                            coroutineScope.launch(Dispatchers.IO) {
                                saveChatHistory(context, emptyList())
                            }
                        }
                    )
                }
            }
        }

        Text(
            text = when (selectedBackend) {
                "PC Ollama" -> if (ollamaConnected == true) "♡ PC connected • $selectedOllamaModel ♡" else "♡ PC Ollama mode • connecting... ♡"
                "Local SmolLM2" -> if (resolvedModelPath.isNotEmpty()) "♡ Local SmolLM2 ready ♡" else "♡ Local mode • model needed ♡"
                else -> when (ollamaConnected) {
                    true -> "♡ PC connected • $selectedOllamaModel ♡"
                    false -> if (resolvedModelPath.isNotEmpty()) "♡ Local AI ready • SmolLM2 ♡" else "♡ PC unavailable • local model needed ♡"
                    null -> "♡ Checking AI connection... ♡"
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            fontSize = 11.sp,
            color = theme.appActionColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // scrollable chat conversation lazy column :3
        LazyColumn(
            state = chatListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showModelPrompt) {
                item(key = "select_model_prompt") {
                    val promptFocused = focusZone == 1
                    val promptShape = RoundedCornerShape(16.dp)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (promptFocused) 2.5.dp else 1.5.dp,
                                color = if (promptFocused) theme.appActionColor else theme.greetingOutlineColor,
                                shape = promptShape
                            )
                            .clickable {
                                focusZone = 1
                                try {
                                    modelPicker.launch(arrayOf("*/*"))
                                } catch (e: Exception) {
                                    messages.add(ChatMessage(isUser = false, text = "Could not open file picker: ${e.localizedMessage} ♡"))
                                }
                            },
                        shape = promptShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (promptFocused) theme.selectedContainerColor else theme.greetingBgColor
                        )
                    ) {
                        Text(
                            text = "📁 Press OK or tap here to select your SmolLM2 .gguf model file ♡",
                            modifier = Modifier.padding(14.dp),
                            color = theme.fgTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (messages.isEmpty() && !isGenerating) {
                item(key = "welcome_prompt") {
                    Text(
                        text = "♡ send me a message to try the chat ♡",
                        color = theme.fgTextColor,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            items(messages.size, key = { index -> messages[index].id }) { index ->
                val msg = messages[index]
                val isMsgFocused = focusZone == 2 && selectedMsgIndex == index
                val bubbleShape = RoundedCornerShape(18.dp)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                ) {
                    if (!msg.isUser && msg.status != null) {
                        Text(
                            text = "🌐 ${msg.status}",
                            fontSize = 11.sp,
                            color = theme.appActionColor,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth(if (msg.isUser) 0.88f else 1f)
                            .border(
                                width = if (isMsgFocused) 2.5.dp else 1.dp,
                                color = if (isMsgFocused) theme.appActionColor else theme.greetingOutlineColor,
                                shape = bubbleShape
                            )
                            .pointerInput(msg.id) {
                                detectTapGestures(
                                    onTap = {
                                        focusZone = 2
                                        selectedMsgIndex = index
                                    },
                                    onLongPress = {
                                        focusZone = 2
                                        selectedMsgIndex = index
                                        selectedMessageForOptions = msg
                                    }
                                )
                            },
                        shape = bubbleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isMsgFocused -> theme.selectedContainerColor
                                msg.isUser -> theme.greetingBgColor
                                else -> softSurfaceColor
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = msg.text,
                                color = theme.fgTextColor,
                                fontSize = chatFontSize.sp
                            )

                            if (msg.sources.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Sources:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.appActionColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                msg.sources.forEach { src ->
                                    Text(
                                        text = "• ${src.title}",
                                        fontSize = 11.sp,
                                        color = theme.appActionColor,
                                        modifier = Modifier
                                            .clickable {
                                                try {
                                                    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(src.url))
                                                    context.startActivity(viewIntent)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            .padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Typing indicator positioned immediately above the input box <3
        if (isGenerating) {
            Spacer(modifier = Modifier.height(4.dp))
            AnimatedTypingIndicator(
                statusText = activeStatus ?: "Thinking...",
                showReasoning = showReasoning
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // bottom message input text field and send button <3
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val fieldShape = RoundedCornerShape(18.dp)
            TextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = if (focusZone == 3) 2.5.dp else 1.dp,
                        color = if (focusZone == 3) theme.appActionColor else theme.greetingOutlineColor,
                        shape = fieldShape
                    )
                    .focusRequester(inputFocusRequester),
                placeholder = { Text(if (isGenerating) "thinking..." else "say something...") },
                enabled = !isGenerating,
                shape = fieldShape,
                textStyle = TextStyle(
                    color = theme.fgTextColor,
                    fontSize = 14.sp
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = softSurfaceColor,
                    unfocusedContainerColor = softSurfaceColor.copy(alpha = 0.85f),
                    focusedTextColor = theme.fgTextColor,
                    unfocusedTextColor = theme.fgTextColor,
                    focusedPlaceholderColor = theme.fgTextColor.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = theme.fgTextColor.copy(alpha = 0.6f),
                    disabledPlaceholderColor = theme.fgTextColor.copy(alpha = 0.45f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledContainerColor = softSurfaceColor.copy(alpha = 0.6f),
                    disabledTextColor = theme.fgTextColor,
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(6.dp))

            KawaiiDialogPillButton(
                text = "Send",
                isFocused = focusZone == 4,
                enabled = !isGenerating && messageText.isNotBlank(),
                isPrimary = true,
                onClick = {
                    focusZone = 4
                    sendMessageAction()
                }
            )
        }
    }

    // Message options dialog (triggered on long press or OK on a focused message)
    if (selectedMessageForOptions != null) {
        val selectedMsg = selectedMessageForOptions!!
        MessageOptionsMenu(
            msg = selectedMsg,
            onDismiss = { selectedMessageForOptions = null },
            onResend = {
                messageText = selectedMsg.text
                selectedMessageForOptions = null
                focusZone = 3
                Toast.makeText(context, "Text loaded into input box ♡", Toast.LENGTH_SHORT).show()
            },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("chat_message", selectedMsg.text)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, "Copied to clipboard! ♡", Toast.LENGTH_SHORT).show()
                selectedMessageForOptions = null
            },
            onDelete = {
                messages.remove(selectedMsg)
                val snapshot = messages.toList()
                coroutineScope.launch(Dispatchers.IO) {
                    saveChatHistory(context, snapshot)
                }
                Toast.makeText(context, "Message deleted ♡", Toast.LENGTH_SHORT).show()
                selectedMessageForOptions = null
            }
        )
    }

    // Settings dialog popup
    if (settingsDialogOpen) {
        ChatSettingsDialog(
            onDismiss = { settingsDialogOpen = false },
            onClearChat = {
                messages.clear()
                coroutineScope.launch(Dispatchers.IO) {
                    saveChatHistory(context, emptyList())
                }
                settingsDialogOpen = false
            },
            canClearChat = !isGenerating && messages.isNotEmpty(),
            selectedBackend = selectedBackend,
            onBackendChange = updateBackend,
            selectedOllamaModel = selectedOllamaModel,
            onOllamaModelChange = updateOllamaModel,
            ollamaModels = ollamaModels,
            onRefreshModels = refreshModels,
            isRefreshingModels = isRefreshingModels,
            modelFetchStatus = modelFetchStatus,
            chatFontSize = chatFontSize,
            onFontSizeChange = updateFontSize,
            enableReasoning = enableReasoning,
            onEnableReasoningChange = updateEnableReasoning,
            showReasoning = showReasoning,
            onShowReasoningChange = updateShowReasoning
        )
    }
}

// Dialog for message long-press options (resend, copy, delete) ♡
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageOptionsMenu(
    msg: ChatMessage,
    onDismiss: () -> Unit,
    onResend: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    var sawCenterDown by remember { mutableStateOf(false) }

    val actions = remember(msg.isUser) {
        buildList<Triple<String, Boolean, () -> Unit>> {
            if (msg.isUser) {
                add(Triple("🔄 Resend / Edit text", false, onResend))
            }
            add(Triple("📋 Copy text", false, onCopy))
            add(Triple("🗑️ Delete message", true, onDelete))
            add(Triple("Cancel ♡", false, onDismiss))
        }
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        actions.getOrNull(selectedIndex)?.third?.invoke()
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else actions.lastIndex
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        selectedIndex = if (selectedIndex < actions.lastIndex) selectedIndex + 1 else 0
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(theme.cardRoundnessDp.dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .border(2.5.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (msg.isUser) "Message Options ♡" else "Response Options ♡",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.fgTextColor
                )

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = softSurfaceColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg.text,
                        fontSize = 12.sp,
                        color = theme.fgTextColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                actions.forEachIndexed { idx, (label, isDestructive, action) ->
                    KawaiiDialogItem(
                        text = label,
                        isFocused = selectedIndex == idx,
                        isDestructive = isDestructive,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedIndex = idx
                            action()
                        }
                    )
                }
            }
        }
    }
}

// Compact, animated typing indicator positioned right above the input box <3
@Composable
fun AnimatedTypingIndicator(
    statusText: String,
    showReasoning: Boolean,
    reasoningText: String? = null
) {
    val theme = LocalLauncherTheme.current
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots_transition")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1_alpha"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2_alpha"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.selectedContainerColor.copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(theme.appActionColor.copy(alpha = dot1Alpha))
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(theme.appActionColor.copy(alpha = dot2Alpha))
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(theme.appActionColor.copy(alpha = dot3Alpha))
                    )
                }

                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = theme.fgTextColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showReasoning && !reasoningText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💭 $reasoningText",
                    fontSize = 11.sp,
                    color = theme.fgTextColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Compact scrollable settings dialog for 480x800 display with full D-pad support ♡
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsDialog(
    onDismiss: () -> Unit,
    onClearChat: () -> Unit = {},
    canClearChat: Boolean = false,
    selectedBackend: String,
    onBackendChange: (String) -> Unit,
    selectedOllamaModel: String,
    onOllamaModelChange: (String) -> Unit,
    ollamaModels: List<String>,
    onRefreshModels: () -> Unit,
    isRefreshingModels: Boolean,
    modelFetchStatus: String?,
    chatFontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    enableReasoning: Boolean,
    onEnableReasoningChange: (Boolean) -> Unit,
    showReasoning: Boolean,
    onShowReasoningChange: (Boolean) -> Unit
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var sawCenterDown by remember { mutableStateOf(false) }

    val showOllamaSection = selectedBackend == "Auto" || selectedBackend == "PC Ollama"
    val displayModels = remember(ollamaModels, selectedOllamaModel) {
        if (ollamaModels.isEmpty()) listOf(selectedOllamaModel) else ollamaModels
    }

    // Focus rows:
    // 0: Backend "Auto"
    // 1: Backend "PC Ollama"
    // 2: Backend "Local SmolLM2"
    // 3: Refresh Ollama models button (when showOllamaSection)
    // 4: Cycle Ollama model selector (when showOllamaSection; LEFT/RIGHT/OK cycles models)
    // 5: Font size slider (LEFT/RIGHT adjusts font size)
    // 6: Enable reasoning switch
    // 7: Show reasoning in chat switch
    // 8: Clear chat button
    // 9: Done button
    var focusRow by remember { mutableIntStateOf(0) }

    LaunchedEffect(focusRow) {
        val target = ((focusRow / 9f) * scrollState.maxValue).toInt()
        scrollState.animateScrollTo(target)
    }

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusRow) {
                            0 -> onBackendChange("Auto")
                            1 -> onBackendChange("PC Ollama")
                            2 -> onBackendChange("Local SmolLM2")
                            3 -> if (!isRefreshingModels) onRefreshModels()
                            4 -> {
                                if (displayModels.isNotEmpty()) {
                                    val idx = displayModels.indexOf(selectedOllamaModel)
                                    val next = displayModels[(idx + 1).mod(displayModels.size)]
                                    onOllamaModelChange(next)
                                }
                            }
                            5 -> {
                                val next = if (chatFontSize >= 22f) 11f else (chatFontSize + 1f).coerceAtMost(22f)
                                onFontSizeChange(next)
                            }
                            6 -> onEnableReasoningChange(!enableReasoning)
                            7 -> onShowReasoningChange(!showReasoning)
                            8 -> if (canClearChat) onClearChat()
                            9 -> onDismiss()
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        focusRow = when {
                            focusRow == 0 -> 9
                            focusRow == 5 && !showOllamaSection -> 2
                            focusRow == 9 && !canClearChat -> 7
                            else -> focusRow - 1
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusRow = when {
                            focusRow == 2 && !showOllamaSection -> 5
                            focusRow == 7 && !canClearChat -> 9
                            focusRow == 9 -> 0
                            else -> focusRow + 1
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when (focusRow) {
                            4 -> {
                                if (displayModels.isNotEmpty()) {
                                    val idx = displayModels.indexOf(selectedOllamaModel).coerceAtLeast(0)
                                    val prev = displayModels[(idx - 1).mod(displayModels.size)]
                                    onOllamaModelChange(prev)
                                }
                            }
                            5 -> {
                                onFontSizeChange((chatFontSize - 1f).coerceIn(11f, 22f))
                            }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when (focusRow) {
                            4 -> {
                                if (displayModels.isNotEmpty()) {
                                    val idx = displayModels.indexOf(selectedOllamaModel).coerceAtLeast(0)
                                    val next = displayModels[(idx + 1).mod(displayModels.size)]
                                    onOllamaModelChange(next)
                                }
                            }
                            5 -> {
                                onFontSizeChange((chatFontSize + 1f).coerceIn(11f, 22f))
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(theme.cardRoundnessDp.dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 520.dp)
                .border(2.5.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = "✧ Chat Settings ✧",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.fgTextColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Section 1: Backend selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "AI Backend",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.appActionColor
                        )

                        listOf("Auto", "PC Ollama", "Local SmolLM2").forEachIndexed { idx, backend ->
                            val isRowFocused = focusRow == idx
                            val rowShape = RoundedCornerShape(10.dp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(rowShape)
                                    .background(
                                        if (isRowFocused) theme.selectedContainerColor else Color.Transparent,
                                        rowShape
                                    )
                                    .border(
                                        width = if (isRowFocused) 2.dp else 0.dp,
                                        color = if (isRowFocused) theme.appActionColor else Color.Transparent,
                                        shape = rowShape
                                    )
                                    .clickable {
                                        focusRow = idx
                                        onBackendChange(backend)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = (selectedBackend == backend),
                                    onClick = {
                                        focusRow = idx
                                        onBackendChange(backend)
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = theme.appActionColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (backend) {
                                        "Auto" -> "Auto (PC first, local fallback)"
                                        "PC Ollama" -> "PC Ollama (Tailscale)"
                                        else -> "Local SmolLM2 (Offline)"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (isRowFocused) FontWeight.Bold else FontWeight.SemiBold,
                                    color = theme.fgTextColor
                                )
                            }
                        }
                    }

                    // Section 2: Ollama model selection (if Auto or PC Ollama)
                    if (showOllamaSection) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Ollama Model",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.appActionColor,
                                    modifier = Modifier.weight(1f)
                                )
                                KawaiiDialogPillButton(
                                    text = if (isRefreshingModels) "Fetching..." else "Refresh ↺",
                                    isFocused = focusRow == 3,
                                    enabled = !isRefreshingModels,
                                    onClick = {
                                        focusRow = 3
                                        onRefreshModels()
                                    }
                                )
                            }

                            val modelRowFocused = focusRow == 4
                            val modelShape = RoundedCornerShape(12.dp)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (modelRowFocused) 2.5.dp else 1.dp,
                                        color = if (modelRowFocused) theme.appActionColor else theme.greetingOutlineColor,
                                        shape = modelShape
                                    )
                                    .clickable {
                                        focusRow = 4
                                        if (displayModels.isNotEmpty()) {
                                            val idx = displayModels.indexOf(selectedOllamaModel)
                                            val next = displayModels[(idx + 1).mod(displayModels.size)]
                                            onOllamaModelChange(next)
                                        }
                                    },
                                shape = modelShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (modelRowFocused) theme.selectedContainerColor else softSurfaceColor
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedOllamaModel,
                                        fontSize = 12.sp,
                                        color = theme.fgTextColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "◀ / ▶",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.appActionColor
                                    )
                                }
                            }

                            if (!modelFetchStatus.isNullOrBlank()) {
                                Text(
                                    text = modelFetchStatus,
                                    fontSize = 11.sp,
                                    color = theme.fgTextColor
                                )
                            }
                        }
                    }

                    // Section 3: Font size
                    val sliderFocused = focusRow == 5
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (sliderFocused) theme.selectedContainerColor else Color.Transparent
                            )
                            .border(
                                width = if (sliderFocused) 2.dp else 0.dp,
                                color = if (sliderFocused) theme.appActionColor else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Chat font size: ${chatFontSize.toInt()} sp",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.appActionColor
                            )
                            if (sliderFocused) {
                                Text(
                                    text = "◀ / ▶",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.appActionColor
                                )
                            }
                        }
                        Slider(
                            value = chatFontSize,
                            onValueChange = {
                                focusRow = 5
                                onFontSizeChange(it)
                            },
                            valueRange = 11f..22f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = theme.appActionColor,
                                activeTrackColor = theme.cardTextColor
                            )
                        )
                    }

                    // Section 4: Reasoning controls
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Reasoning / Thinking",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.appActionColor
                        )

                        val row6Focused = focusRow == 6
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (row6Focused) theme.selectedContainerColor else Color.Transparent
                                )
                                .border(
                                    width = if (row6Focused) 2.dp else 0.dp,
                                    color = if (row6Focused) theme.appActionColor else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    focusRow = 6
                                    onEnableReasoningChange(!enableReasoning)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable reasoning",
                                    fontSize = 12.sp,
                                    color = theme.fgTextColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Ask model to think before answering",
                                    fontSize = 10.sp,
                                    color = theme.fgTextColor.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = enableReasoning,
                                onCheckedChange = {
                                    focusRow = 6
                                    onEnableReasoningChange(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = theme.appActionColor
                                )
                            )
                        }

                        val row7Focused = focusRow == 7
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (row7Focused) theme.selectedContainerColor else Color.Transparent
                                )
                                .border(
                                    width = if (row7Focused) 2.dp else 0.dp,
                                    color = if (row7Focused) theme.appActionColor else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    focusRow = 7
                                    onShowReasoningChange(!showReasoning)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Show reasoning in chat",
                                    fontSize = 12.sp,
                                    color = theme.fgTextColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Display thinking process in bubbles",
                                    fontSize = 10.sp,
                                    color = theme.fgTextColor.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = showReasoning,
                                onCheckedChange = {
                                    focusRow = 7
                                    onShowReasoningChange(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = theme.appActionColor
                                )
                            )
                        }

                        if (canClearChat) {
                            KawaiiDialogItem(
                                text = "🗑 Clear chat history",
                                isFocused = focusRow == 8,
                                isDestructive = true,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    focusRow = 8
                                    onClearChat()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    KawaiiDialogPillButton(
                        text = "Done ♡",
                        isFocused = focusRow == 9,
                        isPrimary = true,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

// checks if PC Ollama API is reachable :3
private fun checkOllamaConnection(): Boolean {
    var connection: HttpURLConnection? = null
    return try {
        connection = URL("http://100.103.158.68:11434/api/tags").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 2500
        connection.readTimeout = 2500
        connection.responseCode == HttpURLConnection.HTTP_OK
    } catch (e: Exception) {
        false
    } finally {
        connection?.disconnect()
    }
}

// fetches installed Ollama models from /api/tags <3
fun fetchOllamaModels(): List<String> {
    var connection: HttpURLConnection? = null
    return try {
        val url = URL("http://100.103.158.68:11434/api/tags")
        connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 3000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }

        if (connection.responseCode != 200) {
            return emptyList()
        }

        val responseString = connection.inputStream.bufferedReader().use { it.readText() }
        val rootObj = JSONObject(responseString)
        val modelsArray = rootObj.optJSONArray("models") ?: return emptyList()

        val list = mutableListOf<String>()
        for (i in 0 until modelsArray.length()) {
            val item = modelsArray.getJSONObject(i)
            val name = item.optString("name", "")
            if (name.isNotBlank()) {
                list.add(name)
            }
        }
        list
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    } finally {
        connection?.disconnect()
    }
}

// helper to try Ollama at http://100.103.158.68:11434/api/chat with specified model and options
fun tryOllama(
    messages: List<ChatMessage>,
    searchEvidence: List<SearchResult>,
    modelName: String = "qwen3-fast:latest",
    enableReasoning: Boolean = false,
    showReasoning: Boolean = false
): OllamaResponse {
    Log.e("M120Ollama", "tryOllama called for model: $modelName")

    var connection: HttpURLConnection? = null
    return try {
        val url = URL("http://100.103.158.68:11434/api/chat")
        connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 60000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        val jsonBody = JSONObject().apply {
            put("model", modelName)
            put("stream", false)
            put("think", enableReasoning)

            val messagesArray = JSONArray()

            val systemContent = StringBuilder()
            systemContent.append(
                "You are a friendly, caring, concise assistant. " +
                    "Answer the user's latest message directly. " +
                    "Do not repeat these instructions or describe yourself instead of answering. " +
                    "Be warm and playful when appropriate, but prioritize accuracy. " +
                    "If you do not know something, say so."
            )
            if (enableReasoning) {
                systemContent.append("\nConsider the question carefully before answering.")
            }
            if (searchEvidence.isNotEmpty()) {
                systemContent.append("\nUse the following web evidence when relevant. Do not invent details:\n")
                for ((idx, src) in searchEvidence.withIndex()) {
                    systemContent.append("${idx + 1}. ").append(src.title).append(": ").append(src.snippet).append("\n")
                }
            }
            messagesArray.put(JSONObject().put("role", "system").put("content", systemContent.toString()))

            val recent = messages.takeLast(6)
            for (msg in recent) {
                val role = if (msg.isUser) "user" else "assistant"
                messagesArray.put(JSONObject().put("role", role).put("content", msg.text))
            }

            put("messages", messagesArray)
        }

        connection.outputStream.use { os ->
            val input = jsonBody.toString().toByteArray(Charsets.UTF_8)
            os.write(input, 0, input.size)
        }

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            val errorBody = connection.errorStream
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            val parsedError = try {
                if (errorBody.isNotBlank()) JSONObject(errorBody).optString("error", errorBody) else ""
            } catch (e: Exception) {
                errorBody
            }
            val errMessage = if (parsedError.isNotBlank()) "HTTP $responseCode: $parsedError" else "HTTP status $responseCode"
            Log.e("M120Ollama", "Ollama returned error: $errMessage")
            return OllamaResponse(error = errMessage)
        }

        val responseString = connection.inputStream.bufferedReader().use { it.readText() }

        val rootObj = JSONObject(responseString)
        val messageObj = rootObj.optJSONObject("message")

        val content = messageObj?.optString("content").orEmpty()
            .ifBlank { rootObj.optString("response", "") }
        val thinkingText = messageObj?.optString("thinking").orEmpty()

        val resultText = StringBuilder()
        if (showReasoning && thinkingText.isNotBlank()) {
            resultText.append("💭 Thinking process:\n")
            resultText.append(thinkingText.trim())
            if (content.isNotBlank()) resultText.append("\n\n")
        }
        resultText.append(content)

        val finalString = resultText.toString().trim()
        if (finalString.isNotBlank()) {
            OllamaResponse(content = finalString)
        } else {
            OllamaResponse(error = "Ollama returned an empty response.")
        }
    } catch (e: java.net.SocketTimeoutException) {
        val msg = "Connection timed out (60s). The PC model may still be loading."
        Log.e("M120Ollama", msg, e)
        OllamaResponse(error = msg)
    } catch (e: java.net.ConnectException) {
        val msg = "Could not connect to Ollama at 100.103.158.68:11434 (${e.message})."
        Log.e("M120Ollama", msg, e)
        OllamaResponse(error = msg)
    } catch (e: Exception) {
        val msg = "${e.javaClass.simpleName}: ${e.message}"
        Log.e("M120Ollama", "Ollama request failed: $msg", e)
        OllamaResponse(error = msg)
    } finally {
        connection?.disconnect()
    }
}
