package com.example.tts

import android.content.Context
import android.util.Base64
import com.example.model.ElevenLabsKeyEntry
import com.example.model.PronunciationEntry
import com.example.model.WordTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class TtsSynthesisOutput(
    val audioBytes: ByteArray,
    val isWav: Boolean,
    val localWordTimings: List<WordTiming>,
    val keyUsed: String?,
    val decodedPcm44100Mono: ByteArray? = null
)

data class DetectedVoice(
    val voiceId: String,
    val name: String,
    val category: String
)

data class VoicesAndCreditsResult(
    val voices: List<DetectedVoice>,
    val quotaEntry: ElevenLabsKeyEntry?
)

class ElevenLabsHttpException(
    val statusCode: Int,
    val endpointUrl: String,
    val voiceId: String,
    val modelId: String,
    val rawBody: String
) : IOException(
    "ElevenLabs HTTP $statusCode | URL: $endpointUrl | Voice: $voiceId | Model: $modelId | Response: ${rawBody.take(500)}"
)

typealias ElevenLabsTtsClient = ElevenLabsClient

class ElevenLabsClient(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkSubscriptionQuota(
        apiKey: String,
        ttsBaseUrl: String = DEFAULT_TTS_BASE_URL
    ): ElevenLabsKeyEntry = withContext(Dispatchers.IO) {
        val cleanBase = cleanTtsBaseUrl(ttsBaseUrl)
        val cleanKey = cleanCredential(apiKey)
        val url = "$cleanBase/v1/user/subscription"
        val req = Request.Builder()
            .url(url)
            .get()
            .header("xi-api-key", cleanKey)
            .header("Accept", "application/json")
            .build()

        return@withContext try {
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use ElevenLabsKeyEntry(
                        key = cleanKey,
                        lastError = "HTTP ${resp.code} | URL: $url | Response: ${body.take(240)}",
                        lastCheckedAtMs = System.currentTimeMillis()
                    )
                }
                val json = JSONObject(body)
                val used = json.optInt("character_count", 0)
                val limit = json.optInt("character_limit", 0)
                val tier = json.optString("tier", "free")
                ElevenLabsKeyEntry(
                    key = cleanKey,
                    remainingChars = (limit - used).coerceAtLeast(0),
                    limitChars = limit,
                    tier = tier,
                    lastError = null,
                    lastCheckedAtMs = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            ElevenLabsKeyEntry(
                key = cleanKey,
                lastError = "URL: $url | Error: ${e.message ?: "Network error"}",
                lastCheckedAtMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun detectVoices(
        ttsBaseUrl: String = DEFAULT_TTS_BASE_URL,
        apiKey: String
    ): Result<List<DetectedVoice>> = withContext(Dispatchers.IO) {
        val cleanBase = cleanTtsBaseUrl(ttsBaseUrl)
        val trimmedKey = cleanCredential(apiKey)
        val voicesUrl = "$cleanBase/v1/voices"
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter or add an ElevenLabs API key first.")
            )
        }

        return@withContext try {
            val req = Request.Builder()
                .url(voicesUrl)
                .get()
                .header("Accept", "application/json")
                .header("xi-api-key", trimmedKey)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use Result.failure(
                        IOException("HTTP ${resp.code} | URL: $voicesUrl | Response: ${bodyStr.take(360)}")
                    )
                }
                val voices = parseVoicesResponse(bodyStr)
                if (voices.isEmpty()) {
                    Result.failure(IOException("HTTP 200 | URL: $voicesUrl | No voices returned"))
                } else {
                    Result.success(voices)
                }
            }
        } catch (e: Exception) {
            Result.failure(IOException("URL: $voicesUrl | Error: ${e.message ?: "Failed to detect voices"}", e))
        }
    }

    suspend fun detectVoicesAndCredits(
        ttsBaseUrl: String = DEFAULT_TTS_BASE_URL,
        apiKey: String
    ): Result<VoicesAndCreditsResult> = withContext(Dispatchers.IO) {
        val cleanKey = cleanCredential(apiKey)
        val voicesResult = detectVoices(ttsBaseUrl = ttsBaseUrl, apiKey = cleanKey)
        val voices = voicesResult.getOrElse { err ->
            return@withContext Result.failure(err)
        }
        val quota = checkSubscriptionQuota(apiKey = cleanKey, ttsBaseUrl = ttsBaseUrl)
        Result.success(
            VoicesAndCreditsResult(
                voices = voices,
                quotaEntry = quota
            )
        )
    }

    suspend fun synthesizeScene(
        rawTagalogNarration: String,
        voiceId: String,
        keyPool: List<ElevenLabsKeyEntry>,
        tempCacheDir: File,
        customDictionary: List<PronunciationEntry> = emptyList(),
        previousText: String? = null,
        nextText: String? = null,
        ttsBaseUrl: String = DEFAULT_TTS_BASE_URL,
        ttsModelId: String = DEFAULT_TTS_MODEL_ID,
        narrationSpeed: Float = 1.0f,
        onKeyStatusUpdate: suspend (ElevenLabsKeyEntry) -> Unit
    ): TtsSynthesisOutput = withContext(Dispatchers.IO) {
        val cleanBase = cleanTtsBaseUrl(ttsBaseUrl)
        val cleanVoiceId = cleanCredential(voiceId)
        val initialModelId = cleanCredential(ttsModelId).ifBlank { DEFAULT_TTS_MODEL_ID }
        val clampedSpeed = narrationSpeed.coerceIn(MIN_NARRATION_SPEED, MAX_NARRATION_SPEED)
        val validKeys = keyPool.map { it.copy(key = cleanCredential(it.key)) }.filter { it.key.isNotEmpty() }

        if (validKeys.isEmpty()) {
            throw IOException("Missing ElevenLabs API key. Configure your ElevenLabs key in API & Settings.")
        }
        if (cleanVoiceId.isEmpty()) {
            throw IOException("Missing ElevenLabs Voice ID. Select or enter a Voice ID in Studio or API & Settings.")
        }

        val cleanTagalog = com.example.llm.TagalogNarrationValidator.cleanForTts(rawTagalogNarration)
        com.example.llm.TagalogNarrationValidator.validateOrThrow(cleanTagalog)
        val normalizedText = TagalogPhoneticNormalizer.normalize(cleanTagalog, customDictionary)
        val normPrev = previousText?.takeIf { it.isNotBlank() }?.let {
            TagalogPhoneticNormalizer.normalize(
                com.example.llm.TagalogNarrationValidator.cleanForTts(it),
                customDictionary
            )
        }
        val normNext = nextText?.takeIf { it.isNotBlank() }?.let {
            TagalogPhoneticNormalizer.normalize(
                com.example.llm.TagalogNarrationValidator.cleanForTts(it),
                customDictionary
            )
        }
        val displayWords = cleanTagalog.split(Regex("\\s+")).filter { it.isNotEmpty() }

        val keyErrors = mutableListOf<String>()
        for (entry in validKeys) {
            val quota = checkSubscriptionQuota(entry.key, cleanBase)
            onKeyStatusUpdate(quota)
            if (quota.lastError == null &&
                quota.remainingChars != null &&
                quota.remainingChars < normalizedText.length + 5
            ) {
                val exhausted = quota.copy(lastError = "Quota exhausted (${quota.remainingChars} left)")
                onKeyStatusUpdate(exhausted)
                keyErrors.add("${entry.maskedKey}: Quota exhausted (${quota.remainingChars} chars left)")
                continue
            }

            try {
                return@withContext synthesizeWithSingleKey(
                    cleanBase = cleanBase,
                    apiKey = entry.key,
                    maskedKey = entry.maskedKey,
                    voiceId = cleanVoiceId,
                    initialModelId = initialModelId,
                    normalizedText = normalizedText,
                    normPrev = normPrev,
                    normNext = normNext,
                    narrationSpeed = clampedSpeed,
                    displayWords = displayWords,
                    tempCacheDir = tempCacheDir
                )
            } catch (httpErr: ElevenLabsHttpException) {
                if (httpErr.statusCode == 401 || httpErr.statusCode == 402 || httpErr.statusCode == 429) {
                    val updated = quota.copy(
                        lastError = "HTTP ${httpErr.statusCode} | ${httpErr.rawBody.take(120)}",
                        lastCheckedAtMs = System.currentTimeMillis()
                    )
                    onKeyStatusUpdate(updated)
                    keyErrors.add("${entry.maskedKey}: ${httpErr.message}")
                    continue
                }
                throw httpErr
            } catch (e: IOException) {
                keyErrors.add("${entry.maskedKey}: ${e.message}")
            }
        }

        throw IOException(
            keyErrors.lastOrNull()
                ?: "ElevenLabs synthesis failed | URL: $cleanBase | Voice: $cleanVoiceId | Model: $initialModelId"
        )
    }

    private suspend fun synthesizeWithSingleKey(
        cleanBase: String,
        apiKey: String,
        maskedKey: String,
        voiceId: String,
        initialModelId: String,
        normalizedText: String,
        normPrev: String?,
        normNext: String?,
        narrationSpeed: Float,
        displayWords: List<String>,
        tempCacheDir: File
    ): TtsSynthesisOutput {
        val timestampsUrl = "$cleanBase/v1/text-to-speech/$voiceId/with-timestamps"
        var activeModelId = initialModelId

        // 1. Primary call: /with-timestamps (up to 3 transient retries on 429/5xx with 2s, 4s, 8s backoff)
        val firstResp = executeWithTimestampsCall(
            url = timestampsUrl,
            apiKey = apiKey,
            voiceId = voiceId,
            modelId = activeModelId,
            normalizedText = normalizedText,
            normPrev = normPrev,
            normNext = normNext,
            narrationSpeed = narrationSpeed
        )
        if (firstResp is TimestampsAttempt.Success) {
            return parseTimestampsSuccess(
                bodyStr = firstResp.bodyStr,
                displayWords = displayWords,
                maskedKey = maskedKey,
                endpointUrl = timestampsUrl,
                voiceId = voiceId,
                modelId = activeModelId,
                tempCacheDir = tempCacheDir
            )
        }

        var latestFailure = firstResp as TimestampsAttempt.HttpFailure

        // Auto-retry once with eleven_flash_v2_5 ONLY if HTTP 422 explicitly indicates unsupported model_id
        if (latestFailure.statusCode == 422 &&
            isUnsupportedModel422(latestFailure.rawBody) &&
            !activeModelId.equals(FALLBACK_TTS_MODEL_ID, ignoreCase = true)
        ) {
            activeModelId = FALLBACK_TTS_MODEL_ID
            val retryResp = executeWithTimestampsCall(
                url = timestampsUrl,
                apiKey = apiKey,
                voiceId = voiceId,
                modelId = activeModelId,
                normalizedText = normalizedText,
                normPrev = normPrev,
                normNext = normNext,
                narrationSpeed = narrationSpeed
            )
            if (retryResp is TimestampsAttempt.Success) {
                return parseTimestampsSuccess(
                    bodyStr = retryResp.bodyStr,
                    displayWords = displayWords,
                    maskedKey = maskedKey,
                    endpointUrl = timestampsUrl,
                    voiceId = voiceId,
                    modelId = activeModelId,
                    tempCacheDir = tempCacheDir
                )
            }
            latestFailure = retryResp as TimestampsAttempt.HttpFailure
        }

        // 2. Fallback to standard /v1/text-to-speech/{voiceId} (Accept: audio/mpeg) if /with-timestamps returns 404 or 422
        if (latestFailure.statusCode == 404 || latestFailure.statusCode == 422) {
            val standardUrl = "$cleanBase/v1/text-to-speech/$voiceId"
            return executeStandardAudioWithModelFallback(
                url = standardUrl,
                apiKey = apiKey,
                maskedKey = maskedKey,
                voiceId = voiceId,
                initialModelId = activeModelId,
                normalizedText = normalizedText,
                normPrev = normPrev,
                normNext = normNext,
                narrationSpeed = narrationSpeed,
                displayWords = displayWords,
                tempCacheDir = tempCacheDir
            )
        }

        throw ElevenLabsHttpException(
            statusCode = latestFailure.statusCode,
            endpointUrl = timestampsUrl,
            voiceId = voiceId,
            modelId = activeModelId,
            rawBody = latestFailure.rawBody
        )
    }

    private sealed class TimestampsAttempt {
        data class Success(val bodyStr: String) : TimestampsAttempt()
        data class HttpFailure(val statusCode: Int, val rawBody: String) : TimestampsAttempt()
    }

    private suspend fun executeWithTimestampsCall(
        url: String,
        apiKey: String,
        voiceId: String,
        modelId: String,
        normalizedText: String,
        normPrev: String?,
        normNext: String?,
        narrationSpeed: Float
    ): TimestampsAttempt {
        val payload = buildTtsRequestPayload(
            normalizedText = normalizedText,
            normPrev = normPrev,
            normNext = normNext,
            narrationSpeed = narrationSpeed,
            modelId = modelId
        )

        var lastFailure = TimestampsAttempt.HttpFailure(0, "<no response>")
        for (retryIdx in 0..TRANSIENT_RETRY_BACKOFFS_MS.size) {
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()

            val attempt = httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    TimestampsAttempt.Success(bodyStr)
                } else {
                    TimestampsAttempt.HttpFailure(
                        statusCode = resp.code,
                        rawBody = bodyStr.ifBlank { "<empty body>" }
                    )
                }
            }
            if (attempt is TimestampsAttempt.Success) return attempt
            lastFailure = attempt as TimestampsAttempt.HttpFailure
            if (!isTransientHttpStatus(lastFailure.statusCode) || retryIdx >= TRANSIENT_RETRY_BACKOFFS_MS.size) {
                return lastFailure
            }
            delay(TRANSIENT_RETRY_BACKOFFS_MS[retryIdx])
        }
        return lastFailure
    }

    private fun parseTimestampsSuccess(
        bodyStr: String,
        displayWords: List<String>,
        maskedKey: String,
        endpointUrl: String,
        voiceId: String,
        modelId: String,
        tempCacheDir: File
    ): TtsSynthesisOutput {
        val json = runCatching { JSONObject(bodyStr) }.getOrElse {
            throw IOException("Invalid JSON from ElevenLabs | URL: $endpointUrl | Voice: $voiceId | Model: $modelId | Body: ${bodyStr.take(300)}")
        }
        val audioB64 = json.optString("audio_base64", "")
        if (audioB64.isBlank()) {
            throw IOException("Missing audio_base64 in ElevenLabs response | URL: $endpointUrl | Voice: $voiceId | Model: $modelId | Body: ${bodyStr.take(300)}")
        }
        val mp3Bytes = Base64.decode(audioB64, Base64.DEFAULT)
        if (mp3Bytes.isEmpty()) {
            throw IOException("ElevenLabs returned empty MP3 buffer | URL: $endpointUrl | Voice: $voiceId | Model: $modelId")
        }
        val pcm44100Mono = AudioPcmStitcher.decodeAudioBytesToPcm44100Mono(
            audioBytes = mp3Bytes,
            isWav = false,
            tempCacheDir = tempCacheDir
        )
        validateDecodedPcmOrThrow(
            pcmBytes = pcm44100Mono,
            endpointUrl = endpointUrl,
            voiceId = voiceId,
            modelId = modelId
        )

        val alignment = json.optJSONObject("alignment")
            ?: json.optJSONObject("normalized_alignment")
        val timings = mapAlignmentToDisplayWords(alignment, displayWords)

        return TtsSynthesisOutput(
            audioBytes = mp3Bytes,
            isWav = false,
            localWordTimings = timings,
            keyUsed = maskedKey,
            decodedPcm44100Mono = pcm44100Mono
        )
    }

    private suspend fun executeStandardAudioWithModelFallback(
        url: String,
        apiKey: String,
        maskedKey: String,
        voiceId: String,
        initialModelId: String,
        normalizedText: String,
        normPrev: String?,
        normNext: String?,
        narrationSpeed: Float,
        displayWords: List<String>,
        tempCacheDir: File
    ): TtsSynthesisOutput {
        var activeModelId = initialModelId
        var mp3Bytes: ByteArray? = null
        var lastCode = 0
        var lastBody = ""

        for (attempt in 1..2) {
            val payload = buildTtsRequestPayload(
                normalizedText = normalizedText,
                normPrev = normPrev,
                normNext = normNext,
                narrationSpeed = narrationSpeed,
                modelId = activeModelId
            )

            for (retryIdx in 0..TRANSIENT_RETRY_BACKOFFS_MS.size) {
                val req = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .header("xi-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "audio/mpeg")
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes() ?: ByteArray(0)
                        if (bytes.isEmpty()) {
                            throw IOException("ElevenLabs returned empty audio | URL: $url | Voice: $voiceId | Model: $activeModelId")
                        }
                        mp3Bytes = bytes
                    } else {
                        lastCode = resp.code
                        lastBody = resp.body?.string().orEmpty().ifBlank { "<empty body>" }
                    }
                }

                if (mp3Bytes != null) break
                if (!isTransientHttpStatus(lastCode) || retryIdx >= TRANSIENT_RETRY_BACKOFFS_MS.size) {
                    break
                }
                delay(TRANSIENT_RETRY_BACKOFFS_MS[retryIdx])
            }

            if (mp3Bytes != null) break

            val canRetryModel = attempt == 1 &&
                lastCode == 422 &&
                isUnsupportedModel422(lastBody) &&
                !activeModelId.equals(FALLBACK_TTS_MODEL_ID, ignoreCase = true)
            if (canRetryModel) {
                activeModelId = FALLBACK_TTS_MODEL_ID
            } else {
                throw ElevenLabsHttpException(
                    statusCode = lastCode,
                    endpointUrl = url,
                    voiceId = voiceId,
                    modelId = activeModelId,
                    rawBody = lastBody
                )
            }
        }

        val finalMp3 = mp3Bytes ?: throw ElevenLabsHttpException(
            statusCode = lastCode,
            endpointUrl = url,
            voiceId = voiceId,
            modelId = activeModelId,
            rawBody = lastBody
        )
        val pcm44100Mono = AudioPcmStitcher.decodeAudioBytesToPcm44100Mono(
            audioBytes = finalMp3,
            isWav = false,
            tempCacheDir = tempCacheDir
        )
        val actualDurationMs = validateDecodedPcmOrThrow(
            pcmBytes = pcm44100Mono,
            endpointUrl = url,
            voiceId = voiceId,
            modelId = activeModelId
        )
        val proportionalTimings = buildProportionalWordTimings(displayWords, actualDurationMs)

        return TtsSynthesisOutput(
            audioBytes = finalMp3,
            isWav = false,
            localWordTimings = proportionalTimings,
            keyUsed = maskedKey,
            decodedPcm44100Mono = pcm44100Mono
        )
    }

    private fun mapAlignmentToDisplayWords(
        alignment: JSONObject?,
        displayWords: List<String>
    ): List<WordTiming> {
        if (alignment == null || displayWords.isEmpty()) return emptyList()
        val chars = alignment.optJSONArray("characters") ?: return emptyList()
        val starts = alignment.optJSONArray("character_start_times_seconds") ?: return emptyList()
        val ends = alignment.optJSONArray("character_end_times_seconds") ?: return emptyList()
        if (chars.length() == 0) return emptyList()

        val alignedWords = mutableListOf<Pair<Double, Double>>()
        var wordStart = -1.0
        var wordEnd = 0.0
        for (i in 0 until chars.length()) {
            val ch = chars.optString(i, " ")
            val s = starts.optDouble(i, 0.0) * 1000.0
            val e = ends.optDouble(i, s / 1000.0 + 0.05) * 1000.0
            if (ch.isBlank()) {
                if (wordStart >= 0.0) {
                    alignedWords.add(wordStart to wordEnd.coerceAtLeast(wordStart + 40.0))
                    wordStart = -1.0
                }
            } else {
                if (wordStart < 0.0) wordStart = s
                wordEnd = e
            }
        }
        if (wordStart >= 0.0) {
            alignedWords.add(wordStart to wordEnd.coerceAtLeast(wordStart + 40.0))
        }

        if (alignedWords.isEmpty()) return emptyList()
        val totalStart = alignedWords.first().first
        val totalEnd = alignedWords.last().second

        return if (alignedWords.size == displayWords.size) {
            displayWords.mapIndexed { idx, w ->
                WordTiming(w, alignedWords[idx].first, alignedWords[idx].second)
            }
        } else {
            val span = (totalEnd - totalStart).coerceAtLeast(300.0)
            val totalChars = displayWords.sumOf { it.length.coerceAtLeast(1) }.toDouble()
            var cursor = totalStart
            displayWords.map { word ->
                val weight = word.length.coerceAtLeast(1) / totalChars
                val dur = span * weight
                val wt = WordTiming(word, cursor, cursor + dur)
                cursor += dur
                wt
            }
        }
    }

    companion object {
        const val DEFAULT_TTS_BASE_URL = "https://api.elevenlabs.io"
        const val DEFAULT_VOICE_ID = "N2IVS1w4EtoT3dr4eOWO"
        const val DEFAULT_TTS_MODEL_ID = "eleven_multilingual_v2"
        const val FALLBACK_TTS_MODEL_ID = "eleven_flash_v2_5"
        const val MIN_NARRATION_SPEED = 0.85f
        const val MAX_NARRATION_SPEED = 1.15f
        const val MIN_DECODED_AUDIO_DURATION_MS = 600.0
        val TRANSIENT_RETRY_BACKOFFS_MS = longArrayOf(2000L, 4000L, 8000L)

        fun isTransientHttpStatus(statusCode: Int): Boolean =
            statusCode == 429 || statusCode in 500..599

        fun calculatePcmDurationMs(
            pcmBytes: ByteArray,
            sampleRate: Int = AudioPcmStitcher.TARGET_SAMPLE_RATE
        ): Double {
            if (pcmBytes.isEmpty()) return 0.0
            return (pcmBytes.size / 2.0 / sampleRate.toDouble()) * 1000.0
        }

        fun validateDecodedPcmOrThrow(
            pcmBytes: ByteArray,
            endpointUrl: String,
            voiceId: String,
            modelId: String
        ): Double {
            if (pcmBytes.isEmpty()) {
                throw IOException("ElevenLabs decoded PCM buffer is empty | URL: $endpointUrl | Voice: $voiceId | Model: $modelId")
            }
            val durationMs = calculatePcmDurationMs(pcmBytes)
            if (durationMs < MIN_DECODED_AUDIO_DURATION_MS) {
                throw IOException(
                    "ElevenLabs decoded audio too short (${durationMs.toInt()}ms < ${MIN_DECODED_AUDIO_DURATION_MS.toInt()}ms) | URL: $endpointUrl | Voice: $voiceId | Model: $modelId"
                )
            }
            return durationMs
        }

        fun cleanCredential(raw: String): String =
            raw.replace("\r", "").replace("\n", "").trim()

        fun clampNarrationSpeed(raw: Float): Double {
            val clamped = raw.coerceIn(MIN_NARRATION_SPEED, MAX_NARRATION_SPEED)
            return (clamped * 100f).roundToInt() / 100.0
        }

        fun isUnsupportedModel422(rawBody: String): Boolean {
            val lower = rawBody.lowercase()
            val mentionsModel = lower.contains("model_id") || lower.contains("model")
            val indicatesUnsupported = lower.contains("unsupported") ||
                lower.contains("not_found") ||
                lower.contains("invalid") ||
                lower.contains("not allowed") ||
                lower.contains("does not exist") ||
                lower.contains("cannot use")
            return mentionsModel && indicatesUnsupported
        }

        fun buildProportionalWordTimings(
            displayWords: List<String>,
            actualDurationMs: Double
        ): List<WordTiming> {
            if (displayWords.isEmpty()) return emptyList()
            val safeTotal = actualDurationMs.coerceAtLeast(300.0)
            val edgePad = (safeTotal * 0.03).coerceIn(20.0, 80.0)
            val usable = (safeTotal - edgePad * 2.0).coerceAtLeast(160.0)
            val totalChars = displayWords.sumOf { it.length.coerceAtLeast(1) }.toDouble()
            var cursor = edgePad
            return displayWords.map { word ->
                val weight = word.length.coerceAtLeast(1) / totalChars
                val dur = usable * weight
                val wt = WordTiming(word, cursor, cursor + dur)
                cursor += dur
                wt
            }
        }

        fun buildTtsRequestPayload(
            normalizedText: String,
            normPrev: String?,
            normNext: String?,
            narrationSpeed: Float = 1.0f,
            modelId: String = DEFAULT_TTS_MODEL_ID
        ): JSONObject {
            val speedDouble = clampNarrationSpeed(narrationSpeed)
            val cleanModel = cleanCredential(modelId).ifBlank { DEFAULT_TTS_MODEL_ID }
            return JSONObject().apply {
                put("text", normalizedText)
                put("model_id", cleanModel)
                if (!normPrev.isNullOrBlank()) put("previous_text", normPrev)
                if (!normNext.isNullOrBlank()) put("next_text", normNext)
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.45)
                    put("similarity_boost", 0.78)
                    put("style", 0.35)
                    put("speed", speedDouble)
                })
            }
        }

        fun cleanTtsBaseUrl(raw: String): String {
            var clean = cleanCredential(raw).trimEnd('/')
            if (clean.isEmpty()) return DEFAULT_TTS_BASE_URL
            if (clean.endsWith("/v1", ignoreCase = true)) {
                clean = clean.dropLast(3).trimEnd('/')
            }
            return clean.ifEmpty { DEFAULT_TTS_BASE_URL }
        }

        fun parseVoicesResponse(body: String): List<DetectedVoice> {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return emptyList()
            val tokener = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull() ?: return emptyList()
            val voicesArray = when (tokener) {
                is JSONObject -> tokener.optJSONArray("voices") ?: tokener.optJSONArray("data")
                is JSONArray -> tokener
                else -> null
            } ?: return emptyList()

            return buildList {
                for (i in 0 until voicesArray.length()) {
                    val obj = voicesArray.optJSONObject(i) ?: continue
                    val id = obj.optString("voice_id", "")
                        .ifBlank { obj.optString("id", "") }
                        .trim()
                    val name = obj.optString("name", "").trim()
                    val category = obj.optString("category", "custom")
                        .trim()
                        .ifBlank { "custom" }
                    if (id.isNotEmpty()) {
                        add(
                            DetectedVoice(
                                voiceId = id,
                                name = name.ifBlank { id },
                                category = category
                            )
                        )
                    }
                }
            }.sortedWith(compareBy<DetectedVoice>({ it.category.lowercase() }, { it.name.lowercase() }))
        }
    }
}
