package com.example.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class LlmProviderPreset(
    val label: String,
    val baseUrl: String,
    val defaultModelHint: String
) {
    DEEPSEEK(
        label = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        defaultModelHint = "deepseek-chat"
    ),
    GEMINI(
        label = "Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        defaultModelHint = "gemini-2.5-flash"
    ),
    GROQ(
        label = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        defaultModelHint = "qwen/qwen3-32b"
    ),
    OPENROUTER(
        label = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        defaultModelHint = "qwen/qwen3-235b-a22b:free"
    ),
    CUSTOM(
        label = "Custom Proxy",
        baseUrl = "",
        defaultModelHint = ""
    )
}

data class DetectedModel(
    val id: String,
    val displayName: String,
    val workerCount: Int? = null,
    val isRecommended: Boolean = false
)

data class ModelDetectionResult(
    val models: List<DetectedModel>,
    val totalParsedCount: Int,
    val resolvedChatBaseUrl: String,
    val endpointUsed: String
)

data class ModelPingResult(
    val modelId: String,
    val latencyMs: Long,
    val replyPreview: String,
    val endpointUsed: String
)

class UniversalModelDetector(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun detectAvailableModels(
        baseUrl: String,
        apiKey: String,
        showAllRaw: Boolean = false
    ): Result<ModelDetectionResult> = withContext(Dispatchers.IO) {
        val cleanBase = cleanUrl(baseUrl)
        val trimmedKey = cleanCredential(apiKey)

        if (cleanBase.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter an LLM Base URL or choose a provider preset first.")
            )
        }
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter your LLM API Key first.")
            )
        }

        val candidates = buildCandidateModelUrls(cleanBase)
        val probeErrors = mutableListOf<String>()

        for (candidateUrl in candidates) {
            try {
                val reqBuilder = Request.Builder()
                    .url(candidateUrl)
                    .get()
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $trimmedKey")
                if (candidateUrl.contains("openrouter.ai", ignoreCase = true)) {
                    reqBuilder.header("HTTP-Referer", "https://toonforge.tagalog.app")
                    reqBuilder.header("X-Title", "ToonForge Tagalog")
                }

                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val rawIds = parseModelsResponse(bodyStr)
                        if (rawIds.isEmpty() && bodyStr.isNotBlank()) {
                            probeErrors.add("HTTP 200 | URL: $candidateUrl | Unexpected models payload: ${bodyStr.take(240)}")
                        } else {
                            val ranked = filterAndRankModels(rawIds, showAllRaw = showAllRaw)
                            val resolvedChatBase = resolveChatBaseUrlFromModelsUrl(candidateUrl)
                            return@withContext Result.success(
                                ModelDetectionResult(
                                    models = ranked,
                                    totalParsedCount = rawIds.size,
                                    resolvedChatBaseUrl = resolvedChatBase,
                                    endpointUsed = candidateUrl
                                )
                            )
                        }
                    } else {
                        val errText = "HTTP ${resp.code} | URL: $candidateUrl | Response: ${bodyStr.take(360)}"
                        probeErrors.add(errText)
                        if (resp.code == 401 || resp.code == 403) {
                            return@withContext Result.failure(IOException(errText))
                        }
                    }
                }
            } catch (e: Exception) {
                probeErrors.add("URL: $candidateUrl | Error: ${e.message ?: "Connection failed"}")
            }
        }

        val summary = probeErrors.lastOrNull() ?: "No model-listing endpoint responded with HTTP 200"
        Result.failure(IOException(summary))
    }

    suspend fun testModelPing(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        detectedChatBaseUrl: String = ""
    ): Result<ModelPingResult> = withContext(Dispatchers.IO) {
        val cleanBase = cleanUrl(baseUrl)
        val trimmedKey = cleanCredential(apiKey)
        val trimmedModel = cleanCredential(modelId)

        if (cleanBase.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter an LLM Base URL first."))
        }
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter your LLM API Key first."))
        }
        if (trimmedModel.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Select or enter a Model ID first."))
        }

        val candidateChatUrls = buildCandidateChatUrls(cleanBase, detectedChatBaseUrl)
        var lastError: String? = null

        for (chatUrl in candidateChatUrls) {
            val startMs = System.currentTimeMillis()
            try {
                val payload = JSONObject().apply {
                    put("model", trimmedModel)
                    put("max_tokens", 12)
                    put("temperature", 0.0)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "Reply OK")
                        })
                    })
                }
                val reqBuilder = Request.Builder()
                    .url(chatUrl)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $trimmedKey")
                if (chatUrl.contains("openrouter.ai", ignoreCase = true)) {
                    reqBuilder.header("HTTP-Referer", "https://toonforge.tagalog.app")
                    reqBuilder.header("X-Title", "ToonForge Tagalog")
                }

                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    val bodyStr = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val reply = extractChatReplyPreview(bodyStr).ifBlank { "OK" }
                        return@withContext Result.success(
                            ModelPingResult(
                                modelId = trimmedModel,
                                latencyMs = elapsed,
                                replyPreview = reply,
                                endpointUsed = chatUrl
                            )
                        )
                    } else {
                        lastError = "HTTP ${resp.code} | URL: $chatUrl | Model: $trimmedModel | Response: ${bodyStr.take(360)}"
                        if (resp.code != 404 && resp.code != 405) {
                            return@withContext Result.failure(IOException(lastError))
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = "URL: $chatUrl | Model: $trimmedModel | Error: ${e.message ?: "Network error"}"
            }
        }

        Result.failure(IOException(lastError ?: "Ping failed"))
    }

    companion object {
        private val NON_SCRIPT_KEYWORDS = listOf(
            "whisper",
            "tts",
            "audio",
            "embedding",
            "embed",
            "dall-e",
            "flux",
            "sdxl",
            "moderation",
            "rerank",
            "vision-only"
        )

        private val RECOMMENDED_MODEL_MARKERS = listOf(
            "deepseek",
            "gemini-2.5",
            "qwen",
            "gpt-4",
            "gpt-oss",
            "claude",
            "llama-3.3",
            "glm"
        )

        fun cleanCredential(raw: String): String =
            raw.replace("\r", "").replace("\n", "").trim()

        fun cleanUrl(raw: String): String {
            return cleanCredential(raw).trimEnd('/')
        }

        fun inferBaseUrlFromApiKey(apiKey: String): String? {
            val trimmed = cleanCredential(apiKey)
            if (trimmed.isEmpty()) return null
            return when {
                trimmed.startsWith("AIza") -> LlmProviderPreset.GEMINI.baseUrl
                trimmed.startsWith("gsk_") -> LlmProviderPreset.GROQ.baseUrl
                trimmed.startsWith("sk-or-") -> LlmProviderPreset.OPENROUTER.baseUrl
                else -> null
            }
        }

        fun isDefaultOrEmptyBaseUrl(baseUrl: String): Boolean {
            val clean = cleanUrl(baseUrl)
            if (clean.isEmpty()) return true
            val knownDefaults = LlmProviderPreset.entries
                .filter { it != LlmProviderPreset.CUSTOM }
                .map { it.baseUrl }
                .toSet()
            return knownDefaults.any { it.equals(clean, ignoreCase = true) }
        }

        fun matchPresetForUrl(baseUrl: String): LlmProviderPreset {
            val clean = cleanUrl(baseUrl)
            if (clean.isEmpty()) return LlmProviderPreset.CUSTOM
            return LlmProviderPreset.entries.firstOrNull { preset ->
                preset != LlmProviderPreset.CUSTOM && clean.equals(preset.baseUrl, ignoreCase = true)
            } ?: LlmProviderPreset.CUSTOM
        }

        fun buildCandidateModelUrls(cleanBaseUrl: String): List<String> {
            val clean = cleanUrl(cleanBaseUrl)
            if (clean.isEmpty()) return emptyList()
            if (clean.endsWith("/models", ignoreCase = true)) return listOf(clean)

            val list = mutableListOf<String>()
            list.add("$clean/models")

            val endsWithV1OrOpenAi = clean.endsWith("/v1", ignoreCase = true) ||
                clean.endsWith("/openai", ignoreCase = true)
            if (!endsWithV1OrOpenAi) {
                list.add("$clean/v1/models")
            }

            if (!clean.contains("/openai", ignoreCase = true)) {
                list.add("$clean/openai/v1/models")
            }
            return list.distinct()
        }

        fun resolveChatBaseUrlFromModelsUrl(modelsUrl: String): String {
            val clean = cleanUrl(modelsUrl)
            return if (clean.endsWith("/models", ignoreCase = true)) {
                clean.removeSuffix("/models").removeSuffix("/MODELS").trimEnd('/')
            } else {
                clean
            }
        }

        fun buildCandidateChatUrls(baseUrl: String, detectedChatBaseUrl: String = ""): List<String> {
            val clean = cleanUrl(baseUrl)
            if (clean.isEmpty()) return emptyList()
            if (clean.endsWith("/chat/completions", ignoreCase = true)) return listOf(clean)

            val candidates = mutableListOf<String>()
            val cleanDetected = cleanUrl(detectedChatBaseUrl)
            if (cleanDetected.isNotEmpty() &&
                cleanDetected.startsWith(clean, ignoreCase = true)
            ) {
                candidates.add("$cleanDetected/chat/completions")
            }

            val hasApiPathSuffix = clean.endsWith("/v1", ignoreCase = true) ||
                clean.endsWith("/openai", ignoreCase = true) ||
                clean.endsWith("/v1beta", ignoreCase = true)
            if (hasApiPathSuffix) {
                candidates.add("$clean/chat/completions")
            } else {
                candidates.add("$clean/v1/chat/completions")
                candidates.add("$clean/chat/completions")
                if (!clean.contains("/openai", ignoreCase = true)) {
                    candidates.add("$clean/openai/v1/chat/completions")
                }
            }
            return candidates.distinct()
        }

        fun parseModelsResponse(body: String): List<String> {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return emptyList()
            val parsed = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull() ?: return emptyList()

            val ids = mutableListOf<String>()
            when (parsed) {
                is JSONObject -> {
                    // Format 1: OpenAI {"data": [{"id": "model-id"}]}
                    val dataArr = parsed.optJSONArray("data")
                    if (dataArr != null) {
                        for (i in 0 until dataArr.length()) {
                            when (val item = dataArr.opt(i)) {
                                is JSONObject -> {
                                    val id = item.optString("id", "").trim()
                                    if (id.isNotEmpty()) ids.add(id)
                                }
                                is String -> if (item.isNotBlank()) ids.add(item.trim())
                            }
                        }
                    }
                    // Format 2: Native Gemini {"models": [{"name": "models/gemini-2.5-flash"}]}
                    val modelsArr = parsed.optJSONArray("models")
                    if (modelsArr != null) {
                        for (i in 0 until modelsArr.length()) {
                            when (val item = modelsArr.opt(i)) {
                                is JSONObject -> {
                                    val rawName = item.optString("name", "")
                                        .ifBlank { item.optString("id", "") }
                                        .trim()
                                        .removePrefix("models/")
                                    if (rawName.isNotEmpty()) ids.add(rawName)
                                }
                                is String -> {
                                    val clean = item.trim().removePrefix("models/")
                                    if (clean.isNotEmpty()) ids.add(clean)
                                }
                            }
                        }
                    }
                }
                is JSONArray -> {
                    // Format 3: Raw JSON string array ["model-1", "model-2"] (or object array)
                    for (i in 0 until parsed.length()) {
                        when (val item = parsed.opt(i)) {
                            is String -> if (item.isNotBlank()) ids.add(item.trim())
                            is JSONObject -> {
                                val id = item.optString("id", "")
                                    .ifBlank { item.optString("name", "") }
                                    .trim()
                                    .removePrefix("models/")
                                if (id.isNotEmpty()) ids.add(id)
                            }
                        }
                    }
                }
            }
            return ids.distinct()
        }

        fun isNonScriptModel(modelId: String): Boolean {
            val lower = modelId.lowercase()
            return NON_SCRIPT_KEYWORDS.any { kw -> lower.contains(kw) }
        }

        fun isRecommendedModel(modelId: String): Boolean {
            val lower = modelId.lowercase()
            return RECOMMENDED_MODEL_MARKERS.any { marker -> lower.contains(marker) }
        }

        private fun recommendedRank(modelId: String): Int {
            val lower = modelId.lowercase()
            val idx = RECOMMENDED_MODEL_MARKERS.indexOfFirst { lower.contains(it) }
            return if (idx >= 0) idx else Int.MAX_VALUE
        }

        fun filterAndRankModels(rawIds: List<String>, showAllRaw: Boolean = false): List<DetectedModel> {
            val filteredIds = if (showAllRaw) rawIds else rawIds.filterNot { isNonScriptModel(it) }
            val sorted = filteredIds.sortedWith(
                compareBy<String>(
                    { !isRecommendedModel(it) },
                    { recommendedRank(it) },
                    { it.lowercase() }
                )
            )
            return sorted.map { id ->
                DetectedModel(
                    id = id,
                    displayName = id,
                    workerCount = null,
                    isRecommended = isRecommendedModel(id)
                )
            }
        }

        private fun extractChatReplyPreview(bodyStr: String): String {
            return runCatching {
                val root = JSONObject(bodyStr)
                root.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                    .take(40)
            }.getOrDefault("OK")
        }
    }
}
