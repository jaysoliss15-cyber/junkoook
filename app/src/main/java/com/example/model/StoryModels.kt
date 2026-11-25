package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class CameraMotion {
    WHIP_PAN_LEFT,
    WHIP_PAN_RIGHT,
    PAN_LEFT,
    PAN_RIGHT,
    TRACKING_FOLLOW,
    IMPACT_SHAKE,
    ZOOM_IN,
    ZOOM_OUT,
    SHAKE,
    STATIC;

    companion object {
        fun fromRaw(raw: String?): CameraMotion {
            val clean = raw?.trim()?.uppercase() ?: return STATIC
            return entries.firstOrNull { it.name == clean } ?: STATIC
        }
    }
}

enum class MouthShape {
    CLOSED_M,
    SMILE_TEETH,
    ROUND_O,
    OPEN_WIDE
}

enum class ActorRole {
    NARRATOR,
    RICH_MAN,
    POOR_MAN,
    CHICKEN_MENTOR,
    HERO;

    companion object {
        fun fromRaw(raw: String?): ActorRole {
            val clean = raw?.trim()?.uppercase() ?: return NARRATOR
            return entries.firstOrNull { it.name == clean } ?: NARRATOR
        }
    }
}

enum class ActorAction {
    WALK_IN,
    TALK_BOUNCE,
    THUMBS_UP,
    POINT_STICK,
    SURPRISED_JUMP,
    CRY_SHAKE;

    companion object {
        fun fromRaw(raw: String?): ActorAction {
            val clean = raw?.trim()?.uppercase() ?: return TALK_BOUNCE
            return entries.firstOrNull { it.name == clean } ?: TALK_BOUNCE
        }
    }
}

enum class WorldFx {
    NONE,
    COIN_RAIN,
    FIRE_SPARKS,
    CHALKBOARD_DRAW;

    companion object {
        fun fromRaw(raw: String?): WorldFx {
            val clean = raw?.trim()?.uppercase() ?: return NONE
            return entries.firstOrNull { it.name == clean } ?: NONE
        }
    }
}

enum class StatusPill {
    NONE,
    GOAL,
    PROBLEM,
    FIX,
    SECRET;

    companion object {
        fun fromRaw(raw: String?): StatusPill {
            val clean = raw?.trim()?.uppercase() ?: return NONE
            return entries.firstOrNull { it.name == clean } ?: NONE
        }
    }
}

enum class HudWidget {
    NONE,
    PROGRESS_GAUGE,
    WAVEFORM_GRAPH,
    ANIMATED_CHECKLIST;

    companion object {
        fun fromRaw(raw: String?): HudWidget {
            val clean = raw?.trim()?.uppercase() ?: return NONE
            return entries.firstOrNull { it.name == clean } ?: NONE
        }
    }
}

enum class ProductionMode(
    val title: String,
    val subtitle: String,
    val badge: String
) {
    FULL_AI_STORY(
        title = "Full AI Story",
        subtitle = "Script, AI diorama, animated puppets & voiceover",
        badge = "AI STORY"
    ),
    CLIP_AUTO_EDITOR(
        title = "Clip + Auto-HUD",
        subtitle = "Import .mp4/.jpg/.png clips + auto voiceover, pills & HUD",
        badge = "AUTO-HUD"
    ),
    CROSS_SECTION_EXPLAINER(
        title = "2D Cutaway",
        subtitle = "Kurzgesagt cross-sections, pipes, fans & energy flow",
        badge = "CUTAWAY"
    );

    companion object {
        fun fromRaw(raw: String?): ProductionMode {
            val clean = raw?.trim()?.uppercase() ?: return FULL_AI_STORY
            return entries.firstOrNull { it.name == clean } ?: FULL_AI_STORY
        }
    }
}

enum class ShotFraming {
    AUTO,
    WIDE_FULL_BODY,
    MEDIUM_WAIST_UP,
    CLOSE_UP_FACE,
    RULE_OF_THIRDS_LEFT,
    RULE_OF_THIRDS_RIGHT;

    companion object {
        fun fromRaw(raw: String?): ShotFraming {
            val clean = raw?.trim()?.uppercase() ?: return AUTO
            return entries.firstOrNull { it.name == clean } ?: AUTO
        }
    }
}

enum class CharacterPose {
    AUTO,
    WALK_ACROSS_LEFT_TO_RIGHT,
    THREE_QUARTER_EXPLAIN,
    SIT_SLUMPED,
    SHOCK_AIRBORNE_RECOIL,
    POINT_AND_GESTURE,
    CELEBRATE_JUMP,
    TWO_CHARACTER_CONVERSATION;

    companion object {
        fun fromRaw(raw: String?): CharacterPose {
            val clean = raw?.trim()?.uppercase() ?: return AUTO
            return entries.firstOrNull { it.name == clean } ?: AUTO
        }
    }
}

enum class HandProp {
    NONE,
    POINTER_STICK,
    MONEY_BAG,
    PHONE_SCREEN,
    COFFEE_CUP,
    BASKET_GOODS,
    BOOK_NOTEBOOK,
    TOOL_WRENCH,
    MAGNIFYING_GLASS,
    LIGHTBULB;

    companion object {
        fun fromRaw(raw: String?): HandProp {
            val clean = raw?.trim()?.uppercase() ?: return NONE
            return when (clean) {
                "EGG_BASKET" -> BASKET_GOODS
                else -> entries.firstOrNull { it.name == clean } ?: NONE
            }
        }
    }
}

enum class ShotType {
    AUTO,
    WIDE_STAGE,
    MEDIUM_WAIST_UP,
    CLOSE_UP_REACTION,
    TRACKING_FOLLOW,
    WHIP_CUT;

    companion object {
        fun fromRaw(raw: String?): ShotType {
            val clean = raw?.trim()?.uppercase() ?: return AUTO
            return entries.firstOrNull { it.name == clean } ?: AUTO
        }
    }
}

enum class ActorExpression {
    HAPPY_TALK,
    ANGRY_EVIL,
    PANIC_SWEAT_FIRE,
    SHOCKED_WIDE_EYES,
    TONGUE_STING,
    SLEEPING_ZZZ;

    companion object {
        fun fromRaw(raw: String?): ActorExpression {
            val clean = raw?.trim()?.uppercase() ?: return HAPPY_TALK
            return entries.firstOrNull { it.name == clean } ?: HAPPY_TALK
        }
    }
}

enum class ActorHeadgear {
    NONE,
    CHEF_HAT,
    HARD_HAT,
    CROWN;

    companion object {
        fun fromRaw(raw: String?): ActorHeadgear {
            val clean = raw?.trim()?.uppercase() ?: return NONE
            return entries.firstOrNull { it.name == clean } ?: NONE
        }
    }
}

enum class MetaphorVisual {
    NONE,
    CHOMP_CHAIN,
    TISSUE_CROSS_SECTION,
    TIME_LAPSE_CLOCK,
    BOILING_POT_PROCESS,
    SUNBURST_SPOTLIGHT,
    CRACKING_EGG_CHICK,
    GROWING_MONEY_TREE,
    BRAIN_BATTERY_DRAIN,
    SCALE_BALANCE_COMPARE,
    ROCKET_GROWTH_CHART,
    LOCK_AND_KEY_SECRET,
    SHIELD_BLOCKING_ARROWS,
    DOMINO_CHAIN_REACTION;

    companion object {
        fun fromRaw(raw: String?): MetaphorVisual {
            val clean = raw?.trim()?.uppercase() ?: return NONE
            return entries.firstOrNull { it.name == clean } ?: NONE
        }
    }
}

enum class EnvironmentTheme {
    BRIGHT_MORNING_KITCHEN,
    SUNNY_MARKET_STREET,
    MODERN_OFFICE_DESK,
    COZY_BEDROOM_MIDNIGHT,
    CLASSROOM_CHALKBOARD,
    BANK_VAULT_FINANCE,
    SUNNY_PARK_NATURE,
    SCIENCE_LAB_INTERIOR,
    HOSPITAL_CLINIC,
    ABSTRACT_STUDIO_GRADIENT,
    DRAMATIC_SUNSET_CITY;

    companion object {
        fun fromRaw(raw: String?): EnvironmentTheme? {
            val clean = raw?.trim()?.uppercase() ?: return null
            return when (clean) {
                "MODERN_OFFICE" -> MODERN_OFFICE_DESK
                "COZY_BEDROOM_NIGHT" -> COZY_BEDROOM_MIDNIGHT
                "CLASSROOM" -> CLASSROOM_CHALKBOARD
                "FINANCE_VAULT" -> BANK_VAULT_FINANCE
                "SUNNY_PARK" -> SUNNY_PARK_NATURE
                "ABSTRACT_SUNBURST" -> ABSTRACT_STUDIO_GRADIENT
                else -> entries.firstOrNull { it.name == clean }
            }
        }
    }
}

enum class CharacterGender {
    MALE,
    FEMALE;

    companion object {
        fun fromRaw(raw: String?): CharacterGender? {
            val clean = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == clean }
        }
    }
}

enum class CharacterAge {
    CHILD,
    YOUNG_ADULT,
    MIDDLE_AGED,
    ELDERLY;

    companion object {
        fun fromRaw(raw: String?): CharacterAge? {
            val clean = raw?.trim()?.uppercase() ?: return null
            return when (clean) {
                "TEEN" -> CHILD
                "YOUNG" -> YOUNG_ADULT
                "ADULT" -> MIDDLE_AGED
                else -> entries.firstOrNull { it.name == clean }
            }
        }
    }
}

enum class CharacterOutfit {
    CASUAL_TSHIRT,
    MARKET_APRON,
    OFFICE_SUIT,
    STUDENT_UNIFORM,
    MEDICAL_SCRUBS,
    HOODIE,
    WORK_UNIFORM;

    companion object {
        fun fromRaw(raw: String?): CharacterOutfit? {
            val clean = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == clean }
        }
    }
}

enum class FlowDirection {
    UP,
    DOWN,
    LEFT_TO_RIGHT,
    RADIAL_BURST;

    companion object {
        fun fromRaw(raw: String?): FlowDirection {
            val clean = raw?.trim()?.uppercase() ?: return LEFT_TO_RIGHT
            return entries.firstOrNull { it.name == clean } ?: LEFT_TO_RIGHT
        }
    }
}

enum class DynamicDiagramType {
    NONE,
    BEFORE_AFTER_SPLIT,
    STEP_FLOW_ARROWS,
    METER_GAUGE_SHIFT,
    SCALE_WEIGH_OFF,
    SUNBURST_HERO_CALLOUT;

    companion object {
        fun fromRaw(raw: String?): DynamicDiagramType {
            val clean = raw?.trim()?.uppercase() ?: return NONE
            return entries.firstOrNull { it.name == clean } ?: NONE
        }
    }
}

enum class CutawaySubject {
    BRAIN_NEURON_SIGNAL,
    SKIN_WOUND_HEALING,
    TOOTH_ENAMEL_DECAY,
    HEART_ARTERY_FLOW,
    BATTERY_CIRCUIT_CHARGE,
    SOIL_ROOT_GROWTH;

    companion object {
        fun fromRaw(raw: String?): CutawaySubject? {
            val clean = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == clean }
        }
    }
}

enum class CutawayStage {
    SURFACE_ZOOM_IN,
    CROSS_SECTION_DAMAGE,
    IMMUNE_CLEANUP,
    STRAND_AND_VESSEL_REBUILD,
    CELL_MIGRATION_CLOSE,
    SURFACE_HEALED_ZOOM_OUT;

    companion object {
        fun fromRaw(raw: String?): CutawayStage? {
            val clean = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == clean }
        }
    }
}

data class PointerCallout(
    val label: String,
    val x: Float,
    val y: Float
)

data class SceneScript(
    val sceneId: Int,
    val tagalogNarration: String,
    val backgroundPrompt: String,
    val cameraMotion: CameraMotion,
    val actorRole: ActorRole,
    val actorAction: ActorAction,
    val worldFx: WorldFx,
    val statusPill: StatusPill = StatusPill.NONE,
    val calloutBanner: String = "",
    val hudWidget: HudWidget = HudWidget.NONE,
    val hudLabel: String = "",
    val hudFromValue: Int = 0,
    val hudToValue: Int = 100,
    val checklistItems: List<String> = emptyList(),
    val shotFraming: ShotFraming = ShotFraming.AUTO,
    val characterPose: CharacterPose = CharacterPose.AUTO,
    val handProp: HandProp = HandProp.NONE,
    val shotType: ShotType = ShotType.AUTO,
    val secondaryActorRole: ActorRole? = null,
    val expression: ActorExpression = ActorExpression.HAPPY_TALK,
    val headgear: ActorHeadgear = ActorHeadgear.NONE,
    val metaphorVisual: MetaphorVisual = MetaphorVisual.NONE,
    val topicPillLabel: String = "",
    val environmentTheme: EnvironmentTheme? = null,
    val cutawaySubject: CutawaySubject? = null,
    val characterGender: CharacterGender? = null,
    val characterAge: CharacterAge? = null,
    val characterOutfit: CharacterOutfit? = null,
    val outfitColorHex: String = "",
    val hairColorHex: String = "",
    val cutawayStage: CutawayStage? = null,
    val cutawayDiagramPrompt: String = "",
    val flowDirection: FlowDirection = FlowDirection.LEFT_TO_RIGHT,
    val diagramType: DynamicDiagramType = DynamicDiagramType.NONE,
    val leftLabel: String = "",
    val rightLabel: String = "",
    val step1: String = "",
    val step2: String = "",
    val step3: String = "",
    val pointerCallouts: List<PointerCallout> = emptyList(),
    val visualDescription: String = ""
) {
    val effectiveVisualDescription: String
        get() = visualDescription.ifBlank { backgroundPrompt }
}

data class WordTiming(
    val word: String,
    val startMs: Double,
    val endMs: Double
)

data class TimelineScene(
    val scene: SceneScript,
    val startMs: Double,
    val endMs: Double,
    val wordTimings: List<WordTiming>,
    val backdropBitmap: Bitmap? = null
) {
    val durationMs: Double
        get() = (endMs - startMs).coerceAtLeast(1.0)
}

data class PromptTemplate(
    val id: String,
    val name: String,
    val content: String,
    val isBuiltIn: Boolean = false
)

data class PronunciationEntry(
    val original: String,
    val spoken: String
)

data class ImportedClipItem(
    val uriString: String,
    val displayName: String,
    val isVideo: Boolean
)

data class ElevenLabsKeyEntry(
    val key: String,
    val remainingChars: Int? = null,
    val limitChars: Int? = null,
    val tier: String? = null,
    val lastError: String? = null,
    val lastCheckedAtMs: Long = 0L
) {
    val maskedKey: String
        get() = if (key.length <= 10) "****" else "${key.take(6)}...${key.takeLast(4)}"
}

data class StudioSettings(
    val promptTemplates: List<PromptTemplate> = emptyList(),
    val activePromptId: String = "",
    val elevenLabsKeys: List<ElevenLabsKeyEntry> = emptyList(),
    val selectedVoiceId: String = "N2IVS1w4EtoT3dr4eOWO",
    val ttsBaseUrl: String = "https://api.elevenlabs.io",
    val ttsModelId: String = "eleven_multilingual_v2",
    val llmBaseUrl: String = "",
    val llmApiKey: String = "",
    val selectedModelId: String = "",
    val detectedChatBaseUrl: String = "",
    val groqApiKey: String = "",
    val openRouterApiKey: String = "",
    val customBaseUrl: String = "",
    val customModel: String = "",
    val customApiKey: String = "",
    val aiHordeKey: String = "0000000000",
    val customSpritePaths: Map<String, String> = emptyMap(),
    val spriteStickerOutline: Boolean = true,
    val customBgmPath: String = "",
    val customBgmName: String = "",
    val pronunciationDictionary: List<PronunciationEntry> = emptyList(),
    val narrationSpeed: Float = 1.0f
) {
    val activeTemplate: PromptTemplate?
        get() = promptTemplates.firstOrNull { it.id == activePromptId } ?: promptTemplates.firstOrNull()

    val effectiveLlmBaseUrl: String
        get() = llmBaseUrl.ifBlank { customBaseUrl }.replace("\r", "").replace("\n", "").trim()

    val effectiveLlmApiKey: String
        get() = llmApiKey.ifBlank { customApiKey }.replace("\r", "").replace("\n", "").trim()

    val effectiveSelectedModelId: String
        get() = selectedModelId.ifBlank { customModel }.replace("\r", "").replace("\n", "").trim()

    val effectiveTtsBaseUrl: String
        get() = ttsBaseUrl.ifBlank { "https://api.elevenlabs.io" }.replace("\r", "").replace("\n", "").trim()

    val effectiveTtsModelId: String
        get() = ttsModelId.ifBlank { "eleven_multilingual_v2" }.replace("\r", "").replace("\n", "").trim()

    val effectiveVoiceId: String
        get() = selectedVoiceId.ifBlank { "N2IVS1w4EtoT3dr4eOWO" }.replace("\r", "").replace("\n", "").trim()
}

enum class PipelineStep(val label: String, val index: Int) {
    IDLE("Ready", 0),
    SCRIPT("Script", 1),
    AUDIO("Audio", 2),
    ART("Art", 3),
    PUPPET_RENDER("Puppet Render", 4),
    MUXING("Muxing", 5),
    COMPLETED("Done", 6),
    ERROR("Error", -1)
}

data class GenerationProgress(
    val step: PipelineStep = PipelineStep.IDLE,
    val stepProgress: Float = 0f,
    val overallProgress: Float = 0f,
    val statusMessage: String = "Ready to forge your Tagalog story",
    val sceneCount: Int = 0,
    val currentSceneIndex: Int = 0,
    val providerInfo: String = "",
    val outputVideoUri: Uri? = null,
    val outputDisplayName: String? = null,
    val errorMessage: String? = null,
    val isRunning: Boolean = false,
    val generatedScenesPreview: List<TimelineScene> = emptyList()
)

data class SavedVideoItem(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long
)
