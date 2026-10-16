package com.tnyemii.mochibun

// bridges kotlin to our native llama.cpp c++ library :3
object NativeLlm {
    @Volatile
    private var libraryLoaded = false

    @Volatile
    private var modelLoaded = false

    @Synchronized
    private fun ensureLibraryLoaded() {
        if (!libraryLoaded) {
            // load the compiled native shared library lazily when offline LLM is actually used <3
            System.loadLibrary("m120llama")
            libraryLoaded = true
        }
    }

    fun generateText(modelPath: String, prompt: String): String {
        ensureLibraryLoaded()
        modelLoaded = true
        return generate(modelPath, prompt)
    }

    fun unloadModelIfLoaded() {
        if (libraryLoaded && modelLoaded) {
            modelLoaded = false
            unloadModel()
        }
    }

    // runs offline llm token generation using model file path and prompt :3
    external fun generate(modelPath: String, prompt: String): String

    // unloads model from native memory when leaving chat <3
    external fun unloadModel(): Boolean
}
