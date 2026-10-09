package com.tnyemii.m120launcher

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tnyemii.m120launcher.ui.theme.cardBgColor
import com.tnyemii.m120launcher.ui.theme.cardTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// main composable screen for offline llm chat and web search :3
@Composable
fun ChatPlaceholderPage(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var activeStatus by remember { mutableStateOf<String?>(null) }

    // load saved chat history from local json file <3
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            addAll(loadChatHistory(context))
        }
    }

    val chatListState = rememberLazyListState()

    // cleanup native llm model when leaving chat screen :3
    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch(Dispatchers.IO) {
                NativeLlm.unloadModel()
            }
        }
    }

    // scroll to bottom of chat when new message arrives <3
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            chatListState.scrollToItem(messages.lastIndex)
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

    // auto copy model file from sdcard to app private storage on startup <3
    LaunchedEffect(Unit) {
        val internalFile = java.io.File(context.filesDir, "smol_model.gguf")
        val externalPath = "/sdcard/SmolLM2/SmolLM2-360M-Instruct.Q4_K_M.gguf"
        if ((!internalFile.exists() || internalFile.length() == 0L)) {
            val extFile = java.io.File(externalPath)
            if (extFile.exists()) {
                isGenerating = true
                messages.add(ChatMessage(isUser = false, text = "Auto-loading SmolLM2 model from /sdcard/... ♡ Please wait."))
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
            messages.add(ChatMessage(isUser = false, text = "Copying model file... ♡ Please wait."))
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
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFFFF4FA))
            .padding(12.dp)
    ) {
        // top header with back button and clear history button <3
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        NativeLlm.unloadModel()
                    }
                    onBack()
                }
            ) {
                Text("← Home", color = Color(0xFF9C6DF5))
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "✧･ﾟ local llm ✧˖°",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = cardTextColor
            )

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = {
                    messages.clear()
                    coroutineScope.launch(Dispatchers.IO) {
                        saveChatHistory(context, emptyList())
                    }
                },
                enabled = !isGenerating && messages.isNotEmpty()
            ) {
                Text(
                    text = "Clear ♡",
                    color = if (!isGenerating && messages.isNotEmpty()) Color(0xFFB77BBF) else Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }

        Text(
            text = if (resolvedModelPath.isNotEmpty()) "offline • ready ♡" else "offline • model needed ♡",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            fontSize = 12.sp,
            color = Color(0xFFB77BBF)
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
            if (isGenerating) {
                item(key = "generating_status") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .align(Alignment.Start),
                        shape = RoundedCornerShape(cardRoundness),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        Text(
                            text = activeStatus ?: "thinking... ♡",
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFB77BBF),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (resolvedModelPath.isEmpty()) {
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

            if (messages.isEmpty() && !isGenerating && resolvedModelPath.isNotEmpty()) {
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
                        modifier = Modifier.fillMaxWidth(if (msg.isUser) 0.88f else 1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.isUser) Color(0xFFE9D9FF) else Color.White
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = msg.text,
                                color = Color(0xFF684A7C),
                                fontSize = 14.sp
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
                placeholder = { Text(if (isGenerating) "thinking..." else if (resolvedModelPath.isEmpty()) "select model first..." else "say something...") },
                enabled = !isGenerating,
                shape = RoundedCornerShape(20.dp),
                textStyle = TextStyle(
                    color = Color(0xFF684A7C),
                    fontSize = 14.sp
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(6.dp))

            TextButton(
                onClick = {
                    val sent = messageText.trim()
                    if (sent.isNotEmpty() && !isGenerating) {
                        if (resolvedModelPath.isEmpty()) {
                            messages.add(ChatMessage(isUser = true, text = sent))
                            messages.add(ChatMessage(isUser = false, text = "Please select your model file first by tapping the card above! ♡"))
                            coroutineScope.launch(Dispatchers.IO) {
                                saveChatHistory(context, messages)
                            }
                            messageText = ""
                        } else {
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
                                var statusTag: String? = null

                                if (needSearch) {
                                    if (isOnline) {
                                        withContext(Dispatchers.Main) { activeStatus = "Searching the web..." }
                                        val query = WebSearchClassifier.extractSearchQuery(sent)
                                        searchResults = WebSearchClient.search(query)
                                        if (searchResults.isNotEmpty()) {
                                            statusTag = "Web results used"
                                        } else {
                                            statusTag = "Search unavailable"
                                        }
                                    } else {
                                        statusTag = "Offline answer"
                                    }
                                }

                                withContext(Dispatchers.Main) { activeStatus = "Thinking..." }
                                val fullPrompt = buildChatMLPrompt(messages, searchResults)

                                val reply = try {
                                    val file = java.io.File(resolvedModelPath)
                                    if (!file.exists()) {
                                        "Model file not found at $resolvedModelPath ♡"
                                    } else {
                                        NativeLlm.generate(resolvedModelPath, fullPrompt)
                                    }
                                } catch (e: Exception) {
                                    "Error: ${e.localizedMessage ?: e.toString()} ♡"
                                }

                                withContext(Dispatchers.Main) {
                                    activeStatus = null
                                    messages.add(ChatMessage(isUser = false, text = reply, status = statusTag, sources = searchResults))
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
}
