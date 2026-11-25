package com.example.llm

import com.example.data.BuiltInPrompts
import com.example.model.AnimationStyle
import com.example.model.SceneScript
import com.example.model.StudioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

data class DurationBudget(
    val targetDurationSeconds: Int,
    val targetSceneCount: Int,
    val targetSpokenChars: Int,
    val minChars: Int,
    val maxChars: Int
)

data class SceneQualityIssue(
    val sceneId: Int,
    val reasons: List<String>
)

data class ScriptQualityReport(
    val missingSceneIds: List<Int>,
    val sceneIssues: List<SceneQualityIssue>,
    val totalSpokenChars: Int,
    val requiredMinSpokenChars: Int
) {
    val isValid: Boolean
        get() = missingSceneIds.isEmpty() &&
            sceneIssues.isEmpty() &&
            totalSpokenChars >= requiredMinSpokenChars

    fun summaryMessage(): String = buildString {
        if (missingSceneIds.isNotEmpty()) {
            append("Missing scene IDs: ${missingSceneIds.joinToString(", ")}. ")
        }
        if (totalSpokenChars < requiredMinSpokenChars) {
            append("Total spoken chars $totalSpokenChars < required $requiredMinSpokenChars. ")
        }
        if (sceneIssues.isNotEmpty()) {
            append(
                sceneIssues.joinToString(" | ") { issue ->
                    "Scene ${issue.sceneId}: ${issue.reasons.joinToString("; ")}"
                }
            )
        }
    }.trim()
}

data class LlmScriptResult(
    val scenes: List<SceneScript>,
    val providerUsed: String
)

class LlmHttpException(
    val statusCode: Int,
    val endpointUrl: String,
    val modelId: String,
    val rawBody: String
) : IOException("LLM HTTP $statusCode | URL: $endpointUrl | Model: $modelId | Response: ${rawBody.take(500)}")

class LlmDirectorClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
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
        val primaryBaseUrl = UniversalModelDetector.cleanUrl(settings.effectiveLlmBaseUrl)
        val primaryApiKey = UniversalModelDetector.cleanCredential(settings.effectiveLlmApiKey)
        val primaryModel = UniversalModelDetector.cleanCredential(settings.effectiveSelectedModelId)

        if (primaryBaseUrl.isBlank()) {
            throw IOException("Missing LLM Base URL. Configure your provider in API & Settings.")
        }
        if (primaryApiKey.isBlank()) {
            throw IOException("Missing LLM API Key. Enter your API key in API & Settings.")
        }

        val resolvedModel = primaryModel.ifBlank {
            UniversalModelDetector.matchPresetForUrl(primaryBaseUrl).defaultModelHint
        }
        if (resolvedModel.isBlank()) {
            throw IOException("Missing LLM Model ID. Detect or enter a chat model in API & Settings.")
        }

        val activeCreativePrompt = settings.activeTemplate?.content
            ?: BuiltInPrompts.VIRAL_PINOY_EXPLAINER.content
        val systemPrompt = BuiltInPrompts.composeSystemPrompt(
            activeCreativePrompt = activeCreativePrompt,
            durationSeconds = durationSeconds,
            style = style
        )
        val durationBudget = calculateDurationBudget(durationSeconds)

        val candidateUrls = UniversalModelDetector.buildCandidateChatUrls(
            baseUrl = primaryBaseUrl,
            detectedChatBaseUrl = settings.detectedChatBaseUrl
        )
        val presetLabel = UniversalModelDetector.matchPresetForUrl(primaryBaseUrl).label
        val extraHeaders = if (primaryBaseUrl.contains("openrouter.ai", ignoreCase = true)) {
            mapOf(
                "HTTP-Referer" to "https://toonforge.tagalog.app",
                "X-Title" to "ToonForge Tagalog"
            )
        } else {
            emptyMap()
        }

        var lastError: Throwable? = null
        for (url in candidateUrls) {
            val result = tryTwoStageOpenAiPipeline(
                providerName = "$presetLabel ($resolvedModel)",
                url = url,
                model = resolvedModel,
                apiKey = primaryApiKey,
                topic = topic,
                durationSeconds = durationSeconds,
                style = style,
                activeCreativePrompt = activeCreativePrompt,
                systemPrompt = systemPrompt,
                durationBudget = durationBudget,
                extraHeaders = extraHeaders,
                onStatus = onStatus
            )
            if (result.isSuccess) return@withContext result.getOrThrow()

            val err = result.exceptionOrNull()!!
            lastError = err
            // Only try alternate candidate URL path (/v1/chat/completions vs /chat/completions) on 404/405 path mismatch
            val isPathMismatch = err is LlmHttpException && (err.statusCode == 404 || err.statusCode == 405)
            if (!isPathMismatch) {
                throw err
            }
        }

        throw (lastError ?: IOException("LLM request failed for URL: $primaryBaseUrl | Model: $resolvedModel"))
    }

    private suspend fun tryTwoStageOpenAiPipeline(
        providerName: String,
        url: String,
        model: String,
        apiKey: String,
        topic: String,
        durationSeconds: Int,
        style: AnimationStyle,
        activeCreativePrompt: String,
        systemPrompt: String,
        durationBudget: DurationBudget,
        extraHeaders: Map<String, String> = emptyMap(),
        onStatus: (String) -> Unit
    ): Result<LlmScriptResult> {
        var lastJsonError: Throwable? = null
        var cachedBlueprint: String? = null

        for (attempt in 1..3) { // initial + up to 2 retries ONLY on malformed JSON / failed quality gate
            try {
                // PASS 1: Beat-by-Beat Narrative Blueprint
                if (cachedBlueprint.isNullOrBlank()) {
                    onStatus("Pass 1/2: Planning ${durationBudget.targetSceneCount}-beat narrative blueprint via $providerName...")
                    val blueprintSystem = buildBlueprintSystemPrompt(activeCreativePrompt)
                    val blueprintUser = buildNarrativeBlueprintPrompt(
                        topic = topic,
                        durationSeconds = durationSeconds,
                        style = style,
                        budget = durationBudget
                    )
                    cachedBlueprint = executeOpenAiChatCompletion(
                        url = url,
                        model = model,
                        apiKey = apiKey,
                        systemPrompt = blueprintSystem,
                        userPrompt = blueprintUser,
                        extraHeaders = extraHeaders
                    )
                }

                // PASS 2: Granular Scene Production
                onStatus("Pass 2/2: Producing dense scenes via $providerName (try $attempt/3)...")
                val retryHint = if (attempt > 1) {
                    "\nCRITICAL: Previous output failed JSON/quality validation (${lastJsonError?.message ?: "error"}). Output ONLY a raw JSON array starting with '[' and ending with ']' satisfying every word-count, visual_description, and non-repetition requirement."
                } else ""

                val sceneUserPrompt = buildSceneProductionPrompt(
                    topic = topic,
                    durationSeconds = durationSeconds,
                    style = style,
                    budget = durationBudget,
                    blueprint = cachedBlueprint.orEmpty()
                )

                val rawText = executeOpenAiChatCompletion(
                    url = url,
                    model = model,
                    apiKey = apiKey,
                    systemPrompt = systemPrompt + retryHint,
                    userPrompt = sceneUserPrompt,
                    extraHeaders = extraHeaders
                )

                val initialScenes = JsonSanitizer.parseAndValidateScenes(rawText)
                val validatedScenes = validateScenesOrRepair(
                    initialScenes = initialScenes,
                    budget = durationBudget,
                    blueprint = cachedBlueprint.orEmpty(),
                    onStatus = onStatus
                ) { repairUserPrompt ->
                    executeOpenAiChatCompletion(
                        url = url,
                        model = model,
                        apiKey = apiKey,
                        systemPrompt = systemPrompt,
                        userPrompt = repairUserPrompt,
                        extraHeaders = extraHeaders
                    )
                }
                return Result.success(LlmScriptResult(scenes = validatedScenes, providerUsed = providerName))
            } catch (e: MalformedSceneJsonException) {
                lastJsonError = e
                delay(400)
            } catch (e: Exception) {
                // Never swallow HTTP or network errors
                return Result.failure(e)
            }
        }
        return Result.failure(lastJsonError ?: IOException("Script quality gate failed after 3 attempts | URL: $url | Model: $model"))
    }

    private suspend fun validateScenesOrRepair(
        initialScenes: List<SceneScript>,
        budget: DurationBudget,
        blueprint: String,
        onStatus: (String) -> Unit,
        requestRepair: suspend (repairUserPrompt: String) -> String
    ): List<SceneScript> {
        var working = initialScenes
            .sortedBy { it.sceneId }
            .distinctBy { it.sceneId }

        for (repairPass in 1..MAX_REPAIR_PASSES) {
            val report = validateSceneQuality(working, budget)
            if (report.isValid) break

            val deficientIds = (report.missingSceneIds + report.sceneIssues.map { it.sceneId })
                .distinct()
                .sorted()
            val targetIds = if (deficientIds.isNotEmpty()) {
                deficientIds
            } else {
                // Spoken char deficit across total script with no per-scene failure: expand shortest scenes
                working.sortedBy { it.tagalogNarration.length }
                    .take(((budget.targetSceneCount + 1) / 2).coerceAtLeast(1))
                    .map { it.sceneId }
                    .sorted()
            }

            onStatus("Quality Gate Repair ($repairPass/$MAX_REPAIR_PASSES): Fixing scenes ${targetIds.joinToString(", ")}...")
            val repairPrompt = buildTargetedRepairPrompt(
                workingScenes = working,
                targetSceneIds = targetIds,
                report = report,
                budget = budget,
                blueprint = blueprint
            )
            val rawRepaired = requestRepair(repairPrompt)
            val parsedRepaired = JsonSanitizer.parseAndValidateScenes(rawRepaired)
            working = mergeRepairedScenes(
                existing = working,
                repaired = parsedRepaired,
                targetSceneCount = budget.targetSceneCount
            )
        }

        val trimThreshold = (budget.maxChars * OVERSHOOT_TRIM_MULTIPLIER).roundToInt()
        if (working.size > budget.targetSceneCount && totalSpokenChars(working) > trimThreshold) {
            onStatus("Trimming excess scenes to lock ${budget.targetDurationSeconds}s pacing...")
            working = trimExcessScenesPreservingEnding(working, budget)
        }

        val finalReport = validateSceneQuality(working, budget)
        if (!finalReport.isValid) {
            throw MalformedSceneJsonException(
                "Zero-Shortcut Quality Gate rejected script after $MAX_REPAIR_PASSES repair passes: ${finalReport.summaryMessage()}"
            )
        }
        return working
    }

    private fun executeOpenAiChatCompletion(
        url: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        extraHeaders: Map<String, String>
    ): String {
        val cleanKey = UniversalModelDetector.cleanCredential(apiKey)
        val cleanModel = UniversalModelDetector.cleanCredential(model)
        val cleanUrl = url.trim()

        val payload = JSONObject().apply {
            put("model", cleanModel)
            put("temperature", 0.7)
            put("max_tokens", MAX_TOKENS)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }

        val reqBuilder = Request.Builder()
            .url(cleanUrl)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $cleanKey")
        extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

        return httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw LlmHttpException(
                    statusCode = resp.code,
                    endpointUrl = cleanUrl,
                    modelId = cleanModel,
                    rawBody = bodyStr.ifBlank { "<empty body>" }
                )
            }
            val root = JSONObject(bodyStr)
            val content = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()
            if (content.isBlank()) {
                throw IOException("Empty completion content | URL: $cleanUrl | Model: $cleanModel | Body: ${bodyStr.take(300)}")
            }
            content
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_SEC = 30L
        const val READ_TIMEOUT_SEC = 90L
        const val WRITE_TIMEOUT_SEC = 30L
        const val MAX_TOKENS = 4096

        const val TAGALOG_CHARS_PER_SECOND = 15.5
        const val SCENE_PAUSE_SEC = 0.45
        const val OVERSHOOT_TRIM_MULTIPLIER = 1.15

        const val MIN_WORDS_PER_NARRATION = 11
        const val MIN_CHARS_PER_NARRATION = 60
        const val MIN_WORDS_PER_VISUAL_DESCRIPTION = 18
        const val MIN_SPOKEN_CHAR_RATIO = 0.94
        const val MAX_REPAIR_PASSES = 2

        private val VAGUE_ETC_REGEX = Regex("\\betc\\b\\.?", RegexOption.IGNORE_CASE)
        private val VISUAL_SUBJECT_ACTION_CUES = listOf(
            "stand", "walk", "sit", "slump", "point", "hold", "gestur", "lean", "reach", "jump",
            "look", "run", "kneel", "grip", "pour", "open", "type", "scroll", "count", "recoil",
            "person", "man", "woman", "vendor", "doctor", "nurse", "student", "worker", "character", "hero",
            "tindera", "narrator", "child", "boy", "girl", "elder", "mentor"
        )
        private val VISUAL_EXPRESSION_PROP_CUES = listOf(
            "smil", "grin", "frown", "shock", "wide-eyed", "wide eyed", "worried", "panic", "angry",
            "curious", "focused", "tired", "happy", "surprise", "expression", "face", "eyes", "brow",
            "holding", "cup", "coffee", "mug", "bag", "basket", "phone", "stick", "pointer", "book",
            "notebook", "wrench", "glass", "bulb", "coins", "wallet", "tray", "chalk", "clipboard", "prop"
        )
        private val VISUAL_ENVIRONMENT_CUES = listOf(
            "kitchen", "market", "street", "office", "desk", "room", "bedroom", "classroom", "chalkboard",
            "bank", "vault", "park", "lab", "hospital", "clinic", "store", "stall", "table", "counter",
            "window", "door", "wall", "floor", "tree", "house", "foreground", "background", "behind",
            "beside", "shelf", "chair", "stool", "crate", "building", "jeep", "city", "cafe"
        )
        private val VISUAL_LIGHTING_ATMOS_CUES = listOf(
            "light", "sun", "glow", "shadow", "lamp", "neon", "dark", "bright", "warm", "cool",
            "mist", "fog", "ray", "morning", "night", "midnight", "sunset", "dusk", "amber", "golden",
            "spotlight", "atmos", "ambient", "illuminat", "radiant", "rim", "contrast", "shine", "beam",
            "soft", "dramatic", "crisp", "haze"
        )

        fun calculateDurationBudget(targetDurationSeconds: Int): DurationBudget {
            val safeDuration = targetDurationSeconds.coerceAtLeast(10)
            val targetSceneCount = (safeDuration / 4.8).roundToInt().coerceIn(5, 22)
            val speechWindowSec = (safeDuration - (targetSceneCount * SCENE_PAUSE_SEC)).coerceAtLeast(4.0)
            val targetSpokenChars = (speechWindowSec * TAGALOG_CHARS_PER_SECOND).roundToInt()
            val minChars = (targetSpokenChars * 0.90).roundToInt()
            val maxChars = (targetSpokenChars * 1.08).roundToInt()
            return DurationBudget(
                targetDurationSeconds = safeDuration,
                targetSceneCount = targetSceneCount,
                targetSpokenChars = targetSpokenChars,
                minChars = minChars,
                maxChars = maxChars
            )
        }

        fun requiredMinSpokenChars(budget: DurationBudget): Int =
            (budget.targetSpokenChars * MIN_SPOKEN_CHAR_RATIO).roundToInt()

        fun buildDurationContractBlock(budget: DurationBudget): String {
            val strictMin = requiredMinSpokenChars(budget)
            return """
MANDATORY DURATION & LENGTH CONTRACT:
- Target Duration: ${budget.targetDurationSeconds} seconds (calibrated at 15.5 Tagalog chars/sec + 0.45s breathing pause per scene cut).
- Produce EXACTLY ${budget.targetSceneCount} scenes in the JSON array.
- Combined "tagalog_narration" character count across ALL ${budget.targetSceneCount} scenes MUST be strictly between ${budget.minChars} and ${budget.maxChars} characters (and AT LEAST $strictMin characters — >= 94% of ${budget.targetSpokenChars} target characters).
- Every single scene's "tagalog_narration" MUST contain at least $MIN_WORDS_PER_NARRATION words and at least $MIN_CHARS_PER_NARRATION characters.
- Preserve the voice, tone, and style of the master prompt while strictly obeying this scene count and spoken length.
            """.trimIndent()
        }

        fun buildBlueprintSystemPrompt(activeCreativePrompt: String): String {
            val safeCreative = activeCreativePrompt.trim().ifEmpty { BuiltInPrompts.VIRAL_PINOY_EXPLAINER.content }
            return """
$safeCreative

You are in PASS 1 (Beat-by-Beat Narrative Blueprinting). Plan every scene cleanly before JSON production so there is zero laziness or token fatigue in later scenes.
            """.trimIndent()
        }

        fun buildNarrativeBlueprintPrompt(
            topic: String,
            durationSeconds: Int,
            style: AnimationStyle,
            budget: DurationBudget
        ): String {
            val minCharsPerScene = (budget.targetSpokenChars.toDouble() / budget.targetSceneCount.toDouble())
                .roundToInt()
                .coerceAtLeast(MIN_CHARS_PER_NARRATION)
            return """
Create a numbered Beat-by-Beat Narrative Blueprint for EXACTLY ${budget.targetSceneCount} scenes (~${durationSeconds}s vertical 9:16 video in "${style.displayName}" style).
Topic: $topic
Calibrated Timing: 15.5 Tagalog spoken chars/sec (target ${budget.targetSpokenChars} total chars; ~${minCharsPerScene}+ chars and >= $MIN_WORDS_PER_NARRATION Tagalog words per scene).

Lock all of the following for EACH scene 1..${budget.targetSceneCount}:
1. Narrative Role (Scene 1 Hook -> Conflict/Explanation beats -> Emotional shifts -> Final Payoff/Lesson).
2. Key Tagalog talking point & estimated spoken char length (~${minCharsPerScene} chars).
3. Distinct Visual Plan (main subject & action/pose, facial expression & hand prop, foreground/background environment, lighting/atmosphere).
4. Planned non-repeating camera_motion, character_pose, and shot_framing so consecutive scenes NEVER repeat the same framing/pose/camera.

Output the concise numbered blueprint now.
            """.trimIndent()
        }

        fun buildSceneProductionPrompt(
            topic: String,
            durationSeconds: Int,
            style: AnimationStyle,
            budget: DurationBudget,
            blueprint: String
        ): String {
            return buildString {
                append("Topic: $topic\n")
                append("Visual Rendering Style (aesthetic & camera feel ONLY—do NOT override story topic/characters): ${style.displayName}\n")
                append("Target Duration: ${durationSeconds} seconds\n\n")
                append("--- LOCKED PASS 1 NARRATIVE BLUEPRINT ---\n")
                append(blueprint.trim())
                append("\n\n--- PASS 2 GRANULAR SCENE PRODUCTION RULES ---\n")
                append("Expand EVERY beat 1..${budget.targetSceneCount} into full JSON scene objects with ZERO shortcuts:\n")
                append("- Every tagalog_narration MUST be natural conversational Filipino/Tagalog with >= $MIN_WORDS_PER_NARRATION words AND >= $MIN_CHARS_PER_NARRATION chars.\n")
                append("- Every visual_description MUST have >= $MIN_WORDS_PER_VISUAL_DESCRIPTION vivid English words detailing (1) main subject & exact action/pose, (2) facial expression & hand prop, (3) specific environment/foreground/background elements, and (4) lighting/atmosphere. NEVER write 'generic', 'simple background', or 'etc'.\n")
                append("- Consecutive scenes MUST NOT repeat the same camera_motion, character_pose, or shot_framing (never use STATIC).\n")
                append("- TOPIC-FIRST RULE: Derive 100% of characters, wardrobe, environment_theme, props, cutaway_diagram_prompt, and diagrams strictly from the Topic & Blueprint.\n\n")
                append(buildDurationContractBlock(budget))
                append("\n\nOutput ONLY the valid JSON array of ${budget.targetSceneCount} scenes now.")
            }
        }

        fun buildTargetedRepairPrompt(
            workingScenes: List<SceneScript>,
            targetSceneIds: List<Int>,
            report: ScriptQualityReport,
            budget: DurationBudget,
            blueprint: String
        ): String {
            val issueById = report.sceneIssues.associateBy { it.sceneId }
            val existingById = workingScenes.associateBy { it.sceneId }
            return buildString {
                append("TARGETED SCENE QUALITY REPAIR PASS:\n")
                append("Only rewrite/produce the missing or deficient scenes listed below. Do NOT summarize or omit any detail.\n")
                if (report.totalSpokenChars < report.requiredMinSpokenChars) {
                    val deficit = report.requiredMinSpokenChars - report.totalSpokenChars
                    append("Global spoken char shortfall: currently ${report.totalSpokenChars}, must reach at least ${report.requiredMinSpokenChars} (+$deficit more Tagalog chars needed across the target scenes).\n")
                }
                append("\nBlueprint Reference:\n")
                append(blueprint.take(1800))
                append("\n\nScenes requiring repair or completion:\n")
                targetSceneIds.forEach { id ->
                    val reasons = when {
                        id in report.missingSceneIds -> listOf("MISSING SCENE (truncated or omitted in prior output)")
                        else -> issueById[id]?.reasons ?: listOf("Needs richer Tagalog storytelling to meet duration target")
                    }
                    append("- Scene $id failure reason(s): ${reasons.joinToString("; ")}\n")
                    val prevNeighbor = existingById[id - 1]
                    val nextNeighbor = existingById[id + 1]
                    if (prevNeighbor != null) {
                        append("  * Must differ from Scene ${id - 1}: avoid camera_motion=${prevNeighbor.cameraMotion}, character_pose=${effectiveCharacterPose(prevNeighbor)}, shot_framing=${effectiveShotFraming(prevNeighbor)}\n")
                    }
                    if (nextNeighbor != null) {
                        append("  * Must differ from Scene ${id + 1}: avoid camera_motion=${nextNeighbor.cameraMotion}, character_pose=${effectiveCharacterPose(nextNeighbor)}, shot_framing=${effectiveShotFraming(nextNeighbor)}\n")
                    }
                    existingById[id]?.let { cur ->
                        append("  * Previous draft narration: \"${cur.tagalogNarration}\"\n")
                    }
                }
                append("\nRequirements for EVERY returned scene:\n")
                append("- \"tagalog_narration\": >= $MIN_WORDS_PER_NARRATION words and >= $MIN_CHARS_PER_NARRATION chars of natural Tagalog.\n")
                append("- \"visual_description\": >= $MIN_WORDS_PER_VISUAL_DESCRIPTION words explicitly covering (1) main subject & exact action/pose, (2) facial expression & hand prop, (3) specific environment/foreground/background elements, (4) lighting/atmosphere; zero 'generic', 'simple background', or 'etc'.\n")
                append("- Valid non-STATIC \"camera_motion\", explicit \"character_pose\", and explicit \"shot_framing\" distinct from adjacent scenes.\n")
                append("\nReturn ONLY a JSON array containing ONLY scene_id(s): ${targetSceneIds.joinToString(", ")}.")
            }
        }

        fun mergeRepairedScenes(
            existing: List<SceneScript>,
            repaired: List<SceneScript>,
            targetSceneCount: Int
        ): List<SceneScript> {
            val byId = LinkedHashMap<Int, SceneScript>()
            existing.sortedBy { it.sceneId }.forEach { scene ->
                byId[scene.sceneId] = scene
            }
            repaired.forEach { fixed ->
                val safeId = fixed.sceneId.coerceAtLeast(1)
                byId[safeId] = fixed.copy(sceneId = safeId)
            }
            return byId.values
                .sortedBy { it.sceneId }
                .take(targetSceneCount.coerceAtLeast(byId.size))
        }

        fun countWords(text: String): Int =
            text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

        fun findVagueVisualFillers(text: String): List<String> {
            val lower = text.lowercase()
            return buildList {
                if (lower.contains("generic")) add("generic")
                if (lower.contains("simple background")) add("simple background")
                if (VAGUE_ETC_REGEX.containsMatchIn(lower)) add("etc")
            }
        }

        fun missingVisualDimensions(text: String): List<String> {
            val lower = text.lowercase()
            return buildList {
                if (VISUAL_SUBJECT_ACTION_CUES.none { lower.contains(it) }) {
                    add("main subject & action/pose")
                }
                if (VISUAL_EXPRESSION_PROP_CUES.none { lower.contains(it) }) {
                    add("facial expression & hand prop")
                }
                if (VISUAL_ENVIRONMENT_CUES.none { lower.contains(it) }) {
                    add("environment/foreground/background details")
                }
                if (VISUAL_LIGHTING_ATMOS_CUES.none { lower.contains(it) }) {
                    add("lighting/atmosphere")
                }
            }
        }

        fun effectiveCharacterPose(scene: SceneScript): com.example.model.CharacterPose =
            com.example.render.ProceduralPuppetEngine.resolveCharacterPose(scene)

        fun effectiveShotFraming(scene: SceneScript): com.example.model.ShotFraming =
            com.example.render.ProceduralPuppetEngine.resolveShotFraming(scene)

        fun validateSceneQuality(
            scenes: List<SceneScript>,
            budget: DurationBudget
        ): ScriptQualityReport {
            val byId = scenes.associateBy { it.sceneId }
            val expectedIds = (1..budget.targetSceneCount).toList()
            val missingIds = expectedIds.filter { it !in byId }

            val ordered = scenes.sortedBy { it.sceneId }
            val issues = mutableListOf<SceneQualityIssue>()

            for (i in ordered.indices) {
                val scene = ordered[i]
                val reasons = mutableListOf<String>()

                val narrationWords = countWords(scene.tagalogNarration)
                val narrationChars = scene.tagalogNarration.trim().length
                if (narrationWords < MIN_WORDS_PER_NARRATION) {
                    reasons.add("tagalog_narration has $narrationWords words (minimum $MIN_WORDS_PER_NARRATION)")
                }
                if (narrationChars < MIN_CHARS_PER_NARRATION) {
                    reasons.add("tagalog_narration has $narrationChars chars (minimum $MIN_CHARS_PER_NARRATION)")
                }

                val visualDesc = scene.effectiveVisualDescription.trim()
                val visualWords = countWords(visualDesc)
                if (visualWords < MIN_WORDS_PER_VISUAL_DESCRIPTION) {
                    reasons.add("visual_description has $visualWords words (minimum $MIN_WORDS_PER_VISUAL_DESCRIPTION)")
                }
                val fillers = findVagueVisualFillers(visualDesc)
                if (fillers.isNotEmpty()) {
                    reasons.add("visual_description contains vague filler(s): ${fillers.joinToString(", ")}")
                }
                val missingDims = missingVisualDimensions(visualDesc)
                if (missingDims.isNotEmpty()) {
                    reasons.add("visual_description lacks ${missingDims.joinToString(" & ")}")
                }

                if (i > 0) {
                    val prev = ordered[i - 1]
                    if (scene.cameraMotion == prev.cameraMotion) {
                        reasons.add("camera_motion (${scene.cameraMotion}) repeats Scene ${prev.sceneId}")
                    }
                    val curPose = effectiveCharacterPose(scene)
                    val prevPose = effectiveCharacterPose(prev)
                    if (curPose == prevPose) {
                        reasons.add("character_pose ($curPose) repeats Scene ${prev.sceneId}")
                    }
                    val curFraming = effectiveShotFraming(scene)
                    val prevFraming = effectiveShotFraming(prev)
                    if (curFraming == prevFraming) {
                        reasons.add("shot_framing ($curFraming) repeats Scene ${prev.sceneId}")
                    }
                }

                if (reasons.isNotEmpty()) {
                    issues.add(SceneQualityIssue(sceneId = scene.sceneId, reasons = reasons))
                }
            }

            return ScriptQualityReport(
                missingSceneIds = missingIds,
                sceneIssues = issues,
                totalSpokenChars = totalSpokenChars(ordered),
                requiredMinSpokenChars = requiredMinSpokenChars(budget)
            )
        }

        fun totalSpokenChars(scenes: List<SceneScript>): Int =
            scenes.sumOf { it.tagalogNarration.length }

        fun trimExcessScenesPreservingEnding(
            scenes: List<SceneScript>,
            budget: DurationBudget
        ): List<SceneScript> {
            val trimThreshold = (budget.maxChars * OVERSHOOT_TRIM_MULTIPLIER).roundToInt()
            if (scenes.size <= 2 || totalSpokenChars(scenes) <= trimThreshold) {
                return scenes
            }
            val closingScene = scenes.last()
            val closingLen = closingScene.tagalogNarration.length
            val kept = mutableListOf(scenes.first())
            var runningChars = scenes.first().tagalogNarration.length

            for (i in 1 until scenes.lastIndex) {
                val candidate = scenes[i]
                val candLen = candidate.tagalogNarration.length
                if (runningChars + candLen + closingLen <= budget.maxChars || kept.size < 2 && runningChars + candLen + closingLen <= trimThreshold) {
                    kept.add(candidate)
                    runningChars += candLen
                } else {
                    break
                }
            }
            kept.add(closingScene)
            return kept.mapIndexed { idx, scene -> scene.copy(sceneId = idx + 1) }
        }

        fun mergeExpandedScenes(
            original: List<SceneScript>,
            expanded: List<SceneScript>,
            budget: DurationBudget
        ): List<SceneScript> {
            if (expanded.isEmpty()) return original
            val origChars = totalSpokenChars(original)
            val expChars = totalSpokenChars(expanded)
            val existingNarrations = original.map { it.tagalogNarration.trim().lowercase() }.toSet()
            val newOnly = expanded.filterNot { it.tagalogNarration.trim().lowercase() in existingNarrations }

            val isFullRewrite = (expanded.size > original.size && expChars > origChars) ||
                (expanded.size >= budget.targetSceneCount && expChars >= origChars)

            val merged = when {
                isFullRewrite -> expanded
                newOnly.isNotEmpty() && original.size < budget.targetSceneCount -> original + newOnly
                expChars > origChars -> expanded
                else -> original
            }
            return merged.mapIndexed { idx, scene -> scene.copy(sceneId = idx + 1) }
        }

        fun serializeScenesForExpansion(scenes: List<SceneScript>): String {
            val arr = JSONArray()
            scenes.forEach { s ->
                arr.put(
                    JSONObject().apply {
                        put("scene_id", s.sceneId)
                        put("tagalog_narration", s.tagalogNarration)
                        put("visual_description", s.effectiveVisualDescription)
                        put("background_prompt", s.backgroundPrompt)
                        put("camera_motion", s.cameraMotion.name)
                        put("actor_role", s.actorRole.name)
                        put("actor_action", s.actorAction.name)
                        put("world_fx", s.worldFx.name)
                        if (s.environmentTheme != null) put("environment_theme", s.environmentTheme.name)
                        if (s.characterGender != null) put("character_gender", s.characterGender.name)
                        if (s.characterAge != null) put("character_age", s.characterAge.name)
                        if (s.characterOutfit != null) put("character_outfit", s.characterOutfit.name)
                        if (s.outfitColorHex.isNotBlank()) put("outfit_color_hex", s.outfitColorHex)
                        if (s.hairColorHex.isNotBlank()) put("hair_color_hex", s.hairColorHex)
                        if (s.shotFraming.name != "AUTO") put("shot_framing", s.shotFraming.name)
                        if (s.characterPose.name != "AUTO") put("character_pose", s.characterPose.name)
                        if (s.handProp.name != "NONE") put("hand_prop", s.handProp.name)
                        if (s.metaphorVisual.name != "NONE") put("metaphor_visual", s.metaphorVisual.name)
                        if (s.diagramType.name != "NONE") put("diagram_type", s.diagramType.name)
                        if (s.leftLabel.isNotBlank()) put("left_label", s.leftLabel)
                        if (s.rightLabel.isNotBlank()) put("right_label", s.rightLabel)
                        if (s.step1.isNotBlank()) put("step_1", s.step1)
                        if (s.step2.isNotBlank()) put("step_2", s.step2)
                        if (s.step3.isNotBlank()) put("step_3", s.step3)
                        if (s.statusPill.name != "NONE") put("status_pill", s.statusPill.name)
                        if (s.calloutBanner.isNotBlank()) put("callout_banner", s.calloutBanner)
                        if (s.cutawayDiagramPrompt.isNotBlank()) put("cutaway_diagram_prompt", s.cutawayDiagramPrompt)
                        if (s.flowDirection.name != "LEFT_TO_RIGHT") put("flow_direction", s.flowDirection.name)
                        if (s.cutawaySubject != null) put("cutaway_subject", s.cutawaySubject.name)
                        if (s.cutawayStage != null) put("cutaway_stage", s.cutawayStage.name)
                    }
                )
            }
            return arr.toString()
        }
    }
}
