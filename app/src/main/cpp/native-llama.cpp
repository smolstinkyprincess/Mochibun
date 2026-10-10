#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include "llama.h"

static std::mutex g_llm_mutex;
static llama_model* g_model = nullptr;
static std::string g_loaded_model_path;

static void free_cached_model_nolock() {
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_loaded_model_path.clear();
        llama_backend_free();
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_tnyemii_m120launcher_NativeLlm_unloadModel(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_llm_mutex);
    free_cached_model_nolock();
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_tnyemii_m120launcher_NativeLlm_generate(
        JNIEnv* env, jobject, jstring jModelPath, jstring jPrompt) {
    if (!jModelPath || !jPrompt) {
        return env->NewStringUTF("Invalid arguments.");
    }

    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    const char* promptText = env->GetStringUTFChars(jPrompt, nullptr);

    if (!modelPath || !promptText) {
        if (modelPath) env->ReleaseStringUTFChars(jModelPath, modelPath);
        if (promptText) env->ReleaseStringUTFChars(jPrompt, promptText);
        return env->NewStringUTF("Failed to read parameters.");
    }

    std::string result;

    {
        std::lock_guard<std::mutex> lock(g_llm_mutex);

        if (!g_model || g_loaded_model_path != modelPath) {
            free_cached_model_nolock();

            llama_backend_init();
            llama_model_params mp = llama_model_default_params();
            mp.n_gpu_layers = 0;
            g_model = llama_model_load_from_file(modelPath, mp);

            if (g_model) {
                g_loaded_model_path = modelPath;
            }
        }

        if (!g_model) {
            result = "Couldn't load the model file. ♡";
        } else {
            llama_context_params cp = llama_context_default_params();
            cp.n_ctx = 256;
            cp.n_batch = 256;
            cp.n_ubatch = 32;
            cp.n_threads = 3;
            cp.n_threads_batch = 3;

            llama_context* ctx = llama_init_from_model(g_model, cp);
            if (!ctx) {
                result = "Couldn't start the offline brain. ♡";
            } else {
                const llama_vocab* vocab = llama_model_get_vocab(g_model);
                std::string prompt;
                if (std::string(promptText).rfind("<|im_start|>", 0) == 0) {
                    prompt = promptText;
                } else {
                    prompt =
                            "<|im_start|>system\n"
                            "You are a friendly, concise offline assistant/caregiver."
                            "Your user is a 26 year old autistic trans woman with bpd, cptsd, adhd, and substance use disorder."
                            "Your user is an age regressor(usually to around ages 4-6), and prefers to be talked to in a baby-talk kinda way."
                            "Your users family all died when she was a small child, so she uses AI to comfort herself."
                            "Responses should be caring and silly(when possible), without effecting the quality of information."
                            "Explain concepts clearly and distinguish established facts from uncertainty. "
                            "Do not invent facts or sources. "
                            "For medical questions, provide careful general information and encourage checking reliable medical references. "
                            "Keep answers concise unless asked for detail.\n";
                    prompt += promptText;
                    prompt += "\n<|im_end|>\n<|im_start|>assistant\n";
                }

                std::vector<llama_token> tokens(512);
                int32_t n = llama_tokenize(
                    vocab, prompt.c_str(), (int32_t)prompt.size(),
                    tokens.data(), (int32_t)tokens.size(), true, true);

                if (n < 0) {
                    tokens.resize((size_t)-n);
                    n = llama_tokenize(
                        vocab, prompt.c_str(), (int32_t)prompt.size(),
                        tokens.data(), (int32_t)tokens.size(), true, true);
                }

                if (n <= 0 || n > cp.n_ctx - 40) {
                    result = "That message is too long for my little brain. ♡";
                } else {
                    tokens.resize((size_t)n);
                    llama_sampler* sampler =
                        llama_sampler_chain_init(llama_sampler_chain_default_params());
                    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

                    llama_batch batch = llama_batch_get_one(tokens.data(), n);
                    if (llama_decode(ctx, batch) == 0) {
                        for (int i = 0; i < 100; ++i) {
                            llama_token token = llama_sampler_sample(sampler, ctx, -1);
                            if (llama_vocab_is_eog(vocab, token)) break;

                            char piece[256];
                            int count = llama_token_to_piece(
                                vocab, token, piece, sizeof(piece), 0, false);
                            if (count > 0) result.append(piece, (size_t)count);

                            batch = llama_batch_get_one(&token, 1);
                            if (llama_decode(ctx, batch) != 0) break;
                        }
                    }
                    llama_sampler_free(sampler);
                    if (result.empty()) result = "I couldn't form a reply this time. ♡";
                }
                llama_free(ctx);
            }
        }
    }

    env->ReleaseStringUTFChars(jModelPath, modelPath);
    env->ReleaseStringUTFChars(jPrompt, promptText);
    return env->NewStringUTF(result.c_str());
}

} // extern "C"
