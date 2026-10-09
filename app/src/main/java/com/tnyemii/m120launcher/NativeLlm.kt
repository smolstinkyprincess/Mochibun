package com.tnyemii.m120launcher

// bridges kotlin to our native llama.cpp c++ library :3
object NativeLlm {
    init {
        // load the compiled native shared library <3
        System.loadLibrary("m120llama")
    }

    // runs offline llm token generation using model file path and prompt :3
    external fun generate(modelPath: String, prompt: String): String

    // unloads model from native memory when leaving chat <3
    external fun unloadModel(): Boolean
}
