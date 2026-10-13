package com.tnyemii.m120launcher

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tnyemii.m120launcher.ui.theme.cardTextColor
import kotlinx.coroutines.Dispatchers
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE) }

    var ollamaConnected by remember { mutableStateOf<Boolean?>(null) }

    var messageText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var activeStatus by remember { mutableStateOf<String?>(null) }

    // Selected message for options dialog (long-press)
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
        isRefreshingModels = true
        modelFetchStatus = "Fetching models from PC..."
        coroutineScope.launch(Dispatchers.IO) {
            val fetched = fetchOllamaModels()
            withContext(Dispatchers.Main) {
                isRefreshingModels = false
                if (fetched.isNotEmpty()) {
                    val combined = (listOf(selectedOllamaModel) + fetched).distinct()
                    ollamaModels = combined
                    modelFetchStatus = "Connected • ${fetched.size} model(s) found ♡"
                } else {
                    modelFetchStatus = "Could not fetch models (PC offline/unreachable) ♡"
                }
            }
        }
    }

    // load saved chat history from local json file <3
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            addAll(loadChatHistory(context))
        }
    }

    val chatListState = rememberLazyListState()

    // auto scroll to bottom when new messages arrive or when sending
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            chatListState.animateScrollToItem(messages.size - 1)
        }
    }

    // check if model exists in internal storage or sdcard path :3
    var resolvedModelPath by remember {
        mutableStateOf(
            run {
                val internalFile = java.io.File(context.filesDir, "smol_model.gguf")
                val legacyInternal = java.io.File(context.filesDir, "SmolLM2-360M-Instruct.Q4_K_M.gguf")
                val externalPath = "/sdcard/SmolLM2/SmolLM2-360M-Instruct.Q4_K_M.gguf"
                if (internalFile.exists() && internalFile.length() > 0) {
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
            }
        )
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

    // auto copy model file from sdcard to app private storage on startup <3
    LaunchedEffect(Unit) {
        val internalFile = java.io.File(context.filesDir, "smol_model.gguf")
        val externalPath = "/sdcard/SmolLM2/SmolLM2-360M-Instruct.Q4_K_M.gguf"
        if ((!internalFile.exists() || internalFile.length() == 0L)) {
            val extFile = java.io.File(externalPath)
            if (extFile.exists()) {
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

    // periodic background connection check
    LaunchedEffect(Unit) {
        while (true) {
            val connected = withContext(Dispatchers.IO) {
                checkOllamaConnection()
            }
            ollamaConnected = connected
            delay(5 * 60 * 1000L)
        }
    }

    // fetch models when opening settings dialog if needed
    LaunchedEffect(settingsDialogOpen) {
        if (settingsDialogOpen && (selectedBackend == "Auto" || selectedBackend == "PC Ollama")) {
            refreshModels()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xAEEECFFF))
            .padding(16.dp)
    ) {
        // top header with title, mascot, and three-dot settings menu <3
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PixelArtMascot(expression = mascotExpression, size = 32.dp)
                Text(
                    text = when (selectedBackend) {
                        "PC Ollama" -> "✧･ﾟ ollama ✧˖°"
                        "Local SmolLM2" -> "✧･ﾟ local llm ✧˖°"
                        else -> if (ollamaConnected == true) "✧･ﾟ ollama ✧˖°" else "✧･ﾟ local llm ✧˖°"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = cardTextColor
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Three-dot menu button
            Box {
                TextButton(
                    onClick = { settingsMenuOpen = true }
                ) {
                    Text(
                        text = "⋮",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB77BBF)
                    )
                }

                DropdownMenu(
                    expanded = settingsMenuOpen,
                    onDismissRequest = { settingsMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Chat settings ♡", color = Color(0xFF684A7C)) },
                        onClick = {
                            settingsMenuOpen = false
                            settingsDialogOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Clear chat ♡",
                                color = if (!isGenerating && messages.isNotEmpty()) Color(0xFFB77BBF) else Color.LightGray
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
            fontSize = 12.sp,
            color = Color(0xFFB77BBF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(12.dp))

        // scrollable chat conversation lazy column :3
        LazyColumn(
            state = chatListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (resolvedModelPath.isEmpty() && selectedBackend == "Local SmolLM2") {
                item(key = "select_model_prompt") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    modelPicker.launch(arrayOf("*/*"))
                                } catch (e: Exception) {
                                    messages.add(ChatMessage(isUser = false, text = "Could not open file picker: ${e.localizedMessage} ♡"))
                                }
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE9D9FF))
                    ) {
                        Text(
                            text = "📁 Tap here to select your SmolLM2 .gguf model file ♡",
                            modifier = Modifier.padding(16.dp),
                            color = Color(0xFF684A7C),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (messages.isEmpty() && !isGenerating) {
                item(key = "welcome_prompt") {
                    Text(
                        text = "hi!! ♡ send me a message to try the chat",
                        color = Color(0xFF684A7C),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            items(messages.size, key = { index -> messages[index].id }) { index ->
                val msg = messages[index]

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                ) {
                    if (!msg.isUser && msg.status != null) {
                        Text(
                            text = "🌐 ${msg.status}",
                            fontSize = 11.sp,
                            color = Color(0xFFB77BBF),
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth(if (msg.isUser) 0.88f else 1f)
                            .pointerInput(msg.id) {
                                detectTapGestures(
                                    onLongPress = {
                                        selectedMessageForOptions = msg
                                    }
                                )
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.isUser) Color(0xFFE9D9FF) else Color.White
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = msg.text,
                                color = Color(0xFF684A7C),
                                fontSize = chatFontSize.sp
                            )

                            if (msg.sources.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Sources:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9C6DF5)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                msg.sources.forEach { src ->
                                    Text(
                                        text = "• ${src.title}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF9C6DF5),
                                        modifier = Modifier
                                            .clickable {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(src.url))
                                                    context.startActivity(intent)
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
            TextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (isGenerating) "thinking..." else "say something...") },
                enabled = !isGenerating,
                shape = RoundedCornerShape(20.dp),
                textStyle = TextStyle(
                    color = Color(0xFF684A7C),
                    fontSize = 14.sp
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color(0xFFD36BFF),
                    unfocusedTextColor = Color(0xFF8658A1),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledContainerColor = Color.White,
                    disabledTextColor = Color(0xFF684A7C),
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(6.dp))

            TextButton(
                onClick = {
                    val sent = messageText.trim()
                    if (sent.isNotEmpty() && !isGenerating) {
                        messages.add(ChatMessage(isUser = true, text = sent))
                        coroutineScope.launch(Dispatchers.IO) {
                            saveChatHistory(context, messages)
                        }
                        messageText = ""
                        isGenerating = true

                        val needSearch = WebSearchClassifier.shouldSearchWeb(sent)
                        val isOnline = WebSearchClient.isNetworkAvailable(context)

                        coroutineScope.launch(Dispatchers.IO) {
                            var searchResults = emptyList<SearchResult>()
                            var webStatus: String? = null

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
                                                        NativeLlm.generate(resolvedModelPath, fullPrompt)
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
                                                    NativeLlm.generate(resolvedModelPath, fullPrompt)
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
                                withContext(Dispatchers.Main) {
                                    activeStatus = null
                                    isGenerating = false
                                }
                                saveChatHistory(context, messages)
                            }
                        }
                    }
                },
                enabled = !isGenerating
            ) {
                Text(
                    text = "Send",
                    color = if (isGenerating) Color.LightGray else Color(0xFF9C6DF5),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Message options dialog (triggered on long press)
    if (selectedMessageForOptions != null) {
        val selectedMsg = selectedMessageForOptions!!
        MessageOptionsMenu(
            msg = selectedMsg,
            onDismiss = { selectedMessageForOptions = null },
            onResend = {
                messageText = selectedMsg.text
                selectedMessageForOptions = null
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
                coroutineScope.launch(Dispatchers.IO) {
                    saveChatHistory(context, messages)
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
@Composable
fun MessageOptionsMenu(
    msg: ChatMessage,
    onDismiss: () -> Unit,
    onResend: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel ♡", color = Color(0xFFB77BBF), fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Text(
                text = if (msg.isUser) "Message Options ♡" else "Response Options ♡",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF684A7C)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Text snippet preview
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg.text,
                        fontSize = 12.sp,
                        color = Color(0xFF684A7C),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Resend / Edit (if user message)
                if (msg.isUser) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResend() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE9D9FF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔄 Resend / Edit text",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF684A7C)
                            )
                        }
                    }
                }

                // Copy Text
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopy() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE9D9FF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📋 Copy text",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF684A7C)
                        )
                    }
                }

                // Delete Message
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDelete() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🗑️ Delete message",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD36BFF)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFFFF4FA),
        shape = RoundedCornerShape(20.dp)
    )
}

// Compact, animated typing indicator positioned right above the input box <3
@Composable
fun AnimatedTypingIndicator(
    statusText: String,
    showReasoning: Boolean,
    reasoningText: String? = null
) {
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE9D9FF).copy(alpha = 0.85f))
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
                            .background(Color(0xFF9C6DF5).copy(alpha = dot1Alpha))
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF9C6DF5).copy(alpha = dot2Alpha))
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF9C6DF5).copy(alpha = dot3Alpha))
                    )
                }

                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = Color(0xFF684A7C),
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
                    color = Color(0xFF8658A1),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Compact scrollable settings dialog for 480x800 display ♡
@Composable
fun ChatSettingsDialog(
    onDismiss: () -> Unit,
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
    var modelMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done ♡", color = Color(0xFF9C6DF5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        title = {
            Text(
                text = "✧ Chat Settings ✧",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF684A7C)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Backend selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "AI Backend",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9C6DF5)
                    )

                    listOf("Auto", "PC Ollama", "Local SmolLM2").forEach { backend ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBackendChange(backend) }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = (selectedBackend == backend),
                                onClick = { onBackendChange(backend) },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF9C6DF5))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (backend) {
                                    "Auto" -> "Auto (PC first, local fallback)"
                                    "PC Ollama" -> "PC Ollama (Tailscale)"
                                    else -> "Local SmolLM2 (Offline)"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF684A7C)
                            )
                        }
                    }
                }

                // Section 2: Ollama model selection (if Auto or PC Ollama)
                if (selectedBackend == "Auto" || selectedBackend == "PC Ollama") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Ollama Model",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9C6DF5),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onRefreshModels,
                                enabled = !isRefreshingModels
                            ) {
                                Text(
                                    text = if (isRefreshingModels) "Fetching..." else "Refresh ↻",
                                    fontSize = 11.sp,
                                    color = Color(0xFFB77BBF)
                                )
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { modelMenuExpanded = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
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
                                        color = Color(0xFF684A7C),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("▼", fontSize = 10.sp, color = Color(0xFFB77BBF))
                                }
                            }

                            DropdownMenu(
                                expanded = modelMenuExpanded,
                                onDismissRequest = { modelMenuExpanded = false }
                            ) {
                                val displayModels = if (ollamaModels.isEmpty()) {
                                    listOf(selectedOllamaModel)
                                } else {
                                    ollamaModels
                                }
                                displayModels.forEach { modelName ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = modelName,
                                                fontSize = 12.sp,
                                                color = Color(0xFF684A7C)
                                            )
                                        },
                                        onClick = {
                                            onOllamaModelChange(modelName)
                                            modelMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (!modelFetchStatus.isNullOrBlank()) {
                            Text(
                                text = modelFetchStatus,
                                fontSize = 11.sp,
                                color = Color(0xFFB77BBF)
                            )
                        }
                    }
                }

                // Section 3: Font size
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Chat font size: ${chatFontSize.toInt()} sp",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9C6DF5)
                    )
                    Slider(
                        value = chatFontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 11f..22f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF9C6DF5),
                            activeTrackColor = Color(0xFFE9D9FF)
                        )
                    )
                }

                // Section 4: Reasoning controls
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Reasoning / Thinking",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9C6DF5)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable reasoning",
                                fontSize = 12.sp,
                                color = Color(0xFF684A7C),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Ask model to think before answering",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = enableReasoning,
                            onCheckedChange = onEnableReasoningChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF9C6DF5)
                            )
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show reasoning in chat",
                                fontSize = 12.sp,
                                color = Color(0xFF684A7C),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Display thinking process in bubbles",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = showReasoning,
                            onCheckedChange = onShowReasoningChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF9C6DF5)
                            )
                        )
                    }

                    Text(
                        text = "* Reasoning depends on model and Ollama API support.",
                        fontSize = 10.sp,
                        color = Color(0xFFB77BBF)
                    )
                }
            }
        },
        containerColor = Color(0xFFFFF4FA),
        shape = RoundedCornerShape(20.dp)
    )
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
                systemContent.append("\nThink step by step before answering.")
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


