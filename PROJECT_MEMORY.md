# Project Memory: M120 Launcher with Offline AI Chat

This document is the persistent single source of truth for the **M120 Launcher** project development status, build configurations, native NDK/llama.cpp settings, device constraints, and history.

---

## 1. Project Goal & App Identity

- **Name:** M120 Launcher
- **Package ID:** `com.tnyemii.m120launcher`
- **Location:** `C:\Users\emily\AndroidStudioProjects\M120Launcher`
- **Goal:** A lightweight, cute custom Android launcher featuring a pastel pink & lavender kawaii visual aesthetic with a genuinely offline local LLM AI chat page integrated seamlessly with Option C (Hybrid Local-First AI Chat with Automatic Web Search).

---

## 2. Target Device & Hardware Constraints

- **Device:** Chinoe AT-M120
- **Android Version:** Android 12 (API level 31)
- **CPU Architecture:** 32-bit ARM (`armeabi-v7a` ONLY) — *no 64-bit arm64 or x86 support on target device*.
- **SoC:** Low-end MediaTek MT6761
- **RAM:** ~1.8 GiB total (heavy zRAM usage; memory efficiency and low memory footprint are critical)
- **Display:** 480 × 800 portrait
- **Root Access:** Available on target device
- **Inference Mode:** CPU-only local execution (no GPU acceleration backend assumed)

---

## 3. Local Model & Native Engine

- **Model:** SmolLM2 360M Instruct, GGUF Q4_K_M (~258 MiB)
- **Device Model Path:** `/sdcard/SmolLM2/SmolLM2-360M-Instruct.Q4_K_M.gguf`
- **App Private Storage Model Path:** `/data/user/0/com.tnyemii.m120launcher/files/smol_model.gguf`
- **Chat History File Path:** `/data/user/0/com.tnyemii.m120launcher/files/chat_history.json`
- **Native Engine:** llama.cpp (Local checkout at `C:\Users\emily\llama.cpp`, revision `d4d82d67f`)
- **Prebuilt Pre-compiled Libraries (`armeabi-v7a`):**
  - Location: `app/src/main/jniLibs/armeabi-v7a/`
  - Libraries: `libllama.so`, `libggml.so`, `libggml-cpu.so`, `libggml-base.so`
- **Native Bridge Library:** `libm120llama.so` built via `app/src/main/cpp/native-llama.cpp` and `app/src/main/cpp/CMakeLists.txt`
- **Kotlin Wrapper:** [`NativeLlm.kt`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/java/com/tnyemii/m120launcher/NativeLlm.kt) (`object NativeLlm { external fun generate(modelPath: String, prompt: String): String; external fun unloadModel(): Boolean }`)

---

## 4. Key File Paths

- **Build Configuration:** [`app/build.gradle.kts`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/build.gradle.kts)
- **Manifest:** [`app/src/main/AndroidManifest.xml`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/AndroidManifest.xml)
- **Main Activity / Entry Point:** [`mochibun`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/java/com/tnyemii/m120launcher/MainActivity.kt)
- **Stickers & Visual Decor:** [`mochibun`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/java/com/tnyemii/m120launcher/Stickers.kt)
- **Launcher Models & Icon Helpers:** [`mochibun`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/java/com/tnyemii/m120launcher/LauncherModels.kt)
- **Launcher UI Composables:** [`mochibun`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/java/com/tnyemii/m120launcher/LauncherComponents.kt)
- **Chat Data Models & History Helpers:** [`mochibun`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/java/com/tnyemii/m120launcher/ChatModels.kt)
- **Chat UI Composables:** [`mochibun`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/java/com/tnyemii/m120launcher/ChatComponents.kt)
- **Web Search Classifier:** [`mochibun`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/java/com/tnyemii/m120launcher/WebSearchClassifier.kt)
- **Web Search Client:** [`mochibun`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/java/com/tnyemii/m120launcher/WebSearchClient.kt)
- **Unit Tests:** [`mochibun`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/test/java/com/tnyemii/m120launcher/WebSearchClassifierTest.kt)
- **Native Wrapper:** [`mochibun`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/java/com/tnyemii/m120launcher/NativeLlm.kt)
- **C++ JNI Source:** [`app/src/main/cpp/native-llama.cpp`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/cpp/native-llama.cpp)
- **CMake Build File:** [`app/src/main/cpp/CMakeLists.txt`](file:///C:/Users/emily/AndroidStudioProjects/M120Launcher/app/src/main/cpp/CMakeLists.txt)
- **Prebuilt Libraries:** `app/src/main/jniLibs/armeabi-v7a/`
- **Output APK:** `app/build/outputs/apk/debug/app-debug.apk`

---

## 5. Verified Working Features

- [x] Custom Launcher functionality (HOME launcher intent, App Drawer, Favorites, Favorite reordering).
- [x] Custom launcher icon mapping, icon caching, sampled wallpaper loading (optimized to RGB_565).
- [x] Status card (Battery, Wi-Fi, Time).
- [x] Pastel pink/lavender kawaii aesthetic (`✧･ﾟ local llm ✧˖°`).
- [x] Restricting CMake / Gradle native builds strictly to `armeabi-v7a` ABI.
- [x] Native JNI library compilation (`libm120llama.so`) linking against prebuilt llama.cpp libraries with ARMv7 NEON vectorization (`-O3 -ffast-math -mfpu=neon-vfpv4`).
- [x] Reusable cached native model session (`g_model`) in `native-llama.cpp` to eliminate 258MB model reload latency on every chat turn, plus explicit `unloadModel()` lifecycle management when leaving chat.
- [x] Background thread inference via `Dispatchers.IO` in `ChatPlaceholderPage()` (non-blocking UI).
- [x] Optimized 128 KiB buffer streaming for automatic background copying of GGUF model from `/sdcard/SmolLM2/...` to app-private storage (`filesDir/smol_model.gguf`).
- [x] Persistent chat history across launcher sessions (`saveChatHistory` / `loadChatHistory` via JSON file in app `filesDir` on `Dispatchers.IO`).
- [x] Multi-turn ChatML prompt formatting (`buildChatMLPrompt`) capped to last 4-6 messages to strictly respect `n_ctx = 256` RAM budget.
- [x] Native C++ support for pre-formatted ChatML prompts starting with `<|im_start|>`.
- [x] Chat history clear button ("Clear ♡").
- [x] **Option C Hybrid Local-First AI Chat with Automatic Web Search:**
  - Deterministic rule classifier (`WebSearchClassifier.kt`) for recency, news, weather, stock, explicit search queries.
  - Zero-dependency DuckDuckGo HTTP search client (`WebSearchClient.kt`) using `java.net.HttpURLConnection` with 5s timeouts.
  - Prompt injection stripping & snippet sanitization (truncating context to ~140 chars per snippet).
  - Automatic offline / network error fallback without breaking local chat.
  - Status indicators (`Searching the web...`, `Web results used`, `Offline answer`, `Search unavailable`).
  - Compact clickable source links launching default browser via Intent.
- [x] **Clean Kotlin Modular Reorganization:**
  - Reduced `MainActivity.kt` from ~2,700 lines to ~70 focused lines.
  - Extracted stickers to `Stickers.kt`, launcher data models to `LauncherModels.kt`, launcher UI components to `LauncherComponents.kt`, chat data models/history logic to `ChatModels.kt`, and chat UI components to `ChatComponents.kt`.
- [x] Passed 5 unit tests in `WebSearchClassifierTest.kt`.
- [x] Clean Gradle assembly build (`:app:assembleDebug`).

---

## 6. Performance & Memory Optimizations (Audit Pass)

| Optimization | Target Issue / Inefficiency | Result / Benefit |
| :--- | :--- | :--- |
| **Reusable Native Model Session (`g_model`)** | Model was reloaded from flash (`llama_model_load_from_file`) on *every single chat message*, taking 1.5-2.5s and causing memory allocation bursts. | Caches `g_model` in native C++ across turns; generation starts instantly on subsequent turns. Added explicit `unloadModel()` when leaving chat to release ~258MB RAM back to OS. |
| **ARMv7 NEON Compiler Vectorization** | CMake build compiled C++ without optimization flags for 32-bit ARMv7. | Added `-O3 -ffast-math -mfpu=neon-vfpv4` in `CMakeLists.txt` for `armeabi-v7a`, accelerating vector math and token processing on MediaTek MT6761. |
| **Wallpaper RGB_565 Decoding** | Wallpapers were decoded as `ARGB_8888` (4 bytes/pixel), consuming ~1.54 MB for a 480x800 image. | Switched `inPreferredConfig` to `RGB_565` (2 bytes/pixel), cutting wallpaper RAM footprint in half (~0.77 MB). |
| **128 KiB GGUF File Copy Buffer** | Copying the 258 MiB GGUF file from sdcard used default small stream buffers. | Implemented 128 KiB buffer streaming, speeding up first-time model setup significantly. |
| **Background Chat History I/O & Keyed LazyLists** | JSON chat history was written on the main thread during chat turns; `LazyColumn` lacked stable keys. | Moved `saveChatHistory` to `Dispatchers.IO` and added unique item keys (`id`) to `LazyColumn` items in chat and app drawer, eliminating UI micro-stutters and recomposition jitter. |

---

## 7. Build & Test Commands

- **Run Unit Tests:**
  ```powershell
  ./gradlew app:testDebugUnitTest
  ```
- **Build Debug APK:**
  ```powershell
  ./gradlew app:assembleDebug
  ```
- **Install APK to Connected Device:**
  ```powershell
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
- **Read Runtime Logs:**
  ```powershell
  adb logcat -s m120llama:V AndroidRuntime:E
  ```

---

## 8. Next Priorities & Known Risks

- **Current Priorities:**
  1. Test hybrid web search queries on physical device with active network vs offline mode.
  2. Monitor tokens/sec generation speed and memory footprint on device.
- **Known Risks:**
  - High RAM pressure on 1.8 GiB device if context window or batch size is set too large (keep `n_ctx <= 256`, `n_batch <= 64`).
  - Memory leak risk if `llama_context` or `llama_model` is re-initialized repeatedly without proper `llama_free()` calls in native bridge (mitigated via thread-safe session caching and explicit `unloadModel()`).
