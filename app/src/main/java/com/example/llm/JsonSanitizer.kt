package com.example.llm

import com.example.model.ActorAction
import com.example.model.ActorExpression
import com.example.model.ActorHeadgear
import com.example.model.ActorRole
import com.example.model.CameraMotion
import com.example.model.CharacterAge
import com.example.model.CharacterGender
import com.example.model.CharacterOutfit
import com.example.model.CharacterPose
import com.example.model.CutawayStage
import com.example.model.CutawaySubject
import com.example.model.DynamicDiagramType
import com.example.model.EnvironmentTheme
import com.example.model.FlowDirection
import com.example.model.HandProp
import com.example.model.HudWidget
import com.example.model.MetaphorVisual
import com.example.model.PointerCallout
import com.example.model.SceneScript
import com.example.model.ShotFraming
import com.example.model.ShotType
import com.example.model.StatusPill
import com.example.model.WorldFx
import org.json.JSONArray
import org.json.JSONObject

class MalformedSceneJsonException(message: String, cause: Throwable? = null) : Exception(message, cause)

object JsonSanitizer {

    private val THINK_BLOCK_REGEX = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)
    private val UNCLOSED_THINK_REGEX = Regex("<think>[\\s\\S]*$", RegexOption.IGNORE_CASE)
    private val FENCED_CODE_BLOCK_REGEX = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    private val CODE_FENCE_START = Regex("```(?:json)?\\s*", RegexOption.IGNORE_CASE)
    private val CODE_FENCE_END = Regex("```")

    private val WRAPPER_ARRAY_KEYS = setOf(
        "scenes",
        "storyboard",
        "scenes_list",
        "scene_list",
        "script",
        "video_script",
        "items",
        "data",
        "results",
        "result",
        "output"
    )

    private val REQUIRED_KEYS = listOf(
        "scene_id",
        "tagalog_narration",
        "camera_motion",
        "actor_role",
        "actor_action",
        "world_fx"
    )

    private val SCENE_KEY_REGEX = Regex("\"(?:tagalog_narration|scene_id)\"\\s*:")
    private val ANY_JSON_KEY_REGEX = Regex("\"[^\"]+\"\\s*:")

    private data class ArrayCandidate(
        val startIndex: Int,
        val isPreferredWrapper: Boolean,
        val slice: String
    )

    fun extractJsonArrayText(rawOutput: String): String {
        val candidates = collectAllArrayCandidates(rawOutput)
        if (candidates.isEmpty()) {
            throw MalformedSceneJsonException(
                "No JSON array brackets found. Raw LLM output: ${rawOutput.take(220)}"
            )
        }

        val best = candidates.firstOrNull { it.isPreferredWrapper && isSceneObjectArrayText(it.slice) }
            ?: candidates.firstOrNull { isSceneObjectArrayText(it.slice) }
            ?: candidates.firstOrNull { it.isPreferredWrapper && isObjectArrayText(it.slice) }
            ?: candidates.firstOrNull { isObjectArrayText(it.slice) }
            ?: throw MalformedSceneJsonException(
                "No JSON array of scene objects found. Raw LLM output: ${rawOutput.take(220)}"
            )

        return best.slice
    }

    private fun collectAllArrayCandidates(rawOutput: String): List<ArrayCandidate> {
        val withoutThink = rawOutput
            .replace(THINK_BLOCK_REGEX, "")
            .replace(UNCLOSED_THINK_REGEX, "")
            .trim()

        val fencedBlocks = FENCED_CODE_BLOCK_REGEX.findAll(withoutThink)
            .map { it.groupValues[1].trim() }
            .filter { it.contains('[') || it.contains('{') }
            .toList()

        val strippedFull = withoutThink
            .replace(CODE_FENCE_START, "")
            .replace(CODE_FENCE_END, "")
            .trim()

        val searchSpaces = buildList {
            addAll(fencedBlocks)
            if (strippedFull.isNotEmpty() && strippedFull !in fencedBlocks) {
                add(strippedFull)
            }
        }

        val seenSlices = LinkedHashSet<String>()
        val results = mutableListOf<ArrayCandidate>()
        for (space in searchSpaces) {
            scanArrayCandidatesInText(space).forEach { cand ->
                if (seenSlices.add(cand.slice)) {
                    results.add(cand)
                }
            }
        }
        return results
    }

    /**
     * Scans every `[` outside string literals in [text], extracts its matching/repaired `[ ... ]` slice,
     * and records whether it belongs to a wrapper key like `"scenes"` or `"storyboard"`.
     */
    private fun scanArrayCandidatesInText(text: String): List<ArrayCandidate> {
        val candidates = mutableListOf<ArrayCandidate>()
        var inString = false
        var escaped = false
        var stringStart = -1
        var lastStringValue: String? = null
        var sawColonAfterKey = false

        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                    if (stringStart >= 0 && i > stringStart) {
                        lastStringValue = text.substring(stringStart + 1, i)
                        sawColonAfterKey = false
                    }
                }
                i++
                continue
            }

            when {
                ch == '"' -> {
                    inString = true
                    stringStart = i
                }
                ch == ':' -> {
                    if (lastStringValue != null) {
                        sawColonAfterKey = true
                    }
                }
                ch == '[' -> {
                    val isWrapper = sawColonAfterKey &&
                        lastStringValue != null &&
                        lastStringValue.trim().lowercase() in WRAPPER_ARRAY_KEYS
                    val extracted = tryExtractArraySlice(text, i)
                    if (extracted != null) {
                        candidates.add(
                            ArrayCandidate(
                                startIndex = i,
                                isPreferredWrapper = isWrapper,
                                slice = extracted
                            )
                        )
                    }
                    sawColonAfterKey = false
                    lastStringValue = null
                }
                !ch.isWhitespace() -> {
                    sawColonAfterKey = false
                    lastStringValue = null
                }
            }
            i++
        }
        return candidates
    }

    /**
     * Pure-Kotlin check confirming the `[ ... ]` slice begins with a JSON object `{ ... }`
     * (rejecting `[Scene 1]`, `["tag1", "tag2"]`, `[1, 2]`, `[]`) and contains `"tagalog_narration"` or `"scene_id"`.
     */
    private fun isSceneObjectArrayText(slice: String): Boolean {
        if (!isObjectArrayText(slice)) return false
        return SCENE_KEY_REGEX.containsMatchIn(slice)
    }

    private fun isObjectArrayText(slice: String): Boolean {
        val trimmed = slice.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return false
        val firstInner = nextNonWhitespaceChar(trimmed, 1) ?: return false
        if (firstInner != '{') return false
        return ANY_JSON_KEY_REGEX.containsMatchIn(trimmed)
    }

    private fun nextNonWhitespaceChar(text: String, startIndex: Int): Char? {
        for (idx in startIndex until text.length) {
            val c = text[idx]
            if (!c.isWhitespace()) return c
        }
        return null
    }

    /**
     * Scans from the opening `[` while respecting JSON strings/escapes.
     * - If the outer `[...]` is cleanly closed (`bracketDepth == 0`), returns that exact slice.
     * - If truncated mid-stream before closing `]`, repairs at the last cleanly closed
     *   top-level scene object (`}` where `braceDepth == 0` and `bracketDepth == 1`).
     */
    private fun tryExtractArraySlice(text: String, firstBracket: Int): String? {
        var inString = false
        var escaped = false
        var braceDepth = 0
        var bracketDepth = 0
        var lastCompleteSceneObjectEnd = -1

        for (i in firstBracket until text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                '{' -> braceDepth++
                '}' -> {
                    if (braceDepth > 0) {
                        braceDepth--
                        if (braceDepth == 0 && bracketDepth == 1) {
                            lastCompleteSceneObjectEnd = i
                        }
                    }
                }
                '[' -> bracketDepth++
                ']' -> {
                    if (bracketDepth > 0) {
                        bracketDepth--
                        if (bracketDepth == 0) {
                            val slice = text.substring(firstBracket, i + 1)
                            return stripTrailingCommasBeforeArrayEnd(slice)
                        }
                    }
                }
            }
        }

        if (lastCompleteSceneObjectEnd > firstBracket) {
            val repairedPrefix = text.substring(firstBracket, lastCompleteSceneObjectEnd + 1)
                .trimEnd()
                .trimEnd(',')
                .trimEnd()
            return "$repairedPrefix]"
        }
        return null
    }

    private fun stripTrailingCommasBeforeArrayEnd(jsonArrayText: String): String {
        // Handle e.g. `[{"scene_id":1}, ]` -> `[{"scene_id":1}]`
        return jsonArrayText.replace(Regex(",\\s*\\]$"), "]")
    }

    fun parseAndValidateScenes(rawOutput: String): List<SceneScript> {
        val candidates = collectAllArrayCandidates(rawOutput)
        val orderedSlices = buildList {
            // 1. Inspect wrapper object arrays ({"scenes": [...]} / {"storyboard": [...]}) and direct candidates
            candidates.filter { it.isPreferredWrapper && isSceneObjectArrayText(it.slice) }
                .forEach { add(it.slice) }
            candidates.filter { isSceneObjectArrayText(it.slice) }
                .forEach { if (it.slice !in this) add(it.slice) }
            candidates.filter { isObjectArrayText(it.slice) }
                .forEach { if (it.slice !in this) add(it.slice) }
        }

        if (orderedSlices.isEmpty()) {
            throw MalformedSceneJsonException(
                "No JSON array brackets found. Raw LLM output: ${rawOutput.take(220)}"
            )
        }

        var selectedArray: JSONArray? = null
        var lastSyntaxError: Exception? = null
        for (slice in orderedSlices) {
            val arr = try {
                JSONArray(slice)
            } catch (e: Exception) {
                lastSyntaxError = e
                continue
            }
            if (isValidSceneJsonArray(arr)) {
                selectedArray = arr
                break
            }
        }

        val array = selectedArray ?: throw MalformedSceneJsonException(
            "No valid JSONArray of scene objects found (${lastSyntaxError?.message ?: "non-scene elements"}). Raw LLM output: ${rawOutput.take(220)}",
            lastSyntaxError
        )

        val scenes = mutableListOf<SceneScript>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
                ?: throw MalformedSceneJsonException("Element at index $i is not a JSONObject")
            scenes.add(parseSceneObject(obj, i))
        }
        return scenes
    }

    private fun isValidSceneJsonArray(array: JSONArray): Boolean {
        if (array.length() == 0) return false
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: return false
            if (obj.has("tagalog_narration") || obj.has("scene_id")) {
                return true
            }
        }
        return false
    }

    private fun parseSceneObject(obj: JSONObject, index: Int): SceneScript {
        for (key in REQUIRED_KEYS) {
            if (!obj.has(key) || obj.isNull(key)) {
                throw MalformedSceneJsonException("Scene[$index] missing required key '$key'")
            }
        }

        val sceneId = obj.optInt("scene_id", index + 1)
        val narration = TagalogNarrationValidator.cleanForTts(obj.optString("tagalog_narration", ""))
        val rawBgPrompt = obj.optString("background_prompt", "").trim()
        val rawVisualDesc = obj.optString("visual_description", "").trim()
        val bgPrompt = rawBgPrompt.ifBlank { rawVisualDesc }
        val visualDesc = rawVisualDesc.ifBlank { rawBgPrompt }
        val camRaw = obj.optString("camera_motion", "").trim().uppercase()
        val roleRaw = obj.optString("actor_role", "").trim().uppercase()
        val actionRaw = obj.optString("actor_action", "").trim().uppercase()
        val fxRaw = obj.optString("world_fx", "").trim().uppercase()

        if (narration.isEmpty()) {
            throw MalformedSceneJsonException("Scene[$index] has empty tagalog_narration")
        }
        TagalogNarrationValidator.validateOrThrow(narration)
        if (bgPrompt.isEmpty() && visualDesc.isEmpty()) {
            throw MalformedSceneJsonException("Scene[$index] missing visual_description/background_prompt")
        }

        // Backward-compatible optional HUD & kinetic motion-graphics fields
        val statusPill = StatusPill.fromRaw(obj.optString("status_pill", "NONE"))
        val calloutBanner = obj.optString("callout_banner", "").trim()
        val hudWidget = HudWidget.fromRaw(obj.optString("hud_widget", "NONE"))
        val hudLabel = obj.optString("hud_label", "").trim()
        val hudFrom = obj.optInt("hud_from_value", 0).coerceIn(0, 100)
        val hudTo = obj.optInt("hud_to_value", 100).coerceIn(0, 100)

        val checklistArray = obj.optJSONArray("checklist_items")
        val checklistItems = if (checklistArray != null) {
            buildList {
                for (k in 0 until minOf(4, checklistArray.length())) {
                    val item = checklistArray.optString(k, "").trim()
                    if (item.isNotEmpty()) add(item)
                }
            }
        } else {
            emptyList()
        }

        val shotFraming = ShotFraming.fromRaw(obj.optString("shot_framing", "AUTO"))
        val characterPose = CharacterPose.fromRaw(obj.optString("character_pose", "AUTO"))
        val handProp = HandProp.fromRaw(obj.optString("hand_prop", "NONE"))
        val shotType = ShotType.fromRaw(obj.optString("shot_type", "AUTO"))
        val secondaryRoleRaw = obj.optString("secondary_actor_role", "").trim()
        val secondaryActorRole = if (secondaryRoleRaw.isNotEmpty()) ActorRole.fromRaw(secondaryRoleRaw) else null
        val expression = ActorExpression.fromRaw(obj.optString("expression", "HAPPY_TALK"))
        val headgear = ActorHeadgear.fromRaw(obj.optString("headgear", "NONE"))
        val metaphorVisual = MetaphorVisual.fromRaw(obj.optString("metaphor_visual", "NONE"))
        val topicPillLabel = obj.optString("topic_pill", "").trim()
        val cutawayStage = CutawayStage.fromRaw(obj.optString("cutaway_stage", ""))
        val cutawaySubject = CutawaySubject.fromRaw(obj.optString("cutaway_subject", ""))
        val environmentTheme = EnvironmentTheme.fromRaw(obj.optString("environment_theme", ""))
        val characterGender = CharacterGender.fromRaw(obj.optString("character_gender", ""))
        val characterAge = CharacterAge.fromRaw(obj.optString("character_age", ""))
        val characterOutfit = CharacterOutfit.fromRaw(obj.optString("character_outfit", ""))
        val outfitColorHex = obj.optString("outfit_color_hex", "").trim()
        val hairColorHex = obj.optString("hair_color_hex", "").trim()
        val cutawayDiagramPrompt = obj.optString("cutaway_diagram_prompt", "").trim()
        val flowDirection = FlowDirection.fromRaw(obj.optString("flow_direction", "LEFT_TO_RIGHT"))
        val diagramType = DynamicDiagramType.fromRaw(obj.optString("diagram_type", "NONE"))
        val leftLabel = obj.optString("left_label", "").trim()
        val rightLabel = obj.optString("right_label", "").trim()
        val step1 = obj.optString("step_1", "").trim()
        val step2 = obj.optString("step_2", "").trim()
        val step3 = obj.optString("step_3", "").trim()

        val pointerCalloutsArray = obj.optJSONArray("pointer_callouts")
        val pointerCallouts = if (pointerCalloutsArray != null) {
            buildList {
                for (k in 0 until minOf(2, pointerCalloutsArray.length())) {
                    val pObj = pointerCalloutsArray.optJSONObject(k) ?: continue
                    val label = pObj.optString("label", "").trim().uppercase()
                    if (label.isNotEmpty()) {
                        val px = pObj.optDouble("x", 135.0).toFloat()
                        val py = pObj.optDouble("y", 170.0).toFloat()
                        add(PointerCallout(label = label, x = px, y = py))
                    }
                }
            }
        } else {
            emptyList()
        }

        return SceneScript(
            sceneId = sceneId,
            tagalogNarration = narration,
            backgroundPrompt = bgPrompt,
            cameraMotion = CameraMotion.fromRaw(camRaw),
            actorRole = ActorRole.fromRaw(roleRaw),
            actorAction = ActorAction.fromRaw(actionRaw),
            worldFx = WorldFx.fromRaw(fxRaw),
            statusPill = statusPill,
            calloutBanner = calloutBanner,
            hudWidget = hudWidget,
            hudLabel = hudLabel,
            hudFromValue = hudFrom,
            hudToValue = hudTo,
            checklistItems = checklistItems,
            shotFraming = shotFraming,
            characterPose = characterPose,
            handProp = handProp,
            shotType = shotType,
            secondaryActorRole = secondaryActorRole,
            expression = expression,
            headgear = headgear,
            metaphorVisual = metaphorVisual,
            topicPillLabel = topicPillLabel,
            environmentTheme = environmentTheme,
            cutawaySubject = cutawaySubject,
            characterGender = characterGender,
            characterAge = characterAge,
            characterOutfit = characterOutfit,
            outfitColorHex = outfitColorHex,
            hairColorHex = hairColorHex,
            cutawayStage = cutawayStage,
            cutawayDiagramPrompt = cutawayDiagramPrompt,
            flowDirection = flowDirection,
            diagramType = diagramType,
            leftLabel = leftLabel,
            rightLabel = rightLabel,
            step1 = step1,
            step2 = step2,
            step3 = step3,
            pointerCallouts = pointerCallouts,
            visualDescription = visualDesc
        )
    }
}
