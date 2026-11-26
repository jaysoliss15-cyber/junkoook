package com.example.llm

import com.example.data.BuiltInPrompts
import com.example.model.AnimationStyle
import com.example.model.SceneScript
import com.example.model.StudioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class AiHordeHttpException(
    val statusCode: Int,
    val endpointUrl: String,
    val rawBody: String
) : IOException("AI Horde HTTP $statusCode | URL: $endpointUrl | Response: ${rawBody.take(500)}")

class AiHordeDirectorClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(HTTP_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(HTTP_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(HTTP_TIMEOUT_SEC, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateStoryScript(
        topic: String,
        durationSeconds: Int,
        style: AnimationStyle,
        settings: StudioSettings,
        onStatus: (String) -> Unit
    ): LlmScriptResult = withContext(Dispatchers.IO) {
        val cleanKey = resolveApiKey(settings.aiHordeApiKey)
        val activeCreativePrompt = settings.activeTemplate?.content
            ?: BuiltInPrompts.VIRAL_PINOY_EXPLAINER.content
        val composedSystemPrompt = BuiltInPrompts.composeSystemPrompt(
            activeCreativePrompt = activeCreativePrompt,
            durationSeconds = durationSeconds,
            style = style
        )
        val budget = LlmDirectorClient.calculateDurationBudget(durationSeconds)
        val collectedById = LinkedHashMap<Int, SceneScript>()
        var nextSceneId = 1
        var jobCounter = 0
        val maxJobs = (budget.targetSceneCount * 2).coerceAtLeast(6)

        while (collectedById.size < budget.targetSceneCount && jobCounter < maxJobs) {
            currentCoroutineContext().ensureActive()
            jobCounter++

            val remainingCount = budget.targetSceneCount - collectedById.size
            val batchEnd = (nextSceneId + minOf(MAX_SCENES_PER_HORDE_BATCH, remainingCount) - 1)
                .coerceAtMost(budget.targetSceneCount)
            val batchRange = SceneBatchRange(startSceneId = nextSceneId, endSceneId = batchEnd)
            val continuity = LlmDirectorClient.summarizeBatchContinuity(
                collectedById.values.sortedBy { it.sceneId }
            )
            val batchLabel = "Scenes ${batchRange.startSceneId}–${batchRange.endSceneId}/${budget.targetSceneCount}"
            onStatus(formatQueueStatusMessage(waitTimeSec = 15, batchLabel = batchLabel))

            val batchScenes = generateBatchWithJsonRetry(
                composedSystemPrompt = composedSystemPrompt,
                topic = topic,
                durationSeconds = durationSeconds,
                style = style,
                budget = budget,
                batchRange = batchRange,
                previousBatchSummary = continuity,
                apiKey = cleanKey,
                batchLabel = batchLabel,
                onStatus = onStatus
            )

            batchScenes.forEach { scene ->
                val id = scene.sceneId
                if (id in 1..budget.targetSceneCount && !collectedById.containsKey(id)) {
                    collectedById[id] = scene
                } else if (collectedById.size < budget.targetSceneCount) {
                    val assignedId = (1..budget.targetSceneCount).firstOrNull { !collectedById.containsKey(it) }
                        ?: (collectedById.size + 1)
                    collectedById[assignedId] = scene.copy(sceneId = assignedId)
                }
            }

            nextSceneId = (1..budget.targetSceneCount).firstOrNull { !collectedById.containsKey(it) }
                ?: (budget.targetSceneCount + 1)
        }

        if (collectedById.isEmpty()) {
            throw IOException("AI Horde returned 0 usable Tagalog storyboard scenes. Please retry or switch to Custom LLM mode.")
        }

        var working = collectedById.values
            .sortedBy { it.sceneId }
            .mapIndexed { idx, s -> s.copy(sceneId = idx + 1) }

        // Targeted repair pass if any scenes are missing or severely short
        for (repairPass in 1..MAX_REPAIR_PASSES) {
            val report = LlmDirectorClient.validateSceneQuality(working, budget)
            if (report.isValid) break

            val deficientIds = (report.missingSceneIds + report.sceneIssues.map { it.sceneId })
                .distinct()
                .sorted()
            if (deficientIds.isEmpty() && working.size >= budget.targetSceneCount) break

            val repairBatches = deficientIds.chunked(MAX_SCENES_PER_HORDE_BATCH)
            for (batchIds in repairBatches) {
                currentCoroutineContext().ensureActive()
                val repairPrompt = buildHordeRepairPrompt(
                    composedSystemPrompt = composedSystemPrompt,
                    workingScenes = working,
                    targetSceneIds = batchIds,
                    report = report,
                    budget = budget
                )
                val repairLabel = "Repairing scenes ${batchIds.joinToString(",")} ($repairPass/$MAX_REPAIR_PASSES)"
                onStatus(formatQueueStatusMessage(waitTimeSec = 15, batchLabel = repairLabel))
                val repaired = runCatching {
                    val raw = executeHordeTextGeneration(
                        prompt = repairPrompt,
                        apiKey = cleanKey,
                        batchLabel = repairLabel,
                        onStatus = onStatus
                    )
                    JsonSanitizer.parseAndValidateScenes(raw)
                }.getOrNull()

                if (!repaired.isNullOrEmpty()) {
                    working = LlmDirectorClient.mergeRepairedScenes(
                        existing = working,
                        repaired = repaired,
                        targetSceneCount = budget.targetSceneCount
                    )
                }
            }
        }

        val trimThreshold = (budget.maxChars * LlmDirectorClient.OVERSHOOT_TRIM_MULTIPLIER).roundToInt()
        if (working.size > budget.targetSceneCount && LlmDirectorClient.totalSpokenChars(working) > trimThreshold) {
            working = LlmDirectorClient.trimExcessScenesPreservingEnding(working, budget)
        }

        val finalScenes = working
            .sortedBy { it.sceneId }
            .mapIndexed { idx, scene -> scene.copy(sceneId = idx + 1) }

        val finalReport = LlmDirectorClient.validateSceneQuality(finalScenes, budget)
        if (finalScenes.any { it.tagalogNarration.isBlank() } ||
            (finalScenes.size < budget.targetSceneCount && finalReport.missingSceneIds.isNotEmpty())
        ) {
            throw MalformedSceneJsonException(
                "AI Horde storyboard incomplete (${finalScenes.size}/${budget.targetSceneCount} scenes): ${finalReport.summaryMessage()}"
            )
        }

        LlmScriptResult(
            scenes = finalScenes,
            providerUsed = "AI Horde Async Engine"
        )
    }

    private suspend fun generateBatchWithJsonRetry(
        composedSystemPrompt: String,
        topic: String,
        durationSeconds: Int,
        style: AnimationStyle,
        budget: DurationBudget,
        batchRange: SceneBatchRange,
        previousBatchSummary: String,
        apiKey: String,
        batchLabel: String,
        onStatus: (String) -> Unit
    ): List<SceneScript> {
        var lastJsonError: MalformedSceneJsonException? = null
        for (attempt in 1..2) {
            val retryHint = if (attempt == 1) {
                ""
            } else {
                "Previous output failed json parsing (${lastJsonError?.message ?: "invalid format"}). Output ONLY valid json {\"scenes\": [...]}."
            }
            val prompt = buildHordeStoryboardPrompt(
                composedSystemPrompt = composedSystemPrompt,
                topic = topic,
                durationSeconds = durationSeconds,
                style = style,
                budget = budget,
                batchRange = batchRange,
                previousBatchSummary = previousBatchSummary,
                retryHint = retryHint
            )
            try {
                val rawText = executeHordeTextGeneration(
                    prompt = prompt,
                    apiKey = apiKey,
                    batchLabel = batchLabel,
                    onStatus = onStatus
                )
                val parsed = JsonSanitizer.parseAndValidateScenes(rawText)
                return alignSequentialHordeScenes(parsed, batchRange)
            } catch (e: MalformedSceneJsonException) {
                lastJsonError = e
                if (attempt == 1 && batchRange.count > FALLBACK_SUB_BATCH_SIZE) {
                    val firstSub = SceneBatchRange(
                        startSceneId = batchRange.startSceneId,
                        endSceneId = (batchRange.startSceneId + FALLBACK_SUB_BATCH_SIZE - 1)
                            .coerceAtMost(batchRange.endSceneId)
                    )
                    val subPrompt = buildHordeStoryboardPrompt(
                        composedSystemPrompt = composedSystemPrompt,
                        topic = topic,
                        durationSeconds = durationSeconds,
                        style = style,
                        budget = budget,
                        batchRange = firstSub,
                        previousBatchSummary = previousBatchSummary,
                        retryHint = "Return ONLY a compact valid json object {\"scenes\": [...]} for scenes ${firstSub.startSceneId}..${firstSub.endSceneId}."
                    )
                    val subRaw = executeHordeTextGeneration(
                        prompt = subPrompt,
                        apiKey = apiKey,
                        batchLabel = "Scenes ${firstSub.startSceneId}–${firstSub.endSceneId}/${budget.targetSceneCount}",
                        onStatus = onStatus
                    )
                    val subParsed = JsonSanitizer.parseAndValidateScenes(subRaw)
                    return alignSequentialHordeScenes(subParsed, firstSub)
                }
            }
        }
        throw (lastJsonError ?: MalformedSceneJsonException("AI Horde failed to return valid storyboard JSON for $batchLabel"))
    }

    private suspend fun executeHordeTextGeneration(
        prompt: String,
        apiKey: String,
        batchLabel: String,
        onStatus: (String) -> Unit
    ): String {
        val cleanKey = resolveApiKey(apiKey)
        val payload = JSONObject().apply {
            put("prompt", prompt)
            put("params", JSONObject().apply {
                put("max_length", MAX_LENGTH)
                put("max_context_length", MAX_CONTEXT_LENGTH)
                put("temperature", DEFAULT_TEMPERATURE)
                put("n", 1)
            })
        }

        val submitReq = Request.Builder()
            .url(HORDE_TEXT_ASYNC_URL)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("apikey", cleanKey)
            .header("Client-Agent", CLIENT_AGENT)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()

        val jobId = httpClient.newCall(submitReq).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw AiHordeHttpException(
                    statusCode = resp.code,
                    endpointUrl = HORDE_TEXT_ASYNC_URL,
                    rawBody = bodyStr.ifBlank { "<empty body>" }
                )
            }
            val json = try {
                JSONObject(bodyStr)
            } catch (e: Exception) {
                throw IOException("Invalid JSON from AI Horde submit endpoint: ${bodyStr.take(220)}", e)
            }
            json.optString("id", "").trim().ifBlank {
                throw IOException("AI Horde submit response missing job id: ${bodyStr.take(220)}")
            }
        }

        val statusUrl = buildStatusUrl(jobId)
        val startMs = System.currentTimeMillis()
        var consecutiveTransientFailures = 0

        while (System.currentTimeMillis() - startMs < POLL_TIMEOUT_MS) {
            currentCoroutineContext().ensureActive()
            delay(POLL_INTERVAL_MS)
            currentCoroutineContext().ensureActive()

            val elapsedMs = System.currentTimeMillis() - startMs
            val remainingCapSec = ((POLL_TIMEOUT_MS - elapsedMs) / 1000L).toInt().coerceAtLeast(1)

            val pollReq = Request.Builder()
                .url(statusUrl)
                .get()
                .header("apikey", cleanKey)
                .header("Client-Agent", CLIENT_AGENT)
                .header("Accept", "application/json")
                .build()

            val statusJson: JSONObject? = httpClient.newCall(pollReq).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val transient = resp.code == 429 || resp.code >= 500
                    if (transient && consecutiveTransientFailures < 4) {
                        consecutiveTransientFailures++
                        null
                    } else {
                        throw AiHordeHttpException(
                            statusCode = resp.code,
                            endpointUrl = statusUrl,
                            rawBody = bodyStr.ifBlank { "<empty body>" }
                        )
                    }
                } else {
                    consecutiveTransientFailures = 0
                    try {
                        JSONObject(bodyStr)
                    } catch (e: Exception) {
                        throw IOException("Invalid JSON from AI Horde status endpoint: ${bodyStr.take(220)}", e)
                    }
                }
            }

            if (statusJson == null) {
                onStatus(formatQueueStatusMessage(remainingCapSec, batchLabel))
                continue
            }

            if (statusJson.optBoolean("faulted", false)) {
                throw IOException("AI Horde worker faulted while generating script (job $jobId). Please retry or switch to Custom LLM mode.")
            }
            if (!statusJson.optBoolean("is_possible", true)) {
                throw IOException("No active AI Horde text workers available for max_length=$MAX_LENGTH right now (job $jobId). Try again or switch to Custom LLM mode.")
            }

            val waitTime = statusJson.optInt("wait_time", -1)
            val displayWait = if (waitTime > 0) waitTime else remainingCapSec
            onStatus(formatQueueStatusMessage(displayWait, batchLabel))

            if (statusJson.optBoolean("done", false)) {
                val generations: JSONArray? = statusJson.optJSONArray("generations")
                val rawText = generations
                    ?.optJSONObject(0)
                    ?.optString("text", "")
                    .orEmpty()
                    .trim()
                if (rawText.isBlank()) {
                    throw MalformedSceneJsonException("AI Horde job $jobId completed with empty generations[0].text.")
                }
                return LlmDirectorClient.stripThinkingBlocks(rawText)
            }
        }

        throw IOException(
            "AI Horde script generation timed out after ${POLL_TIMEOUT_MS / 1000L}s in queue (job $jobId). Please try again or switch to Custom LLM mode."
        )
    }

    companion object {
        const val HORDE_TEXT_ASYNC_URL = "https://stablehorde.net/api/v2/generate/text/async"
        const val HORDE_TEXT_STATUS_URL_PREFIX = "https://stablehorde.net/api/v2/generate/text/status/"
        const val ANONYMOUS_API_KEY = "0000000000"
        const val CLIENT_AGENT = "ToonForgeTagalog:1.0:android"
        const val MAX_LENGTH = 1024
        const val MAX_CONTEXT_LENGTH = 4096
        const val DEFAULT_TEMPERATURE = 0.6
        const val POLL_INTERVAL_MS = 3_000L
        const val POLL_TIMEOUT_MS = 180_000L
        const val HTTP_TIMEOUT_SEC = 20L
        const val CALL_TIMEOUT_SEC = 25L
        const val MAX_SCENES_PER_HORDE_BATCH = 4
        const val FALLBACK_SUB_BATCH_SIZE = 2
        const val MAX_REPAIR_PASSES = 1

        fun resolveApiKey(rawKey: String?): String =
            rawKey.orEmpty()
                .replace("\r", "")
                .replace("\n", "")
                .trim()
                .ifBlank { ANONYMOUS_API_KEY }

        fun buildStatusUrl(jobId: String): String =
            "$HORDE_TEXT_STATUS_URL_PREFIX${jobId.trim()}"

        fun formatQueueStatusMessage(waitTimeSec: Int, batchLabel: String = ""): String {
            val safeSec = waitTimeSec.coerceAtLeast(0)
            val base = "AI Horde generating script... (~${safeSec}s left)"
            return if (batchLabel.isBlank()) base else "$base • $batchLabel"
        }

        fun alignSequentialHordeScenes(
            parsed: List<SceneScript>,
            batchRange: SceneBatchRange
        ): List<SceneScript> {
            if (parsed.isEmpty()) return emptyList()
            val expectedRange = batchRange.startSceneId..batchRange.endSceneId
            val distinctIds = parsed.map { it.sceneId }.distinct()
            val alreadyInRange = parsed.all { it.sceneId in expectedRange } && distinctIds.size == parsed.size
            if (alreadyInRange) {
                return parsed.sortedBy { it.sceneId }
            }
            return parsed.mapIndexed { idx, scene ->
                scene.copy(sceneId = batchRange.startSceneId + idx)
            }
        }

        fun buildHordeStoryboardPrompt(
            composedSystemPrompt: String,
            topic: String,
            durationSeconds: Int,
            style: AnimationStyle,
            budget: DurationBudget,
            batchRange: SceneBatchRange,
            previousBatchSummary: String = "",
            retryHint: String = ""
        ): String {
            val systemBlock = LlmDirectorClient.buildDirectorSystemPrompt(
                composedSystemPrompt = composedSystemPrompt,
                retryHint = retryHint
            )
            val userBlock = LlmDirectorClient.buildBatchSceneProductionPrompt(
                topic = topic,
                durationSeconds = durationSeconds,
                style = style,
                budget = budget,
                batchRange = batchRange,
                previousBatchSummary = previousBatchSummary
            )
            return buildString {
                append("### SYSTEM INSTRUCTIONS\n")
                append(systemBlock)
                append("\n\n### USER REQUEST\n")
                append(userBlock)
                append("\n\n### ASSISTANT JSON OUTPUT\n")
                append(LlmDirectorClient.STRICT_JSON_OUTPUT_FOOTER)
            }
        }

        fun buildHordeRepairPrompt(
            composedSystemPrompt: String,
            workingScenes: List<SceneScript>,
            targetSceneIds: List<Int>,
            report: ScriptQualityReport,
            budget: DurationBudget
        ): String {
            val systemBlock = LlmDirectorClient.buildDirectorSystemPrompt(composedSystemPrompt)
            val repairBlock = LlmDirectorClient.buildTargetedRepairPrompt(
                workingScenes = workingScenes,
                targetSceneIds = targetSceneIds,
                report = report,
                budget = budget
            )
            return buildString {
                append("### SYSTEM INSTRUCTIONS\n")
                append(systemBlock)
                append("\n\n### REPAIR REQUEST\n")
                append(repairBlock)
                append("\n\n### ASSISTANT JSON OUTPUT\n")
                append(LlmDirectorClient.STRICT_JSON_OUTPUT_FOOTER)
            }
        }
    }
}
