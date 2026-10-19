/**
 * JNI bridge between Mochibun and the native llama.cpp engine (`libm120llama.so`).
 * Provides on-device offline text inference for the assistant interface while strictly managing
 * native memory allocations within the Chinoe AT-M120's ~1.8 GB RAM envelope.
 */
package com.tnyemii.mochibun

object NativeLlm {
    @Volatile
    private var libraryLoaded = false

    @Volatile
    private var modelLoaded = false

    @Synchronized
    private fun ensureLibraryLoaded() {
        if (!libraryLoaded) {
            // Lazy-load libm120llama.so only when offline inference is explicitly initiated
            System.loadLibrary("m120llama")
            libraryLoaded = true
        }
    }

    /**
     * Executes synchronous offline text generation.
     * Note: In JNI/C++, inference threads are clamped (typically 2 threads) so background LLM
     * compute does not starve Android's UI thread on the MediaTek MT6761's quad-core CPU.
     */
    fun generateText(modelPath: String, prompt: String): String {
        ensureLibraryLoaded()
        modelLoaded = true
        return generate(modelPath, prompt)
    }

    /**
     * Unloads model weights from native memory.
     *
     * Strict RAM constraint:
     * A quantized GGUF model (e.g. SmolLM2 135M / 360M) occupies several hundred megabytes of native heap.
     * On a device with ~1.8 GB usable RAM, leaving weights resident in memory after closing chat
     * would trigger aggressive Low Memory Killer (LMK) kills across running background apps.
     */
    fun unloadModelIfLoaded() {
        if (libraryLoaded && modelLoaded) {
            modelLoaded = false
            unloadModel()
        }
    }

    // Native token generation using GGUF model path and formatted prompt
    external fun generate(modelPath: String, prompt: String): String

    // Frees llama context and model memory from the native heap
    external fun unloadModel(): Boolean
}
