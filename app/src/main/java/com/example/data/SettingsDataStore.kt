package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ElevenLabsKeyEntry
import com.example.model.PromptTemplate
import com.example.model.PronunciationEntry
import com.example.model.StudioSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "toonforge_settings"
)

typealias SettingsRepository = SettingsDataStore

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val PROMPT_TEMPLATES_JSON = stringPreferencesKey("prompt_templates_json")
        val ACTIVE_PROMPT_ID = stringPreferencesKey("active_prompt_id")
        val ELEVENLABS_KEYS_JSON = stringPreferencesKey("elevenlabs_keys_json")
        val SELECTED_VOICE_ID = stringPreferencesKey("selected_voice_id")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        val CUSTOM_MODEL = stringPreferencesKey("custom_model")
        val CUSTOM_API_KEY = stringPreferencesKey("custom_api_key")
        val USE_AI_HORDE = booleanPreferencesKey("use_ai_horde")
        val AI_HORDE_KEY = stringPreferencesKey("ai_horde_key")
        val CUSTOM_SPRITE_PATHS_JSON = stringPreferencesKey("custom_sprite_paths_json")
        val SPRITE_STICKER_OUTLINE = booleanPreferencesKey("sprite_sticker_outline")
        val CUSTOM_BGM_PATH = stringPreferencesKey("custom_bgm_path")
        val CUSTOM_BGM_NAME = stringPreferencesKey("custom_bgm_name")
        val PRONUNCIATION_DICT_JSON = stringPreferencesKey("pronunciation_dict_json")
        val TTS_BASE_URL = stringPreferencesKey("tts_base_url")
        val TTS_MODEL_ID = stringPreferencesKey("tts_model_id")
        val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        val LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val DETECTED_CHAT_BASE_URL = stringPreferencesKey("detected_chat_base_url")
        val NARRATION_SPEED = floatPreferencesKey("narration_speed")
    }

    private fun cleanCredential(raw: String?): String =
        raw.orEmpty().replace("\r", "").replace("\n", "").trim()

    val settingsFlow: Flow<StudioSettings> = context.settingsDataStore.data.map { prefs ->
        val templates = parseTemplates(prefs[Keys.PROMPT_TEMPLATES_JSON])
        val activeId = prefs[Keys.ACTIVE_PROMPT_ID]
            ?.takeIf { id -> templates.any { it.id == id } }
            ?: templates.first().id

        val legacyBaseUrl = cleanCredential(prefs[Keys.CUSTOM_BASE_URL])
        val legacyModel = cleanCredential(prefs[Keys.CUSTOM_MODEL])
        val legacyApiKey = cleanCredential(prefs[Keys.CUSTOM_API_KEY])
        val universalBaseUrl = cleanCredential(prefs[Keys.LLM_BASE_URL]).ifBlank { legacyBaseUrl }
        val universalModel = cleanCredential(prefs[Keys.SELECTED_MODEL_ID]).ifBlank { legacyModel }
        val universalApiKey = cleanCredential(prefs[Keys.LLM_API_KEY]).ifBlank { legacyApiKey }

        StudioSettings(
            promptTemplates = templates,
            activePromptId = activeId,
            elevenLabsKeys = parseElevenLabsKeys(prefs[Keys.ELEVENLABS_KEYS_JSON]),
            selectedVoiceId = cleanCredential(prefs[Keys.SELECTED_VOICE_ID]).ifBlank { "N2IVS1w4EtoT3dr4eOWO" },
            ttsBaseUrl = cleanCredential(prefs[Keys.TTS_BASE_URL]).ifBlank { "https://api.elevenlabs.io" },
            ttsModelId = cleanCredential(prefs[Keys.TTS_MODEL_ID]).ifBlank { "eleven_multilingual_v2" },
            llmBaseUrl = universalBaseUrl,
            llmApiKey = universalApiKey,
            selectedModelId = universalModel,
            detectedChatBaseUrl = cleanCredential(prefs[Keys.DETECTED_CHAT_BASE_URL]),
            groqApiKey = cleanCredential(prefs[Keys.GROQ_API_KEY]),
            openRouterApiKey = cleanCredential(prefs[Keys.OPENROUTER_API_KEY]),
            customBaseUrl = legacyBaseUrl,
            customModel = legacyModel,
            customApiKey = legacyApiKey,
            useAiHorde = prefs[Keys.USE_AI_HORDE] ?: false,
            aiHordeKey = cleanCredential(prefs[Keys.AI_HORDE_KEY]).ifBlank { "0000000000" },
            customSpritePaths = parseSpritePaths(prefs[Keys.CUSTOM_SPRITE_PATHS_JSON]),
            spriteStickerOutline = prefs[Keys.SPRITE_STICKER_OUTLINE] ?: true,
            customBgmPath = prefs[Keys.CUSTOM_BGM_PATH] ?: "",
            customBgmName = prefs[Keys.CUSTOM_BGM_NAME] ?: "",
            pronunciationDictionary = parsePronunciationDict(prefs[Keys.PRONUNCIATION_DICT_JSON]),
            narrationSpeed = (prefs[Keys.NARRATION_SPEED] ?: 1.0f).coerceIn(0.85f, 1.15f)
        )
    }

    suspend fun getSnapshot(): StudioSettings = settingsFlow.first()

    suspend fun setActivePrompt(templateId: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_PROMPT_ID] = templateId
        }
    }

    suspend fun importTxtTemplate(name: String, content: String): PromptTemplate {
        val cleanName = name.removeSuffix(".txt").removeSuffix(".TXT").trim().ifBlank { "Imported Prompt" }
        val newTemplate = PromptTemplate(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            content = content.trim(),
            isBuiltIn = false
        )
        context.settingsDataStore.edit { prefs ->
            val current = parseTemplates(prefs[Keys.PROMPT_TEMPLATES_JSON]).toMutableList()
            current.add(newTemplate)
            prefs[Keys.PROMPT_TEMPLATES_JSON] = serializeTemplates(current)
            prefs[Keys.ACTIVE_PROMPT_ID] = newTemplate.id
        }
        return newTemplate
    }

    suspend fun updateTemplateContent(templateId: String, newName: String, newContent: String) {
        context.settingsDataStore.edit { prefs ->
            val current = parseTemplates(prefs[Keys.PROMPT_TEMPLATES_JSON]).map { item ->
                if (item.id == templateId) {
                    item.copy(
                        name = newName.trim().ifBlank { item.name },
                        content = newContent
                    )
                } else {
                    item
                }
            }
            prefs[Keys.PROMPT_TEMPLATES_JSON] = serializeTemplates(current)
        }
    }

    suspend fun resetTemplate(templateId: String): String {
        var restoredContent = ""
        context.settingsDataStore.edit { prefs ->
            val defaultTemplate = BuiltInPrompts.getDefaultById(templateId)
            val current = parseTemplates(prefs[Keys.PROMPT_TEMPLATES_JSON]).map { item ->
                if (item.id == templateId && defaultTemplate != null) {
                    restoredContent = defaultTemplate.content
                    defaultTemplate
                } else {
                    if (item.id == templateId) restoredContent = item.content
                    item
                }
            }
            prefs[Keys.PROMPT_TEMPLATES_JSON] = serializeTemplates(current)
        }
        return restoredContent
    }

    suspend fun deleteTemplate(templateId: String) {
        context.settingsDataStore.edit { prefs ->
            val current = parseTemplates(prefs[Keys.PROMPT_TEMPLATES_JSON])
            val target = current.firstOrNull { it.id == templateId }
            if (target != null && !target.isBuiltIn && current.size > 1) {
                val filtered = current.filterNot { it.id == templateId }
                prefs[Keys.PROMPT_TEMPLATES_JSON] = serializeTemplates(filtered)
                if (prefs[Keys.ACTIVE_PROMPT_ID] == templateId) {
                    prefs[Keys.ACTIVE_PROMPT_ID] = filtered.first().id
                }
            }
        }
    }

    suspend fun setSelectedVoiceId(voiceId: String) {
        val clean = cleanCredential(voiceId)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SELECTED_VOICE_ID] = clean
        }
    }

    suspend fun addElevenLabsKey(rawKey: String) {
        val clean = cleanCredential(rawKey)
        if (clean.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val list = parseElevenLabsKeys(prefs[Keys.ELEVENLABS_KEYS_JSON]).toMutableList()
            if (list.none { it.key == clean }) {
                list.add(ElevenLabsKeyEntry(key = clean))
                prefs[Keys.ELEVENLABS_KEYS_JSON] = serializeElevenLabsKeys(list)
            }
        }
    }

    suspend fun removeElevenLabsKey(key: String) {
        val clean = cleanCredential(key)
        context.settingsDataStore.edit { prefs ->
            val list = parseElevenLabsKeys(prefs[Keys.ELEVENLABS_KEYS_JSON])
                .filterNot { it.key == clean || it.key == key }
            prefs[Keys.ELEVENLABS_KEYS_JSON] = serializeElevenLabsKeys(list)
        }
    }

    suspend fun updateElevenLabsKeyEntry(updated: ElevenLabsKeyEntry) {
        val cleanUpdated = updated.copy(key = cleanCredential(updated.key))
        context.settingsDataStore.edit { prefs ->
            val list = parseElevenLabsKeys(prefs[Keys.ELEVENLABS_KEYS_JSON]).map {
                if (cleanCredential(it.key) == cleanUpdated.key) cleanUpdated else it
            }
            prefs[Keys.ELEVENLABS_KEYS_JSON] = serializeElevenLabsKeys(list)
        }
    }

    suspend fun saveLlmSettings(
        llmBaseUrl: String,
        llmApiKey: String,
        selectedModelId: String,
        detectedChatBaseUrl: String = "",
        ttsBaseUrl: String = "https://api.elevenlabs.io",
        ttsModelId: String = "eleven_multilingual_v2",
        selectedVoiceId: String? = null,
        aiHordeKey: String = "0000000000",
        narrationSpeed: Float? = null,
        useAiHorde: Boolean? = null
    ) {
        val cleanBase = cleanCredential(llmBaseUrl)
        val cleanKey = cleanCredential(llmApiKey)
        val cleanModel = cleanCredential(selectedModelId)
        val cleanDetected = cleanCredential(detectedChatBaseUrl)
        val cleanTtsBase = cleanCredential(ttsBaseUrl).ifBlank { "https://api.elevenlabs.io" }
        val cleanTtsModel = cleanCredential(ttsModelId).ifBlank { "eleven_multilingual_v2" }
        val cleanHorde = cleanCredential(aiHordeKey).ifBlank { "0000000000" }
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_BASE_URL] = cleanBase
            prefs[Keys.CUSTOM_MODEL] = cleanModel
            prefs[Keys.CUSTOM_API_KEY] = cleanKey
            prefs[Keys.LLM_BASE_URL] = cleanBase
            prefs[Keys.LLM_API_KEY] = cleanKey
            prefs[Keys.SELECTED_MODEL_ID] = cleanModel
            prefs[Keys.DETECTED_CHAT_BASE_URL] = cleanDetected
            prefs[Keys.TTS_BASE_URL] = cleanTtsBase
            prefs[Keys.TTS_MODEL_ID] = cleanTtsModel
            if (selectedVoiceId != null) {
                prefs[Keys.SELECTED_VOICE_ID] = cleanCredential(selectedVoiceId).ifBlank { "N2IVS1w4EtoT3dr4eOWO" }
            }
            prefs[Keys.AI_HORDE_KEY] = cleanHorde
            if (useAiHorde != null) {
                prefs[Keys.USE_AI_HORDE] = useAiHorde
            }
            if (narrationSpeed != null) {
                prefs[Keys.NARRATION_SPEED] = narrationSpeed.coerceIn(0.85f, 1.15f)
            }
        }
    }

    suspend fun setUseAiHorde(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.USE_AI_HORDE] = enabled
        }
    }

    suspend fun setAiHordeKey(apiKey: String) {
        val cleanHorde = cleanCredential(apiKey).ifBlank { "0000000000" }
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.AI_HORDE_KEY] = cleanHorde
        }
    }

    suspend fun setLlmConfig(
        llmBaseUrl: String,
        llmApiKey: String,
        selectedModelId: String,
        detectedChatBaseUrl: String = ""
    ) {
        val cleanBase = cleanCredential(llmBaseUrl)
        val cleanKey = cleanCredential(llmApiKey)
        val cleanModel = cleanCredential(selectedModelId)
        val cleanDetected = cleanCredential(detectedChatBaseUrl)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.LLM_BASE_URL] = cleanBase
            prefs[Keys.CUSTOM_BASE_URL] = cleanBase
            prefs[Keys.LLM_API_KEY] = cleanKey
            prefs[Keys.CUSTOM_API_KEY] = cleanKey
            prefs[Keys.SELECTED_MODEL_ID] = cleanModel
            prefs[Keys.CUSTOM_MODEL] = cleanModel
            prefs[Keys.DETECTED_CHAT_BASE_URL] = cleanDetected
        }
    }

    suspend fun setNarrationSpeed(speed: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.NARRATION_SPEED] = speed.coerceIn(0.85f, 1.15f)
        }
    }

    suspend fun setDetectedModelSelection(
        llmBaseUrl: String,
        llmApiKey: String,
        selectedModelId: String,
        detectedChatBaseUrl: String
    ) {
        setLlmConfig(
            llmBaseUrl = llmBaseUrl,
            llmApiKey = llmApiKey,
            selectedModelId = selectedModelId,
            detectedChatBaseUrl = detectedChatBaseUrl
        )
    }

    suspend fun setTtsBaseUrl(ttsBaseUrl: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.TTS_BASE_URL] = cleanCredential(ttsBaseUrl).ifBlank { "https://api.elevenlabs.io" }
        }
    }

    suspend fun setTtsModelId(ttsModelId: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.TTS_MODEL_ID] = cleanCredential(ttsModelId).ifBlank { "eleven_multilingual_v2" }
        }
    }

    suspend fun setCustomSpritePath(roleName: String, path: String?) {
        context.settingsDataStore.edit { prefs ->
            val map = parseSpritePaths(prefs[Keys.CUSTOM_SPRITE_PATHS_JSON]).toMutableMap()
            if (path.isNullOrBlank()) {
                map.remove(roleName)
            } else {
                map[roleName] = path
            }
            prefs[Keys.CUSTOM_SPRITE_PATHS_JSON] = serializeSpritePaths(map)
        }
    }

    suspend fun setSpriteStickerOutline(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SPRITE_STICKER_OUTLINE] = enabled
        }
    }

    suspend fun setCustomBgm(path: String, displayName: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_BGM_PATH] = path
            prefs[Keys.CUSTOM_BGM_NAME] = displayName
        }
    }

    suspend fun upsertPronunciationEntry(original: String, spoken: String) {
        val cleanOrig = original.trim()
        val cleanSpoken = spoken.trim()
        if (cleanOrig.isEmpty() || cleanSpoken.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val list = parsePronunciationDict(prefs[Keys.PRONUNCIATION_DICT_JSON]).toMutableList()
            val idx = list.indexOfFirst { it.original.equals(cleanOrig, ignoreCase = true) }
            val entry = PronunciationEntry(cleanOrig, cleanSpoken)
            if (idx >= 0) {
                list[idx] = entry
            } else {
                list.add(0, entry)
            }
            prefs[Keys.PRONUNCIATION_DICT_JSON] = serializePronunciationDict(list)
        }
    }

    suspend fun deletePronunciationEntry(original: String) {
        context.settingsDataStore.edit { prefs ->
            val list = parsePronunciationDict(prefs[Keys.PRONUNCIATION_DICT_JSON])
                .filterNot { it.original.equals(original, ignoreCase = true) }
            prefs[Keys.PRONUNCIATION_DICT_JSON] = serializePronunciationDict(list)
        }
    }

    suspend fun resetPronunciationDictionary() {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.PRONUNCIATION_DICT_JSON] =
                serializePronunciationDict(BuiltInPrompts.DEFAULT_PRONUNCIATION_DICTIONARY)
        }
    }

    private fun parseTemplates(json: String?): List<PromptTemplate> {
        if (json.isNullOrBlank()) return BuiltInPrompts.DEFAULT_TEMPLATES
        return try {
            val arr = JSONArray(json)
            val parsed = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        PromptTemplate(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            content = obj.getString("content"),
                            isBuiltIn = obj.optBoolean("isBuiltIn", false)
                        )
                    )
                }
            }
            if (parsed.isEmpty()) BuiltInPrompts.DEFAULT_TEMPLATES else parsed
        } catch (_: Exception) {
            BuiltInPrompts.DEFAULT_TEMPLATES
        }
    }

    private fun serializeTemplates(list: List<PromptTemplate>): String {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("content", t.content)
                    put("isBuiltIn", t.isBuiltIn)
                }
            )
        }
        return arr.toString()
    }

    private fun parseElevenLabsKeys(json: String?): List<ElevenLabsKeyEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        ElevenLabsKeyEntry(
                            key = obj.getString("key"),
                            remainingChars = if (obj.has("remainingChars") && !obj.isNull("remainingChars")) {
                                obj.getInt("remainingChars")
                            } else null,
                            limitChars = if (obj.has("limitChars") && !obj.isNull("limitChars")) {
                                obj.getInt("limitChars")
                            } else null,
                            tier = obj.optString("tier", "").ifBlank { null },
                            lastError = obj.optString("lastError", "").ifBlank { null },
                            lastCheckedAtMs = obj.optLong("lastCheckedAtMs", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeElevenLabsKeys(list: List<ElevenLabsKeyEntry>): String {
        val arr = JSONArray()
        list.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("key", entry.key)
                    if (entry.remainingChars != null) put("remainingChars", entry.remainingChars)
                    if (entry.limitChars != null) put("limitChars", entry.limitChars)
                    if (entry.tier != null) put("tier", entry.tier)
                    if (entry.lastError != null) put("lastError", entry.lastError)
                    put("lastCheckedAtMs", entry.lastCheckedAtMs)
                }
            )
        }
        return arr.toString()
    }

    private fun parseSpritePaths(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key ->
                    val path = obj.optString(key, "")
                    if (path.isNotBlank()) put(key, path)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun serializeSpritePaths(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun parsePronunciationDict(json: String?): List<PronunciationEntry> {
        if (json == null) return BuiltInPrompts.DEFAULT_PRONUNCIATION_DICTIONARY
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val orig = obj.optString("original", "").trim()
                    val spoken = obj.optString("spoken", "").trim()
                    if (orig.isNotEmpty() && spoken.isNotEmpty()) {
                        add(PronunciationEntry(orig, spoken))
                    }
                }
            }
        } catch (_: Exception) {
            BuiltInPrompts.DEFAULT_PRONUNCIATION_DICTIONARY
        }
    }

    private fun serializePronunciationDict(list: List<PronunciationEntry>): String {
        val arr = JSONArray()
        list.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("original", entry.original)
                    put("spoken", entry.spoken)
                }
            )
        }
        return arr.toString()
    }
}
