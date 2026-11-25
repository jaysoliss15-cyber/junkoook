package com.example.render

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DiscretePathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.model.ActorAction
import com.example.model.ActorExpression
import com.example.model.ActorHeadgear
import com.example.model.ActorRole
import com.example.model.AnimationStyle
import com.example.model.CharacterAge
import com.example.model.CharacterGender
import com.example.model.CharacterOutfit
import com.example.model.CharacterPose
import com.example.model.CutawayStage
import com.example.model.CutawaySubject
import com.example.model.HandProp
import com.example.model.HudWidget
import com.example.model.MouthShape
import com.example.model.PointerCallout
import com.example.model.ProductionMode
import com.example.model.SceneScript
import com.example.model.ShotFraming
import com.example.model.StatusPill
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object ProceduralPuppetEngine {

    const val HUD_SAFE_LIFT_PX = -230f
    const val BREATHING_FREQ_HZ = 0.35f
    const val EYE_BLINK_PERIOD_SEC = 2.6f

    fun resolveMouthShape(
        isSpeaking: Boolean,
        activeWord: String?,
        localWordProgress: Float,
        speechPulse: Float
    ): MouthShape {
        if (!isSpeaking) return MouthShape.CLOSED_M
        val letters = activeWord?.lowercase()?.filter { it.isLetter() }.orEmpty()
        if (letters.isEmpty()) {
            return when {
                speechPulse > 0.65f -> MouthShape.OPEN_WIDE
                speechPulse > 0.30f -> MouthShape.SMILE_TEETH
                else -> MouthShape.CLOSED_M
            }
        }
        val idx = (localWordProgress.coerceIn(0f, 0.999f) * letters.length)
            .toInt()
            .coerceIn(0, letters.lastIndex)
        return when (letters[idx]) {
            'm', 'b', 'p' -> MouthShape.CLOSED_M
            'o', 'u', 'w' -> MouthShape.ROUND_O
            'a' -> MouthShape.OPEN_WIDE
            'e', 'i', 'y' -> MouthShape.SMILE_TEETH
            else -> when {
                speechPulse > 0.62f -> MouthShape.OPEN_WIDE
                speechPulse > 0.28f -> MouthShape.SMILE_TEETH
                else -> MouthShape.ROUND_O
            }
        }
    }

    private data class RigPose(
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val bodyTiltDeg: Float = 0f,
        val headTiltDeg: Float = 0f,
        val hairHatTiltDeg: Float = 0f,
        val perspectiveProfile: Float = 0.25f, // 0=front, 0.31=18-deg 3/4, 1.0=true side profile
        val facingDir: Float = 1f,             // +1 facing right, -1 facing left
        val leftShoulderDeg: Float = 20f,
        val leftElbowDeg: Float = -24f,
        val rightShoulderDeg: Float = -24f,
        val rightElbowDeg: Float = 26f,
        val rightHandTiltDeg: Float = 0f,
        val leftHipDeg: Float = 0f,
        val leftKneeDeg: Float = 0f,
        val rightHipDeg: Float = 0f,
        val rightKneeDeg: Float = 0f,
        val limbStretchY: Float = 1f,
        val limbSquashX: Float = 1f,
        val shadowScale: Float = 1f,
        val eyeScaleY: Float = 1f,
        val pupilScale: Float = 1f,
        val mouthOpen: Float = 0f,
        val mouthShape: MouthShape = MouthShape.CLOSED_M,
        val eyebrowOffset: Float = 0f,
        val eyebrowSlantDeg: Float = 0f,
        val showThumbsUp: Boolean = false,
        val handProp: HandProp = HandProp.NONE,
        val seatType: PuppetPropRenderer.SeatType? = null,
        val tearDropLevel: Float = 0f,
        val emoteType: Int = 0, // 0=none, 1=sweatdrop, 2=anger/shock, 3=sparkles
        val expression: ActorExpression = ActorExpression.HAPPY_TALK,
        val headgear: ActorHeadgear = ActorHeadgear.NONE,
        val gender: CharacterGender = CharacterGender.MALE,
        val age: CharacterAge = CharacterAge.MIDDLE_AGED,
        val outfit: CharacterOutfit = CharacterOutfit.CASUAL_TSHIRT,
        val outfitColorHex: String = "",
        val hairColorHex: String = ""
    )

    private data class RolePalette(
        val skin: Int,
        val skinShadow: Int,
        val primary: Int,
        val primaryShadow: Int,
        val secondary: Int,
        val accent: Int,
        val hair: Int,
        val iris: Int
    )

    fun hasActiveHudOverlay(
        scene: SceneScript,
        style: AnimationStyle,
        productionMode: ProductionMode = ProductionMode.FULL_AI_STORY
    ): Boolean {
        return scene.hudWidget != HudWidget.NONE ||
            style == AnimationStyle.TECH_EXPLAINER ||
            style == AnimationStyle.CROSS_SECTION ||
            productionMode == ProductionMode.CLIP_AUTO_EDITOR ||
            productionMode == ProductionMode.CROSS_SECTION_EXPLAINER
    }

    fun resolveShotFraming(scene: SceneScript): ShotFraming {
        if (scene.shotFraming != ShotFraming.AUTO) return scene.shotFraming
        return when ((scene.sceneId - 1).coerceAtLeast(0) % 5) {
            0 -> if (scene.sceneId <= 1) ShotFraming.MEDIUM_WAIST_UP else ShotFraming.WIDE_FULL_BODY
            1 -> ShotFraming.RULE_OF_THIRDS_LEFT
            2 -> ShotFraming.MEDIUM_WAIST_UP
            3 -> ShotFraming.CLOSE_UP_FACE
            else -> ShotFraming.RULE_OF_THIRDS_RIGHT
        }
    }

    fun resolveCharacterPose(scene: SceneScript): CharacterPose {
        if (scene.characterPose != CharacterPose.AUTO) return scene.characterPose
        return when (scene.actorAction) {
            ActorAction.WALK_IN -> CharacterPose.WALK_ACROSS_LEFT_TO_RIGHT
            ActorAction.SURPRISED_JUMP -> CharacterPose.SHOCK_AIRBORNE_RECOIL
            ActorAction.CRY_SHAKE -> CharacterPose.SIT_SLUMPED
            ActorAction.POINT_STICK -> CharacterPose.POINT_AND_GESTURE
            ActorAction.THUMBS_UP -> CharacterPose.CELEBRATE_JUMP
            ActorAction.TALK_BOUNCE -> when ((scene.sceneId - 1).coerceAtLeast(0) % 3) {
                1 -> CharacterPose.TWO_CHARACTER_CONVERSATION
                2 -> CharacterPose.POINT_AND_GESTURE
                else -> CharacterPose.THREE_QUARTER_EXPLAIN
            }
        }
    }

    fun resolveHandProp(scene: SceneScript, resolvedPose: CharacterPose): HandProp {
        if (scene.handProp != HandProp.NONE) return scene.handProp
        return when {
            scene.actorAction == ActorAction.POINT_STICK -> HandProp.POINTER_STICK
            resolvedPose == CharacterPose.POINT_AND_GESTURE && scene.actorRole == ActorRole.CHICKEN_MENTOR ->
                HandProp.POINTER_STICK
            else -> HandProp.NONE
        }
    }

    fun resolvePartnerRole(primaryRole: ActorRole, explicitSecondary: ActorRole?, sceneId: Int): ActorRole {
        if (explicitSecondary != null && explicitSecondary != primaryRole) return explicitSecondary
        return when (primaryRole) {
            ActorRole.HERO -> if (sceneId % 2 == 0) ActorRole.RICH_MAN else ActorRole.CHICKEN_MENTOR
            ActorRole.CHICKEN_MENTOR, ActorRole.RICH_MAN -> ActorRole.HERO
            ActorRole.POOR_MAN -> ActorRole.CHICKEN_MENTOR
            ActorRole.NARRATOR -> ActorRole.HERO
        }
    }

    fun resolveExpression(scene: SceneScript, style: AnimationStyle): ActorExpression {
        if (scene.expression != ActorExpression.HAPPY_TALK) return scene.expression
        if (style != AnimationStyle.VIRAL_2D_EXPLAINER) return scene.expression
        return when {
            scene.actorAction == ActorAction.SURPRISED_JUMP -> ActorExpression.SHOCKED_WIDE_EYES
            scene.actorAction == ActorAction.CRY_SHAKE -> ActorExpression.PANIC_SWEAT_FIRE
            scene.statusPill == StatusPill.PROBLEM ->
                if (scene.sceneId % 2 == 0) ActorExpression.PANIC_SWEAT_FIRE else ActorExpression.ANGRY_EVIL
            else -> ActorExpression.HAPPY_TALK
        }
    }

    fun resolveCutawaySubjectOrNull(scene: SceneScript): CutawaySubject? {
        scene.cutawaySubject?.let { return it }
        val text = "${scene.tagalogNarration} ${scene.backgroundPrompt} ${scene.cutawayDiagramPrompt} ${scene.topicPillLabel} ${scene.calloutBanner}".lowercase()
        return when {
            text.contains("utak") || text.contains("brain") || text.contains("neuron") ||
                text.contains("doomscroll") || text.contains("dopamine") || text.contains("synapse") -> CutawaySubject.BRAIN_NEURON_SIGNAL
            text.contains("ngipin") || text.contains("tooth") || text.contains("teeth") ||
                text.contains("enamel") || text.contains("cavity") || text.contains("toothache") -> CutawaySubject.TOOTH_ENAMEL_DECAY
            text.contains("puso") || text.contains("heart") || text.contains("artery") ||
                text.contains("cholesterol") || text.contains("blood pressure") -> CutawaySubject.HEART_ARTERY_FLOW
            text.contains("baterya") || text.contains("battery") || text.contains("circuit") ||
                text.contains("kuryente") || text.contains("volt") || text.contains("chip") -> CutawaySubject.BATTERY_CIRCUIT_CHARGE
            text.contains("halaman") || text.contains("puno") || text.contains("lupa") ||
                text.contains("soil") || text.contains("root") || text.contains("binhi") ||
                text.contains("seed") || text.contains("plant") -> CutawaySubject.SOIL_ROOT_GROWTH
            text.contains("sugat") || text.contains("wound") || text.contains("balat") ||
                text.contains("epidermis") || text.contains("scab") || text.contains("white blood") -> CutawaySubject.SKIN_WOUND_HEALING
            else -> null
        }
    }

    fun resolveCutawaySubject(scene: SceneScript): CutawaySubject {
        return resolveCutawaySubjectOrNull(scene) ?: CutawaySubject.SKIN_WOUND_HEALING
    }

    fun resolveCharacterGender(scene: SceneScript): CharacterGender {
        scene.characterGender?.let { return it }
        val text = "${scene.tagalogNarration} ${scene.backgroundPrompt}".lowercase()
        return if (
            text.contains("nanay") || text.contains("lola") || text.contains("ate ") ||
            text.contains("babae") || text.contains("tindera") || text.contains("dalaga") ||
            text.contains("mother") || text.contains("woman") || text.contains("girl") ||
            text.contains("nurse") || text.contains("queen")
        ) {
            CharacterGender.FEMALE
        } else {
            CharacterGender.MALE
        }
    }

    fun resolveCharacterAge(scene: SceneScript): CharacterAge {
        scene.characterAge?.let { return it }
        val text = "${scene.tagalogNarration} ${scene.backgroundPrompt}".lowercase()
        return when {
            text.contains("matanda") || text.contains("lola") || text.contains("lolo") ||
                text.contains("elderly") || text.contains("old ") || text.contains("senior") ||
                text.contains("retire") -> CharacterAge.ELDERLY
            text.contains("bata") || text.contains("kid") || text.contains("pupil") ||
                text.contains("child") -> CharacterAge.CHILD
            text.contains("estudyante") || text.contains("student") || text.contains("teen") ||
                text.contains("young") || text.contains("kabataan") -> CharacterAge.YOUNG_ADULT
            else -> CharacterAge.MIDDLE_AGED
        }
    }

    fun resolveCharacterOutfit(scene: SceneScript): CharacterOutfit {
        scene.characterOutfit?.let { return it }
        val text = "${scene.tagalogNarration} ${scene.backgroundPrompt}".lowercase()
        return when {
            text.contains("doktor") || text.contains("doctor") || text.contains("nurse") ||
                text.contains("nars") || text.contains("hospital") || text.contains("clinic") ||
                text.contains("klinika") -> CharacterOutfit.MEDICAL_SCRUBS
            text.contains("tindera") || text.contains("palengke") || text.contains("market") ||
                text.contains("kusina") || text.contains("kitchen") || text.contains("luto") ||
                text.contains("apron") -> CharacterOutfit.MARKET_APRON
            text.contains("estudyante") || text.contains("student") || text.contains("school") ||
                text.contains("paaralan") || text.contains("classroom") || text.contains("pupil") -> CharacterOutfit.STUDENT_UNIFORM
            text.contains("mechanic") || text.contains("mekaniko") || text.contains("factory") ||
                text.contains("construction") || text.contains("trabahador") || text.contains("wrench") -> CharacterOutfit.WORK_UNIFORM
            text.contains("hoodie") || text.contains("doomscroll") || text.contains("gamer") ||
                text.contains("bedroom") -> CharacterOutfit.HOODIE
            text.contains("opisina") || text.contains("office") || text.contains("bank") ||
                text.contains("bangko") || text.contains("ceo") || scene.actorRole == ActorRole.RICH_MAN -> CharacterOutfit.OFFICE_SUIT
            else -> CharacterOutfit.CASUAL_TSHIRT
        }
    }

    fun parseHexColorOrNull(hex: String?): Int? {
        val raw = hex?.trim()?.removePrefix("#").orEmpty()
        if (raw.length != 6 && raw.length != 8) return null
        val parsed = raw.toLongOrNull(16) ?: return null
        val argb = if (raw.length == 6) (0xFF000000L or parsed) else parsed
        return argb.toInt()
    }

    fun resolveCutawayStage(scene: SceneScript): CutawayStage {
        scene.cutawayStage?.let { return it }
        val text = "${scene.tagalogNarration} ${scene.backgroundPrompt} ${scene.cutawayDiagramPrompt}".lowercase()
        return when {
            text.contains("heal") || text.contains("galing") || text.contains("sarado") || text.contains("langib") || text.contains("scab") || text.contains("bloom") ->
                CutawayStage.SURFACE_HEALED_ZOOM_OUT
            text.contains("white blood") || text.contains("immune") || text.contains("linis") || text.contains("bakterya") || text.contains("plaque") || text.contains("overload") ->
                CutawayStage.IMMUNE_CLEANUP
            text.contains("vessel") || text.contains("ugat") || text.contains("fibrin") || text.contains("strand") || text.contains("rebuild") || text.contains("signal") || text.contains("charge") ->
                CutawayStage.STRAND_AND_VESSEL_REBUILD
            text.contains("migrate") || text.contains("migration") || text.contains("repair cell") || text.contains("dikit") || text.contains("cell") || text.contains("flow") ->
                CutawayStage.CELL_MIGRATION_CLOSE
            text.contains("sugat") || text.contains("damage") || text.contains("dugo") || text.contains("biyak") || text.contains("sira") || text.contains("decay") || scene.statusPill == StatusPill.PROBLEM ->
                CutawayStage.CROSS_SECTION_DAMAGE
            else -> CutawayStage.entries[(scene.sceneId - 1).coerceAtLeast(0) % CutawayStage.entries.size]
        }
    }

    fun resolvePointerCallouts(
        scene: SceneScript,
        resolvedStage: CutawayStage = resolveCutawayStage(scene),
        resolvedSubject: CutawaySubject? = resolveCutawaySubjectOrNull(scene)
    ): List<PointerCallout> {
        if (scene.pointerCallouts.isNotEmpty()) {
            return scene.pointerCallouts
                .filter { it.label.isNotBlank() }
                .take(2)
                .map { it.copy(label = it.label.trim().uppercase()) }
        }
        if (resolvedSubject == null) {
            val topLabel = scene.topicPillLabel.ifBlank { scene.calloutBanner }.trim().uppercase()
                .ifBlank {
                    scene.tagalogNarration
                        .split(Regex("\\s+"))
                        .filter { it.length >= 5 }
                        .take(2)
                        .joinToString(" ")
                        .uppercase()
                        .ifBlank { "KEY LAYER" }
                }
            return listOf(
                PointerCallout(topLabel.take(20), 135f, 165f)
            )
        }
        return when (resolvedSubject) {
            CutawaySubject.SKIN_WOUND_HEALING -> when (resolvedStage) {
                CutawayStage.SURFACE_ZOOM_IN -> listOf(
                    PointerCallout("EPIDERMIS SURFACE", 135f, 126f)
                )
                CutawayStage.CROSS_SECTION_DAMAGE -> listOf(
                    PointerCallout("TISSUE BREACH", 135f, 146f),
                    PointerCallout("BLOOD VESSEL", 92f, 246f)
                )
                CutawayStage.IMMUNE_CLEANUP -> listOf(
                    PointerCallout("WHITE BLOOD CELLS", 132f, 174f),
                    PointerCallout("RED BLOOD CELLS", 184f, 248f)
                )
                CutawayStage.STRAND_AND_VESSEL_REBUILD -> listOf(
                    PointerCallout("FIBRIN STRANDS", 135f, 166f),
                    PointerCallout("NEW BLOOD VESSELS", 148f, 218f)
                )
                CutawayStage.CELL_MIGRATION_CLOSE -> listOf(
                    PointerCallout("MIGRATING CELLS", 118f, 152f),
                    PointerCallout("ADIPOSE BASE", 188f, 316f)
                )
                CutawayStage.SURFACE_HEALED_ZOOM_OUT -> listOf(
                    PointerCallout("CRACKED SCAB", 135f, 84f),
                    PointerCallout("HEALED", 135f, 122f)
                )
            }
            CutawaySubject.BRAIN_NEURON_SIGNAL -> when (resolvedStage) {
                CutawayStage.SURFACE_ZOOM_IN -> listOf(
                    PointerCallout("CORTEX FOLDS", 135f, 128f)
                )
                CutawayStage.CROSS_SECTION_DAMAGE -> listOf(
                    PointerCallout("DOPAMINE SPIKE", 126f, 156f),
                    PointerCallout("SIGNAL NOISE", 172f, 242f)
                )
                CutawayStage.IMMUNE_CLEANUP -> listOf(
                    PointerCallout("GLIAL CLEANUP", 128f, 178f),
                    PointerCallout("AXON SHEATH", 178f, 246f)
                )
                CutawayStage.STRAND_AND_VESSEL_REBUILD -> listOf(
                    PointerCallout("SYNAPSE BRIDGE", 135f, 174f),
                    PointerCallout("NEURAL PATHWAY", 156f, 232f)
                )
                CutawayStage.CELL_MIGRATION_CLOSE -> listOf(
                    PointerCallout("MYELIN REPAIR", 122f, 168f),
                    PointerCallout("STEADY PULSE", 182f, 286f)
                )
                CutawayStage.SURFACE_HEALED_ZOOM_OUT -> listOf(
                    PointerCallout("BALANCED FOCUS", 135f, 126f),
                    PointerCallout("HEALED", 135f, 200f)
                )
            }
            CutawaySubject.TOOTH_ENAMEL_DECAY -> when (resolvedStage) {
                CutawayStage.SURFACE_ZOOM_IN -> listOf(
                    PointerCallout("ENAMEL CROWN", 135f, 128f)
                )
                CutawayStage.CROSS_SECTION_DAMAGE -> listOf(
                    PointerCallout("ACID CAVITY", 135f, 146f),
                    PointerCallout("DENTIN LAYER", 96f, 224f)
                )
                CutawayStage.IMMUNE_CLEANUP -> listOf(
                    PointerCallout("FLUORIDE SHIELD", 135f, 150f),
                    PointerCallout("PULP NERVE", 152f, 274f)
                )
                CutawayStage.STRAND_AND_VESSEL_REBUILD -> listOf(
                    PointerCallout("MINERAL MATRIX", 135f, 162f),
                    PointerCallout("DENTIN TUBULES", 172f, 232f)
                )
                CutawayStage.CELL_MIGRATION_CLOSE -> listOf(
                    PointerCallout("ENAMEL SEAL", 135f, 140f),
                    PointerCallout("HEALTHY PULP", 135f, 276f)
                )
                CutawayStage.SURFACE_HEALED_ZOOM_OUT -> listOf(
                    PointerCallout("SEALED CROWN", 135f, 120f),
                    PointerCallout("HEALED", 135f, 166f)
                )
            }
            CutawaySubject.HEART_ARTERY_FLOW -> when (resolvedStage) {
                CutawayStage.SURFACE_ZOOM_IN -> listOf(
                    PointerCallout("CORONARY WALL", 135f, 134f)
                )
                CutawayStage.CROSS_SECTION_DAMAGE -> listOf(
                    PointerCallout("PLAQUE BUILDUP", 135f, 172f),
                    PointerCallout("NARROWED LUMEN", 135f, 234f)
                )
                CutawayStage.IMMUNE_CLEANUP -> listOf(
                    PointerCallout("LIPID REMOVAL", 128f, 186f),
                    PointerCallout("BLOOD STREAM", 182f, 238f)
                )
                CutawayStage.STRAND_AND_VESSEL_REBUILD -> listOf(
                    PointerCallout("ELASTIC LINING", 135f, 174f),
                    PointerCallout("OXYGEN FLOW", 162f, 236f)
                )
                CutawayStage.CELL_MIGRATION_CLOSE -> listOf(
                    PointerCallout("CLEAR ARTERY", 135f, 224f),
                    PointerCallout("MUSCLE WALL", 176f, 304f)
                )
                CutawayStage.SURFACE_HEALED_ZOOM_OUT -> listOf(
                    PointerCallout("OPEN VESSEL", 135f, 176f),
                    PointerCallout("HEALED", 135f, 236f)
                )
            }
            CutawaySubject.BATTERY_CIRCUIT_CHARGE -> when (resolvedStage) {
                CutawayStage.SURFACE_ZOOM_IN -> listOf(
                    PointerCallout("ALUMINUM CASING", 135f, 126f)
                )
                CutawayStage.CROSS_SECTION_DAMAGE -> listOf(
                    PointerCallout("DENDRITE SHORT", 135f, 168f),
                    PointerCallout("ANODE CELL", 88f, 246f)
                )
                CutawayStage.IMMUNE_CLEANUP -> listOf(
                    PointerCallout("THERMAL VENT", 135f, 148f),
                    PointerCallout("ION BALANCE", 174f, 238f)
                )
                CutawayStage.STRAND_AND_VESSEL_REBUILD -> listOf(
                    PointerCallout("SEPARATOR MESH", 135f, 186f),
                    PointerCallout("ELECTRON FLOW", 172f, 246f)
                )
                CutawayStage.CELL_MIGRATION_CLOSE -> listOf(
                    PointerCallout("CATHODE CHARGE", 156f, 212f),
                    PointerCallout("COPPER BUS", 135f, 308f)
                )
                CutawayStage.SURFACE_HEALED_ZOOM_OUT -> listOf(
                    PointerCallout("100% POWER", 135f, 130f),
                    PointerCallout("HEALED", 135f, 216f)
                )
            }
            CutawaySubject.SOIL_ROOT_GROWTH -> when (resolvedStage) {
                CutawayStage.SURFACE_ZOOM_IN -> listOf(
                    PointerCallout("TOPSOIL CRUST", 135f, 132f)
                )
                CutawayStage.CROSS_SECTION_DAMAGE -> listOf(
                    PointerCallout("DRY CRACK", 135f, 144f),
                    PointerCallout("SEED EMBRYO", 135f, 214f)
                )
                CutawayStage.IMMUNE_CLEANUP -> listOf(
                    PointerCallout("WATER POCKETS", 118f, 182f),
                    PointerCallout("NUTRIENT IONS", 178f, 254f)
                )
                CutawayStage.STRAND_AND_VESSEL_REBUILD -> listOf(
                    PointerCallout("ROOT NETWORK", 135f, 224f),
                    PointerCallout("GREEN SPROUT", 135f, 114f)
                )
                CutawayStage.CELL_MIGRATION_CLOSE -> listOf(
                    PointerCallout("XYLEM UPTAKE", 135f, 208f),
                    PointerCallout("SUBSOIL BASE", 184f, 308f)
                )
                CutawayStage.SURFACE_HEALED_ZOOM_OUT -> listOf(
                    PointerCallout("FULL BLOOM", 135f, 92f),
                    PointerCallout("HEALED", 135f, 154f)
                )
            }
        }
    }

    fun drawCharacter(
        canvas: Canvas,
        stageW: Float,
        stageH: Float,
        role: ActorRole,
        action: ActorAction,
        style: AnimationStyle,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        isSpeaking: Boolean,
        speechPulse: Float,
        hasActiveHud: Boolean = false,
        shotFraming: ShotFraming = ShotFraming.WIDE_FULL_BODY,
        customSprite: Bitmap? = null,
        scaleMultiplier: Float = 1f,
        extraOffsetX: Float = 0f,
        characterPose: CharacterPose = CharacterPose.AUTO,
        handProp: HandProp = HandProp.NONE,
        secondaryRole: ActorRole? = null,
        sceneDurationSec: Float = 4f,
        sceneId: Int = 1,
        customSprites: Map<ActorRole, Bitmap> = emptyMap(),
        expression: ActorExpression = ActorExpression.HAPPY_TALK,
        headgear: ActorHeadgear = ActorHeadgear.NONE,
        characterGender: CharacterGender = CharacterGender.MALE,
        characterAge: CharacterAge = CharacterAge.MIDDLE_AGED,
        characterOutfit: CharacterOutfit = CharacterOutfit.CASUAL_TSHIRT,
        outfitColorHex: String = "",
        hairColorHex: String = "",
        mouthShape: MouthShape = MouthShape.CLOSED_M
    ) {
        val resolvedPose = if (characterPose != CharacterPose.AUTO) {
            characterPose
        } else {
            when (action) {
                ActorAction.WALK_IN -> CharacterPose.WALK_ACROSS_LEFT_TO_RIGHT
                ActorAction.SURPRISED_JUMP -> CharacterPose.SHOCK_AIRBORNE_RECOIL
                ActorAction.CRY_SHAKE -> CharacterPose.SIT_SLUMPED
                ActorAction.POINT_STICK -> CharacterPose.POINT_AND_GESTURE
                ActorAction.THUMBS_UP -> CharacterPose.CELEBRATE_JUMP
                ActorAction.TALK_BOUNCE -> CharacterPose.THREE_QUARTER_EXPLAIN
            }
        }
        val effectiveProp = if (handProp != HandProp.NONE) {
            handProp
        } else if (action == ActorAction.POINT_STICK) {
            HandProp.POINTER_STICK
        } else {
            HandProp.NONE
        }

        if (resolvedPose == CharacterPose.TWO_CHARACTER_CONVERSATION) {
            drawTwoCharacterConversation(
                canvas = canvas,
                stageW = stageW,
                stageH = stageH,
                primaryRole = role,
                secondaryRole = secondaryRole,
                action = action,
                style = style,
                sceneElapsedSec = sceneElapsedSec,
                sceneDurationSec = sceneDurationSec,
                globalTimeSec = globalTimeSec,
                isSpeaking = isSpeaking,
                speechPulse = speechPulse,
                hasActiveHud = hasActiveHud,
                handProp = effectiveProp,
                scaleMultiplier = scaleMultiplier,
                extraOffsetX = extraOffsetX,
                sceneId = sceneId,
                primaryCustomSprite = customSprite ?: customSprites[role],
                customSprites = customSprites,
                expression = expression,
                headgear = headgear,
                characterGender = characterGender,
                characterAge = characterAge,
                characterOutfit = characterOutfit,
                outfitColorHex = outfitColorHex,
                hairColorHex = hairColorHex,
                mouthShape = mouthShape
            )
            return
        }

        val spring = PuppetSpringSolver.solve(
            pose = resolvedPose,
            sceneElapsedSec = sceneElapsedSec,
            sceneDurationSec = sceneDurationSec
        )

        val pose = computeRigPose(
            resolvedPose = resolvedPose,
            action = action,
            style = style,
            sceneElapsedSec = sceneElapsedSec,
            sceneDurationSec = sceneDurationSec,
            globalTimeSec = globalTimeSec,
            isSpeaking = isSpeaking,
            speechPulse = speechPulse,
            shotFraming = shotFraming,
            handProp = effectiveProp,
            spring = spring,
            sceneId = sceneId,
            facingOverride = if (shotFraming == ShotFraming.RULE_OF_THIRDS_RIGHT) -1f else 1f,
            expression = expression,
            headgear = headgear,
            gender = characterGender,
            age = characterAge,
            outfit = characterOutfit,
            outfitColorHex = outfitColorHex,
            hairColorHex = hairColorHex,
            mouthShape = mouthShape
        )

        drawSingleActor(
            canvas = canvas,
            stageW = stageW,
            stageH = stageH,
            role = role,
            pose = pose,
            style = style,
            globalTimeSec = globalTimeSec,
            isSpeaking = isSpeaking,
            speechPulse = speechPulse,
            hasActiveHud = hasActiveHud,
            shotFraming = shotFraming,
            sceneElapsedSec = sceneElapsedSec,
            customSprite = customSprite ?: customSprites[role],
            scaleMultiplier = scaleMultiplier,
            extraOffsetX = extraOffsetX,
            applyFramingOffset = true
        )
    }

    private fun drawTwoCharacterConversation(
        canvas: Canvas,
        stageW: Float,
        stageH: Float,
        primaryRole: ActorRole,
        secondaryRole: ActorRole?,
        action: ActorAction,
        style: AnimationStyle,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float,
        isSpeaking: Boolean,
        speechPulse: Float,
        hasActiveHud: Boolean,
        handProp: HandProp,
        scaleMultiplier: Float,
        extraOffsetX: Float,
        sceneId: Int,
        primaryCustomSprite: Bitmap?,
        customSprites: Map<ActorRole, Bitmap>,
        expression: ActorExpression = ActorExpression.HAPPY_TALK,
        headgear: ActorHeadgear = ActorHeadgear.NONE,
        characterGender: CharacterGender = CharacterGender.MALE,
        characterAge: CharacterAge = CharacterAge.MIDDLE_AGED,
        characterOutfit: CharacterOutfit = CharacterOutfit.CASUAL_TSHIRT,
        outfitColorHex: String = "",
        hairColorHex: String = "",
        mouthShape: MouthShape = MouthShape.CLOSED_M
    ) {
        val partner = resolvePartnerRole(primaryRole, secondaryRole, sceneId)
        val primaryOnRight = primaryRole == ActorRole.CHICKEN_MENTOR || primaryRole == ActorRole.RICH_MAN
        val leftRole = if (primaryOnRight) partner else primaryRole
        val rightRole = if (primaryOnRight) primaryRole else partner
        val leftIsSpeaker = !primaryOnRight

        val spring = PuppetSpringSolver.solve(
            pose = CharacterPose.TWO_CHARACTER_CONVERSATION,
            sceneElapsedSec = sceneElapsedSec,
            sceneDurationSec = sceneDurationSec
        )

        // Continuous 0.35 Hz chest/shoulder breathing + 2.6s eye blinks + stressed-syllable head nods
        val speakerBreath = sin(globalTimeSec * 2f * PI.toFloat() * BREATHING_FREQ_HZ)
        val listenerBreath = sin((globalTimeSec + 0.7f) * 2f * PI.toFloat() * BREATHING_FREQ_HZ)

        val listenerBlinkPhase = (globalTimeSec + 1.3f) % EYE_BLINK_PERIOD_SEC
        val listenerEyeScaleY = if (listenerBlinkPhase > EYE_BLINK_PERIOD_SEC - 0.16f) {
            val b = (listenerBlinkPhase - (EYE_BLINK_PERIOD_SEC - 0.16f)) / 0.16f
            abs(1f - 2f * b).coerceIn(0.08f, 1f)
        } else 1f
        val listenerNod = sin(globalTimeSec * 4.2f) * 3.8f + listenerBreath * 2.2f

        val speakerBlinkPhase = globalTimeSec % EYE_BLINK_PERIOD_SEC
        val speakerEyeScaleY = if (speakerBlinkPhase > EYE_BLINK_PERIOD_SEC - 0.16f) {
            val b = (speakerBlinkPhase - (EYE_BLINK_PERIOD_SEC - 0.16f)) / 0.16f
            abs(1f - 2f * b).coerceIn(0.08f, 1f)
        } else 1f
        val talkMouth = if (isSpeaking) (0.28f + 0.72f * speechPulse).coerceIn(0.1f, 1f) else 0.08f
        val gestureWave = sin(globalTimeSec * 6.8f) * (if (isSpeaking) 22f else 9f)
        val speakerSyllableNod = if (isSpeaking) {
            sin(speechPulse * PI.toFloat()) * 3.6f * (if (sin(globalTimeSec * 7.5f) >= 0f) 1f else -0.6f)
        } else {
            speakerBreath * 1.4f
        }

        fun buildParticipantPose(isSpeaker: Boolean, facingDir: Float): RigPose {
            return if (isSpeaker) {
                RigPose(
                    offsetY = -abs(sin(globalTimeSec * 8f)) * (if (isSpeaking) 9f else 3f) - speakerBreath * 2.4f + spring.verticalBounceLagPx * 0.4f,
                    scaleX = 1f + speakerBreath * 0.013f,
                    scaleY = 1f - speakerBreath * 0.011f,
                    bodyTiltDeg = facingDir * (6.5f + sin(globalTimeSec * 3.8f) * 2.2f + speakerBreath * 0.9f),
                    headTiltDeg = facingDir * 3f + speakerSyllableNod + spring.headLagDeg * 0.45f,
                    hairHatTiltDeg = spring.hairHatTiltDeg * 0.55f,
                    perspectiveProfile = 0.34f,
                    facingDir = facingDir,
                    leftShoulderDeg = 22f + gestureWave * 0.45f + speakerBreath * 2.4f,
                    leftElbowDeg = -46f + gestureWave * 0.35f,
                    rightShoulderDeg = -64f - gestureWave - speakerBreath * 2.4f + spring.trailingArmLagDeg * 0.35f,
                    rightElbowDeg = -32f + sin(globalTimeSec * 9.5f) * 14f,
                    rightHandTiltDeg = sin(globalTimeSec * 7f) * 12f,
                    eyeScaleY = speakerEyeScaleY,
                    mouthOpen = talkMouth,
                    mouthShape = mouthShape,
                    eyebrowOffset = if (isSpeaking) -speechPulse * 5f else -2f,
                    eyebrowSlantDeg = if (isSpeaking) 6f else 2f,
                    handProp = handProp,
                    showThumbsUp = action == ActorAction.THUMBS_UP,
                    expression = expression,
                    headgear = headgear,
                    gender = characterGender,
                    age = characterAge,
                    outfit = characterOutfit,
                    outfitColorHex = outfitColorHex,
                    hairColorHex = hairColorHex
                )
            } else {
                RigPose(
                    offsetY = -abs(sin(globalTimeSec * 2.4f)) * 3f - listenerBreath * 2.2f,
                    scaleX = 1f + listenerBreath * 0.012f,
                    scaleY = 1f - listenerBreath * 0.010f,
                    bodyTiltDeg = -facingDir * 1.5f + listenerBreath * 0.8f,
                    headTiltDeg = listenerNod * facingDir,
                    hairHatTiltDeg = listenerNod * facingDir * 1.25f,
                    perspectiveProfile = 0.34f,
                    facingDir = facingDir,
                    leftShoulderDeg = 18f + listenerBreath * 2.5f,
                    leftElbowDeg = -38f + listenerBreath * 2f,
                    rightShoulderDeg = -18f - listenerBreath * 2.5f,
                    rightElbowDeg = -48f - listenerBreath * 2f,
                    eyeScaleY = listenerEyeScaleY,
                    mouthOpen = 0.05f,
                    mouthShape = MouthShape.CLOSED_M,
                    eyebrowOffset = -4f + sin(globalTimeSec * 2.1f) * 2.5f,
                    eyebrowSlantDeg = -4f,
                    expression = ActorExpression.HAPPY_TALK,
                    headgear = ActorHeadgear.NONE
                )
            }
        }

        val pairScale = scaleMultiplier * 0.86f
        val leftOffset = extraOffsetX - 210f
        val rightOffset = extraOffsetX + 210f

        val leftPose = buildParticipantPose(isSpeaker = leftIsSpeaker, facingDir = 1f)
        val rightPose = buildParticipantPose(isSpeaker = !leftIsSpeaker, facingDir = -1f)

        val leftSprite = if (!primaryOnRight) primaryCustomSprite else customSprites[leftRole]
        val rightSprite = if (primaryOnRight) primaryCustomSprite else customSprites[rightRole]

        drawSingleActor(
            canvas = canvas,
            stageW = stageW,
            stageH = stageH,
            role = leftRole,
            pose = leftPose,
            style = style,
            globalTimeSec = globalTimeSec,
            isSpeaking = leftIsSpeaker && isSpeaking,
            speechPulse = if (leftIsSpeaker) speechPulse else 0f,
            hasActiveHud = hasActiveHud,
            shotFraming = ShotFraming.WIDE_FULL_BODY,
            sceneElapsedSec = sceneElapsedSec,
            customSprite = leftSprite,
            scaleMultiplier = pairScale,
            extraOffsetX = leftOffset,
            applyFramingOffset = false
        )
        drawSingleActor(
            canvas = canvas,
            stageW = stageW,
            stageH = stageH,
            role = rightRole,
            pose = rightPose,
            style = style,
            globalTimeSec = globalTimeSec,
            isSpeaking = !leftIsSpeaker && isSpeaking,
            speechPulse = if (!leftIsSpeaker) speechPulse else 0f,
            hasActiveHud = hasActiveHud,
            shotFraming = ShotFraming.WIDE_FULL_BODY,
            sceneElapsedSec = sceneElapsedSec,
            customSprite = rightSprite,
            scaleMultiplier = pairScale,
            extraOffsetX = rightOffset,
            applyFramingOffset = false
        )
    }

    private fun drawSingleActor(
        canvas: Canvas,
        stageW: Float,
        stageH: Float,
        role: ActorRole,
        pose: RigPose,
        style: AnimationStyle,
        globalTimeSec: Float,
        isSpeaking: Boolean,
        speechPulse: Float,
        hasActiveHud: Boolean,
        shotFraming: ShotFraming,
        sceneElapsedSec: Float,
        customSprite: Bitmap?,
        scaleMultiplier: Float,
        extraOffsetX: Float,
        applyFramingOffset: Boolean
    ) {
        val scaleUnit = stageW / 1080f
        val hudLiftPx = if (hasActiveHud) HUD_SAFE_LIFT_PX * scaleUnit else 0f
        val hudScale = (if (hasActiveHud) 0.82f else 1.0f) * scaleMultiplier

        val framingEnter = Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, (sceneElapsedSec / 0.55f).coerceIn(0f, 1f))
        val framingOffsetX = if (!applyFramingOffset) 0f else when (shotFraming) {
            ShotFraming.RULE_OF_THIRDS_LEFT -> -165f * scaleUnit * framingEnter
            ShotFraming.RULE_OF_THIRDS_RIGHT -> 165f * scaleUnit * framingEnter
            else -> 0f
        }

        val groundAnchorY = stageH * 0.685f + hudLiftPx
        val rootX = stageW * 0.5f + framingOffsetX + extraOffsetX * scaleUnit + pose.offsetX * scaleUnit * hudScale
        val rootY = groundAnchorY + pose.offsetY * scaleUnit * hudScale

        canvas.save()
        if (hasActiveHud) {
            canvas.clipRect(0f, 0f, stageW, stageH * (1112f / 1920f))
        }

        // Ground contact shadow (dynamically scales with stride height & airborne jump recoil)
        if (style != AnimationStyle.STICKMAN_WHITEBOARD) {
            val shadowAlpha = when (style) {
                AnimationStyle.PAPER_DIORAMA -> 0x66
                AnimationStyle.DARK_SUSPENSE -> 0x88
                else -> 0x4E
            }
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (shadowAlpha shl 24)
                maskFilter = BlurMaskFilter((18f * scaleUnit).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            }
            val shW = 165f * scaleUnit * hudScale * pose.shadowScale
            val shH = 22f * scaleUnit * hudScale * pose.shadowScale
            canvas.drawOval(
                RectF(
                    rootX - shW,
                    groundAnchorY - shH * 0.9f,
                    rootX + shW,
                    groundAnchorY + shH
                ),
                shadowPaint
            )
        }

        // Seated furniture prop for SIT_SLUMPED
        if (pose.seatType != null && style != AnimationStyle.STICKMAN_WHITEBOARD) {
            val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0E1018.toInt()
                setStyle(Paint.Style.STROKE)
                strokeWidth = 4.5f * scaleUnit * hudScale
                strokeJoin = Paint.Join.ROUND
            }
            PuppetPropRenderer.drawSeatProp(
                canvas = canvas,
                rootX = rootX,
                groundAnchorY = groundAnchorY,
                u = scaleUnit * hudScale,
                seatType = pose.seatType,
                silhouetteOnly = false,
                outlinePaint = outlinePaint
            )
        }

        // Custom transparent PNG character sprite path
        if (customSprite != null && !customSprite.isRecycled) {
            drawCustomSpriteRig(
                canvas = canvas,
                sprite = customSprite,
                rootX = rootX,
                rootY = rootY,
                scaleUnit = scaleUnit,
                hudScale = hudScale,
                pose = pose,
                isSpeaking = isSpeaking,
                speechPulse = speechPulse,
                globalTimeSec = globalTimeSec
            )
            if (style == AnimationStyle.CHIBI_STORYTIME || pose.emoteType != 0) {
                drawAnimeEmotes(
                    canvas = canvas,
                    cx = rootX,
                    topY = rootY - 510f * scaleUnit * hudScale,
                    u = scaleUnit * hudScale,
                    pose = pose,
                    globalTimeSec = globalTimeSec,
                    style = style
                )
            }
            canvas.restore()
            return
        }

        if (style == AnimationStyle.STICKMAN_WHITEBOARD) {
            canvas.save()
            canvas.scale(hudScale, hudScale, rootX, rootY)
            drawWhiteboardStickman(canvas, rootX, rootY, scaleUnit, role, pose, globalTimeSec)
            canvas.restore()
            canvas.restore()
            return
        }

        val combinedScaleX = pose.scaleX * hudScale
        val combinedScaleY = pose.scaleY * hudScale

        // Paper Diorama cutout drop-silhouette pass
        if (style == AnimationStyle.PAPER_DIORAMA) {
            canvas.save()
            val cutM = Matrix().apply {
                postScale(combinedScaleX, combinedScaleY, rootX, rootY)
                postRotate(pose.bodyTiltDeg, rootX, rootY - 120f * scaleUnit * hudScale)
            }
            canvas.concat(cutM)
            drawLayeredPuppet(
                canvas = canvas,
                rootX = rootX,
                rootY = rootY,
                u = scaleUnit,
                role = role,
                pose = pose,
                style = style,
                globalTimeSec = globalTimeSec,
                silhouetteOutlineOnly = true
            )
            canvas.restore()
        } else if (style == AnimationStyle.VIRAL_2D_EXPLAINER) {
            // Soft character drop shadow pass
            canvas.save()
            val shadowM = Matrix().apply {
                postTranslate(12f * scaleUnit * hudScale, 16f * scaleUnit * hudScale)
                postScale(combinedScaleX, combinedScaleY, rootX, rootY)
                postRotate(pose.bodyTiltDeg, rootX, rootY - 120f * scaleUnit * hudScale)
            }
            canvas.concat(shadowM)
            drawLayeredPuppet(
                canvas = canvas,
                rootX = rootX,
                rootY = rootY,
                u = scaleUnit,
                role = role,
                pose = pose,
                style = style,
                globalTimeSec = globalTimeSec,
                silhouetteOutlineOnly = true,
                softDropShadowOnly = true
            )
            canvas.restore()
        }

        // Main Character Hierarchical Pass
        canvas.save()
        val mainM = Matrix().apply {
            postScale(combinedScaleX, combinedScaleY, rootX, rootY)
            postRotate(pose.bodyTiltDeg, rootX, rootY - 120f * scaleUnit * hudScale)
        }
        canvas.concat(mainM)
        drawLayeredPuppet(
            canvas = canvas,
            rootX = rootX,
            rootY = rootY,
            u = scaleUnit,
            role = role,
            pose = pose,
            style = style,
            globalTimeSec = globalTimeSec,
            silhouetteOutlineOnly = false
        )
        canvas.restore()

        if (style == AnimationStyle.CHIBI_STORYTIME ||
            style == AnimationStyle.VIRAL_2D_EXPLAINER ||
            pose.emoteType != 0 ||
            pose.expression != ActorExpression.HAPPY_TALK
        ) {
            drawAnimeEmotes(
                canvas = canvas,
                cx = rootX,
                topY = rootY - 470f * scaleUnit * hudScale,
                u = scaleUnit * hudScale,
                pose = pose,
                globalTimeSec = globalTimeSec,
                style = style
            )
        }

        canvas.restore()
    }

    private fun drawCustomSpriteRig(
        canvas: Canvas,
        sprite: Bitmap,
        rootX: Float,
        rootY: Float,
        scaleUnit: Float,
        hudScale: Float,
        pose: RigPose,
        isSpeaking: Boolean,
        speechPulse: Float,
        globalTimeSec: Float
    ) {
        val targetH = 560f * scaleUnit * hudScale
        val aspect = sprite.width.toFloat() / sprite.height.coerceAtLeast(1).toFloat()
        val drawW = (targetH * aspect).coerceAtMost(720f * scaleUnit * hudScale)
        val drawH = drawW / aspect.coerceAtLeast(0.1f)

        val bounceSquash = if (isSpeaking) speechPulse * 0.045f else 0f
        val sx = pose.scaleX * (1f + bounceSquash) * (if (pose.facingDir < 0f) -1f else 1f)
        val sy = pose.scaleY * (1f - bounceSquash * 0.85f)
        val tilt = pose.bodyTiltDeg + pose.headTiltDeg * 0.35f

        val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.save()
        val m = Matrix().apply {
            postScale(sx, sy, rootX, rootY)
            postRotate(tilt, rootX, rootY - drawH * 0.35f)
        }
        canvas.concat(m)
        val dst = RectF(
            rootX - drawW * 0.5f,
            rootY - drawH,
            rootX + drawW * 0.5f,
            rootY
        )
        canvas.drawBitmap(sprite, null, dst, spritePaint)
        if (pose.handProp != HandProp.NONE) {
            val handOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0E1018.toInt()
                setStyle(Paint.Style.STROKE)
                strokeWidth = 4f * scaleUnit * hudScale
            }
            canvas.save()
            canvas.translate(rootX + drawW * 0.34f, rootY - drawH * 0.44f)
            canvas.rotate(pose.rightHandTiltDeg)
            PuppetPropRenderer.drawHandProp(
                canvas = canvas,
                handX = 0f,
                handY = 0f,
                u = scaleUnit * hudScale,
                prop = pose.handProp,
                globalTimeSec = globalTimeSec,
                silhouetteOnly = false,
                outlinePaint = handOutline,
                facingDir = 1f
            )
            canvas.restore()
        }
        canvas.restore()
    }

    private fun computeRigPose(
        resolvedPose: CharacterPose,
        action: ActorAction,
        style: AnimationStyle,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float,
        isSpeaking: Boolean,
        speechPulse: Float,
        shotFraming: ShotFraming,
        handProp: HandProp,
        spring: SpringFollowThrough,
        sceneId: Int,
        facingOverride: Float,
        expression: ActorExpression = ActorExpression.HAPPY_TALK,
        headgear: ActorHeadgear = ActorHeadgear.NONE,
        gender: CharacterGender = CharacterGender.MALE,
        age: CharacterAge = CharacterAge.MIDDLE_AGED,
        outfit: CharacterOutfit = CharacterOutfit.CASUAL_TSHIRT,
        outfitColorHex: String = "",
        hairColorHex: String = "",
        mouthShape: MouthShape = MouthShape.CLOSED_M
    ): RigPose {
        // Continuous 0.35 Hz chest/shoulder breathing + 2.6s eye blinks + stressed-syllable head micro-nods
        val breathCycle = sin(globalTimeSec * 2f * PI.toFloat() * BREATHING_FREQ_HZ)
        val shoulderBreathDeg = breathCycle * 2.4f
        val chestBreathScaleX = breathCycle * 0.014f
        val chestBreathScaleY = -breathCycle * 0.011f
        val chestBreathLiftY = -breathCycle * 2.6f
        val syllableNodDeg = if (isSpeaking) {
            sin(speechPulse * PI.toFloat()) * 3.8f * (if (sin(globalTimeSec * 7.5f) >= 0f) 1f else -0.6f)
        } else {
            breathCycle * 1.4f
        }

        val blinkPhase = globalTimeSec % EYE_BLINK_PERIOD_SEC
        val baseEyeScaleY = if (blinkPhase > EYE_BLINK_PERIOD_SEC - 0.16f) {
            val b = (blinkPhase - (EYE_BLINK_PERIOD_SEC - 0.16f)) / 0.16f
            abs(1f - 2f * b).coerceIn(0.08f, 1f)
        } else 1f
        val eyeScaleY = when (expression) {
            ActorExpression.SHOCKED_WIDE_EYES -> 1.42f
            ActorExpression.PANIC_SWEAT_FIRE -> 1.30f
            ActorExpression.SLEEPING_ZZZ -> 0.14f
            ActorExpression.TONGUE_STING -> 0.22f
            else -> baseEyeScaleY
        }

        val talkMouth = if (isSpeaking) (0.28f + 0.72f * speechPulse).coerceIn(0.1f, 1f) else 0.06f
        val exprMouth = when (expression) {
            ActorExpression.SHOCKED_WIDE_EYES -> maxOf(talkMouth, 0.86f)
            ActorExpression.PANIC_SWEAT_FIRE -> maxOf(talkMouth, 0.92f)
            ActorExpression.ANGRY_EVIL -> maxOf(talkMouth, 0.65f)
            ActorExpression.TONGUE_STING -> maxOf(talkMouth, 0.74f)
            ActorExpression.SLEEPING_ZZZ -> 0.20f + 0.12f * sin(globalTimeSec * 2.2f)
            ActorExpression.HAPPY_TALK -> talkMouth
        }
        val talkBounce = if (isSpeaking) abs(sin(globalTimeSec * 12f)) * 10f else 0f
        val limbSquash = if (isSpeaking) sin(globalTimeSec * 12f) * 0.05f else breathCycle * 0.022f
        val springHead = spring.headLagDeg * 0.65f
        val springHair = spring.hairHatTiltDeg
        val springArm = spring.trailingArmLagDeg

        fun applyExpressionOverrides(base: RigPose): RigPose {
            val styled = base.copy(
                offsetY = base.offsetY + chestBreathLiftY,
                scaleX = base.scaleX + chestBreathScaleX,
                scaleY = base.scaleY + chestBreathScaleY,
                headTiltDeg = base.headTiltDeg + syllableNodDeg,
                leftShoulderDeg = base.leftShoulderDeg + shoulderBreathDeg,
                rightShoulderDeg = base.rightShoulderDeg - shoulderBreathDeg,
                mouthShape = mouthShape,
                gender = gender,
                age = age,
                outfit = outfit,
                outfitColorHex = outfitColorHex,
                hairColorHex = hairColorHex
            )
            return when (expression) {
                ActorExpression.HAPPY_TALK -> styled.copy(expression = expression, headgear = headgear)
                ActorExpression.ANGRY_EVIL -> styled.copy(
                    eyebrowSlantDeg = -22f,
                    eyebrowOffset = 2f,
                    mouthOpen = maxOf(styled.mouthOpen, exprMouth),
                    emoteType = 2,
                    expression = expression,
                    headgear = headgear
                )
                ActorExpression.PANIC_SWEAT_FIRE -> styled.copy(
                    eyeScaleY = maxOf(styled.eyeScaleY, 1.30f),
                    pupilScale = 0.50f,
                    mouthOpen = maxOf(styled.mouthOpen, exprMouth),
                    eyebrowOffset = -14f,
                    eyebrowSlantDeg = -14f,
                    emoteType = 1,
                    expression = expression,
                    headgear = headgear
                )
                ActorExpression.SHOCKED_WIDE_EYES -> styled.copy(
                    eyeScaleY = 1.42f,
                    pupilScale = 0.45f,
                    mouthOpen = maxOf(styled.mouthOpen, exprMouth),
                    eyebrowOffset = -18f,
                    eyebrowSlantDeg = -10f,
                    emoteType = 2,
                    expression = expression,
                    headgear = headgear
                )
                ActorExpression.TONGUE_STING -> styled.copy(
                    eyeScaleY = 0.22f,
                    mouthOpen = maxOf(styled.mouthOpen, exprMouth),
                    eyebrowOffset = 4f,
                    eyebrowSlantDeg = -16f,
                    emoteType = 1,
                    expression = expression,
                    headgear = headgear
                )
                ActorExpression.SLEEPING_ZZZ -> styled.copy(
                    eyeScaleY = 0.14f,
                    headTiltDeg = styled.headTiltDeg + 10f,
                    mouthOpen = exprMouth,
                    eyebrowOffset = -4f,
                    eyebrowSlantDeg = 6f,
                    expression = expression,
                    headgear = headgear
                )
            }
        }

        val rawPose = when (resolvedPose) {
            CharacterPose.WALK_ACROSS_LEFT_TO_RIGHT -> {
                val progress = PuppetSpringSolver.sampleWalkAcrossProgress(sceneElapsedSec, sceneDurationSec)
                val isWalking = progress < 0.992f
                val stridePhase = sceneElapsedSec * 10.5f
                val s = if (isWalking) sin(stridePhase) else 0f
                val c = if (isWalking) cos(stridePhase) else 1f
                val bob = if (isWalking) abs(s) * 16f else 0f
                val walkX = PuppetSpringSolver.computeWalkOffsetX(sceneElapsedSec, sceneDurationSec)

                // True gait knee flexion: trailing swing leg bends backward (+46 deg) during recovery
                val leftHip = -s * 34f
                val leftKnee = if (s > 0f) (s * 48f) else (-s * 12f)
                val rightHip = s * 34f
                val rightKnee = if (-s > 0f) (-s * 48f) else (s * 12f)

                val rightShoulder = if (handProp != HandProp.NONE) {
                    -58f - s * 14f + springArm * 0.35f
                } else {
                    -s * 36f + springArm * 0.45f
                }
                val rightElbow = if (handProp != HandProp.NONE) -48f else (-34f - abs(c) * 14f)

                RigPose(
                    offsetX = walkX,
                    offsetY = -bob - talkBounce * 0.4f + spring.verticalBounceLagPx * 0.35f,
                    bodyTiltDeg = (if (isWalking) 6.5f + s * 2.5f else 0f),
                    headTiltDeg = (if (isWalking) -s * 2.4f else 0f) + springHead,
                    hairHatTiltDeg = springHair,
                    perspectiveProfile = 1.0f, // True side profile
                    facingDir = 1f,
                    leftShoulderDeg = s * 38f - springArm * 0.4f,
                    leftElbowDeg = -32f - abs(s) * 16f,
                    rightShoulderDeg = rightShoulder,
                    rightElbowDeg = rightElbow,
                    rightHandTiltDeg = springArm * 0.3f,
                    leftHipDeg = leftHip,
                    leftKneeDeg = leftKnee,
                    rightHipDeg = rightHip,
                    rightKneeDeg = rightKnee,
                    limbStretchY = 1f + abs(s) * 0.05f,
                    limbSquashX = 1f - abs(s) * 0.03f,
                    shadowScale = (1f - (bob / 16f) * 0.16f).coerceIn(0.75f, 1f),
                    eyeScaleY = eyeScaleY,
                    mouthOpen = talkMouth,
                    eyebrowOffset = if (isSpeaking) -speechPulse * 4f else -1f,
                    eyebrowSlantDeg = 4f,
                    handProp = handProp
                )
            }

            CharacterPose.SIT_SLUMPED -> {
                val seatType = when (sceneId % 3) {
                    0 -> PuppetPropRenderer.SeatType.CRATE
                    1 -> PuppetPropRenderer.SeatType.ROCK
                    else -> PuppetPropRenderer.SeatType.STOOL
                }
                val breath = sin(globalTimeSec * 2.2f)
                val isCrying = action == ActorAction.CRY_SHAKE
                RigPose(
                    offsetX = if (isCrying) sin(globalTimeSec * 32f) * 4f else 0f,
                    offsetY = 64f + breath * 3.5f + spring.verticalBounceLagPx * 0.25f,
                    scaleX = 1.03f + breath * 0.015f,
                    scaleY = 0.97f - breath * 0.015f,
                    bodyTiltDeg = 17f + breath * 1.4f,
                    headTiltDeg = 11f + breath * 2f + springHead * 0.5f,
                    hairHatTiltDeg = springHair * 0.6f,
                    perspectiveProfile = 0.36f,
                    facingDir = facingOverride,
                    leftShoulderDeg = 22f,
                    leftElbowDeg = -70f + breath * 4f,
                    rightShoulderDeg = if (handProp != HandProp.NONE) -34f else -16f,
                    rightElbowDeg = if (handProp != HandProp.NONE) -42f else -74f + breath * 4f,
                    leftHipDeg = -84f,
                    leftKneeDeg = 88f,
                    rightHipDeg = -78f,
                    rightKneeDeg = 84f,
                    shadowScale = 1.08f,
                    eyeScaleY = if (isCrying) 0.24f else eyeScaleY,
                    mouthOpen = if (isCrying) maxOf(talkMouth, 0.4f) else talkMouth,
                    eyebrowOffset = if (isCrying) 7f else 4f,
                    eyebrowSlantDeg = -14f,
                    seatType = seatType,
                    handProp = handProp,
                    tearDropLevel = if (isCrying) Easing.pingPongWave(globalTimeSec, 1.8f) else 0f,
                    emoteType = 1
                )
            }

            CharacterPose.SHOCK_AIRBORNE_RECOIL -> {
                val tJump = (sceneElapsedSec / 0.88f).coerceIn(0f, 1f)
                val air = sin(tJump * PI.toFloat())
                val recoilY = -air * 195f
                val landed = tJump > 0.92f
                val landSettle = if (landed) sin(((sceneElapsedSec - 0.81f) / 0.22f).coerceIn(0f, 1f) * PI.toFloat()) else 0f
                val sx = when {
                    air > 0.2f -> 1f - air * 0.14f
                    landed -> 1f + landSettle * 0.14f
                    else -> 1.08f
                }
                val sy = when {
                    air > 0.2f -> 1f + air * 0.16f
                    landed -> 1f - landSettle * 0.14f
                    else -> 0.92f
                }
                RigPose(
                    offsetX = -18f * air,
                    offsetY = recoilY + spring.verticalBounceLagPx * 0.55f,
                    scaleX = sx,
                    scaleY = sy,
                    bodyTiltDeg = -10f * air + landSettle * 4f,
                    headTiltDeg = -8f * air + springHead * 0.9f,
                    hairHatTiltDeg = springHair * 1.15f,
                    perspectiveProfile = 0.28f,
                    facingDir = facingOverride,
                    leftShoulderDeg = 138f - landSettle * 25f + springArm * 0.4f,
                    leftElbowDeg = -34f,
                    rightShoulderDeg = -138f + landSettle * 25f - springArm * 0.4f,
                    rightElbowDeg = 34f,
                    leftHipDeg = -32f * air,
                    leftKneeDeg = 72f * air,
                    rightHipDeg = 28f * air,
                    rightKneeDeg = 64f * air,
                    shadowScale = (1f - air * 0.55f).coerceIn(0.42f, 1f),
                    eyeScaleY = 1.32f,
                    pupilScale = 0.65f,
                    mouthOpen = maxOf(talkMouth, 0.82f),
                    eyebrowOffset = -16f,
                    eyebrowSlantDeg = -13f,
                    handProp = handProp,
                    emoteType = 2
                )
            }

            CharacterPose.POINT_AND_GESTURE -> {
                val jab = sin(globalTimeSec * 8.2f) * 11f
                val activeProp = if (handProp != HandProp.NONE) handProp else HandProp.POINTER_STICK
                RigPose(
                    offsetX = 12f,
                    offsetY = -talkBounce * 0.5f + spring.verticalBounceLagPx * 0.3f,
                    bodyTiltDeg = -3.8f + sin(globalTimeSec * 4.2f) * 2f,
                    headTiltDeg = 4f + springHead * 0.65f,
                    hairHatTiltDeg = springHair * 0.8f,
                    perspectiveProfile = 0.31f, // 18-deg 3/4 perspective
                    facingDir = facingOverride,
                    leftShoulderDeg = 26f + sin(globalTimeSec * 4f) * 6f,
                    leftElbowDeg = -52f,
                    rightShoulderDeg = -86f + jab + springArm * 0.3f,
                    rightElbowDeg = -22f + jab * 0.8f,
                    rightHandTiltDeg = jab * 0.5f,
                    leftHipDeg = 6f,
                    rightHipDeg = -6f,
                    limbStretchY = 1f + limbSquash * 0.7f,
                    limbSquashX = 1f - limbSquash * 0.4f,
                    eyeScaleY = eyeScaleY,
                    mouthOpen = talkMouth,
                    eyebrowOffset = -6f,
                    eyebrowSlantDeg = 10f,
                    handProp = activeProp
                )
            }

            CharacterPose.CELEBRATE_JUMP -> {
                val period = 0.95f
                val local = (sceneElapsedSec % period) / period
                val arc = sin(local * PI.toFloat())
                val airY = -arc * 155f
                val isTakeoffOrLand = arc < 0.18f
                val sx = if (isTakeoffOrLand) 1.09f else 0.92f
                val sy = if (isTakeoffOrLand) 0.91f else 1.09f
                RigPose(
                    offsetY = airY + spring.verticalBounceLagPx * 0.5f,
                    scaleX = sx,
                    scaleY = sy,
                    bodyTiltDeg = sin(globalTimeSec * 6.5f) * 4f,
                    headTiltDeg = -4f + springHead * 0.85f,
                    hairHatTiltDeg = springHair * 1.1f,
                    perspectiveProfile = 0.25f,
                    facingDir = facingOverride,
                    leftShoulderDeg = 146f + sin(globalTimeSec * 9f) * 10f + springArm * 0.35f,
                    leftElbowDeg = -24f,
                    rightShoulderDeg = -146f - sin(globalTimeSec * 9f) * 10f - springArm * 0.35f,
                    rightElbowDeg = 24f,
                    leftHipDeg = -22f * arc,
                    leftKneeDeg = 58f * arc,
                    rightHipDeg = 18f * arc,
                    rightKneeDeg = 52f * arc,
                    shadowScale = (1f - arc * 0.48f).coerceIn(0.48f, 1f),
                    eyeScaleY = eyeScaleY,
                    mouthOpen = maxOf(talkMouth, 0.55f),
                    eyebrowOffset = -9f,
                    eyebrowSlantDeg = -7f,
                    showThumbsUp = handProp == HandProp.NONE,
                    handProp = handProp,
                    emoteType = 3
                )
            }

            CharacterPose.THREE_QUARTER_EXPLAIN,
            CharacterPose.TWO_CHARACTER_CONVERSATION,
            CharacterPose.AUTO -> {
                val squash = if (style == AnimationStyle.CHIBI_STORYTIME) {
                    0.055f * sin(globalTimeSec * 10f)
                } else 0.025f * sin(globalTimeSec * 8f)
                val gestureFreq = if (isSpeaking) 6.8f else 3.2f
                val gestureAmp = if (isSpeaking) 24f else 10f
                val rShoulder = if (handProp != HandProp.NONE) -62f - sin(globalTimeSec * gestureFreq) * 14f
                else -48f - sin(globalTimeSec * gestureFreq) * gestureAmp
                RigPose(
                    offsetX = sin(globalTimeSec * 1.9f) * 6f,
                    offsetY = -talkBounce - abs(sin(globalTimeSec * 4.2f)) * 4f + spring.verticalBounceLagPx * 0.3f,
                    scaleX = 1f + squash,
                    scaleY = 1f - squash,
                    bodyTiltDeg = sin(globalTimeSec * 3.4f) * 2.6f,
                    headTiltDeg = sin(globalTimeSec * 4.8f) * 2.8f + springHead * 0.7f,
                    hairHatTiltDeg = springHair * 0.8f,
                    perspectiveProfile = 0.31f, // 18-deg 3/4 perspective
                    facingDir = facingOverride,
                    leftShoulderDeg = 26f + sin(globalTimeSec * 5.2f) * 14f,
                    leftElbowDeg = -46f + sin(globalTimeSec * 6.4f) * 16f,
                    rightShoulderDeg = rShoulder + springArm * 0.35f,
                    rightElbowDeg = -42f + sin(globalTimeSec * (gestureFreq + 1.2f)) * (if (isSpeaking) 22f else 8f),
                    rightHandTiltDeg = sin(globalTimeSec * gestureFreq) * 10f,
                    limbStretchY = 1f + limbSquash,
                    limbSquashX = 1f - limbSquash * 0.5f,
                    eyeScaleY = eyeScaleY,
                    mouthOpen = talkMouth,
                    eyebrowOffset = if (isSpeaking) -speechPulse * 4.5f else -1f,
                    eyebrowSlantDeg = if (isSpeaking) 5f else 0f,
                    showThumbsUp = action == ActorAction.THUMBS_UP && handProp == HandProp.NONE,
                    handProp = handProp,
                    emoteType = if (style == AnimationStyle.CHIBI_STORYTIME) 3 else 0
                )
            }
        }
        return applyExpressionOverrides(rawPose)
    }

    private fun paletteForRole(
        role: ActorRole,
        style: AnimationStyle,
        gender: CharacterGender = CharacterGender.MALE,
        age: CharacterAge = CharacterAge.MIDDLE_AGED,
        outfit: CharacterOutfit = CharacterOutfit.CASUAL_TSHIRT,
        outfitColorHex: String = "",
        hairColorHex: String = ""
    ): RolePalette {
        val base = when (role) {
            ActorRole.RICH_MAN -> RolePalette(
                skin = 0xFFF5CBA7.toInt(),
                skinShadow = 0xFFD4976A.toInt(),
                primary = 0xFF1F2937.toInt(),
                primaryShadow = 0xFF0F172A.toInt(),
                secondary = 0xFFFBBF24.toInt(),
                accent = 0xFFEF4444.toInt(),
                hair = 0xFF374151.toInt(),
                iris = 0xFF1E3A8A.toInt()
            )
            ActorRole.POOR_MAN -> RolePalette(
                skin = 0xFFE5B28A.toInt(),
                skinShadow = 0xFFBF855D.toInt(),
                primary = 0xFF8D6E63.toInt(),
                primaryShadow = 0xFF5D4037.toInt(),
                secondary = 0xFFB49A8E.toInt(),
                accent = 0xFF64748B.toInt(),
                hair = 0xFF292524.toInt(),
                iris = 0xFF3F2E26.toInt()
            )
            ActorRole.CHICKEN_MENTOR -> RolePalette(
                skin = 0xFFFFF8E1.toInt(),
                skinShadow = 0xFFF7DC88.toInt(),
                primary = 0xFFFFFFFF.toInt(),
                primaryShadow = 0xFFD9E2EC.toInt(),
                secondary = 0xFFF59E0B.toInt(),
                accent = 0xFFEF4444.toInt(),
                hair = 0xFFEF4444.toInt(),
                iris = 0xFF0F172A.toInt()
            )
            ActorRole.HERO -> RolePalette(
                skin = 0xFFF3C39B.toInt(),
                skinShadow = 0xFFCF9164.toInt(),
                primary = 0xFF2563EB.toInt(),
                primaryShadow = 0xFF1E40AF.toInt(),
                secondary = 0xFFFF5E36.toInt(),
                accent = 0xFFFFD166.toInt(),
                hair = 0xFF1E1B4B.toInt(),
                iris = 0xFF1D4ED8.toInt()
            )
            ActorRole.NARRATOR -> RolePalette(
                skin = 0xFFF0C09A.toInt(),
                skinShadow = 0xFFCC9167.toInt(),
                primary = 0xFF0F766E.toInt(),
                primaryShadow = 0xFF115E59.toInt(),
                secondary = 0xFFFDE68A.toInt(),
                accent = 0xFF38BDF8.toInt(),
                hair = 0xFF1F2937.toInt(),
                iris = 0xFF0F766E.toInt()
            )
        }

        val outfitDefaultColor = when (outfit) {
            CharacterOutfit.MEDICAL_SCRUBS -> 0xFF0EA5E9.toInt()
            CharacterOutfit.MARKET_APRON -> 0xFFF97316.toInt()
            CharacterOutfit.STUDENT_UNIFORM -> 0xFF1D4ED8.toInt()
            CharacterOutfit.WORK_UNIFORM -> 0xFFD97706.toInt()
            CharacterOutfit.HOODIE -> 0xFF7C3AED.toInt()
            CharacterOutfit.OFFICE_SUIT -> if (role == ActorRole.RICH_MAN) base.primary else 0xFF1E293B.toInt()
            CharacterOutfit.CASUAL_TSHIRT -> if (gender == CharacterGender.FEMALE && role == ActorRole.HERO) 0xFFEC4899.toInt() else base.primary
        }

        val customOutfit = if (role != ActorRole.CHICKEN_MENTOR) parseHexColorOrNull(outfitColorHex) else null
        val chosenPrimary = customOutfit ?: if (role != ActorRole.CHICKEN_MENTOR) outfitDefaultColor else base.primary
        var styled = if (role != ActorRole.CHICKEN_MENTOR) {
            base.copy(
                primary = chosenPrimary,
                primaryShadow = darkenColor(chosenPrimary, 0.76f)
            )
        } else {
            base
        }

        val customHair = if (role != ActorRole.CHICKEN_MENTOR) parseHexColorOrNull(hairColorHex) else null
        if (customHair != null) {
            styled = styled.copy(hair = customHair)
        } else if (age == CharacterAge.ELDERLY && role != ActorRole.CHICKEN_MENTOR) {
            styled = styled.copy(hair = 0xFFD1D5DB.toInt())
        }

        if (style == AnimationStyle.DARK_SUSPENSE) {
            return styled.copy(
                skin = darkenColor(styled.skin, 0.72f),
                skinShadow = darkenColor(styled.skinShadow, 0.65f),
                primary = darkenColor(styled.primary, 0.68f),
                primaryShadow = darkenColor(styled.primaryShadow, 0.60f),
                secondary = darkenColor(styled.secondary, 0.85f)
            )
        }
        return styled
    }

    private fun drawLayeredPuppet(
        canvas: Canvas,
        rootX: Float,
        rootY: Float,
        u: Float,
        role: ActorRole,
        pose: RigPose,
        style: AnimationStyle,
        globalTimeSec: Float,
        silhouetteOutlineOnly: Boolean,
        softDropShadowOnly: Boolean = false
    ) {
        val pal = paletteForRole(role, style, pose.gender, pose.age, pose.outfit, pose.outfitColorHex, pose.hairColorHex)
        val isChibi = style == AnimationStyle.CHIBI_STORYTIME
        val isBorderless = (style == AnimationStyle.FLAT_VECTOR || style == AnimationStyle.TECH_EXPLAINER) && !silhouetteOutlineOnly
        val isSideProfile = pose.perspectiveProfile >= 0.85f

        val strokeW = when {
            softDropShadowOnly -> 0f
            silhouetteOutlineOnly -> 20f * u
            isBorderless -> 0f
            style == AnimationStyle.VIRAL_2D_EXPLAINER -> 6.0f * u
            style == AnimationStyle.DARK_SUSPENSE -> 5.5f * u
            else -> 4.5f * u
        }

        fun makeFillPaint(c1: Int, c2: Int? = null, xLeft: Float = rootX - 90f * u, yTop: Float = 0f, xRight: Float = rootX + 90f * u, yBot: Float = 100f): Paint {
            if (softDropShadowOnly) {
                return Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x44000000
                    setStyle(Paint.Style.FILL)
                    maskFilter = BlurMaskFilter((14f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                }
            }
            if (silhouetteOutlineOnly) {
                return Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    setStyle(Paint.Style.FILL_AND_STROKE)
                    strokeWidth = strokeW
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }
            }
            return Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (c2 != null) {
                    val highlight = lightenColor(c1, 1.14f)
                    shader = LinearGradient(
                        xLeft, yTop, xRight, yBot,
                        intArrayOf(highlight, c1, c2),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                } else {
                    color = c1
                }
                setStyle(Paint.Style.FILL)
            }
        }

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (silhouetteOutlineOnly) Color.BLACK else 0xFF0E1018.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = strokeW
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (style == AnimationStyle.TECH_EXPLAINER) 0xAA00E5FF.toInt() else 0xAAFF4D4D.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 5f * u
            strokeCap = Paint.Cap.ROUND
        }

        val isChild = pose.age == CharacterAge.CHILD && role != ActorRole.CHICKEN_MENTOR
        val headScale = (if (isChibi) 1.32f else 1.0f) * (if (isChild) 1.08f else 1.0f)
        val bodyH = (if (isChibi) 145f * u else 185f * u) * (if (isChild) 0.84f else 1.0f)
        val legH = (if (isChibi) 88f * u else 130f * u) * (if (isChild) 0.82f else 1.0f)
        val hipY = rootY - legH
        val shoulderY = hipY - bodyH + 30f * u
        val neckY = hipY - bodyH

        val torsoWidthFactor = if (isSideProfile) 0.68f else (1f - pose.perspectiveProfile * 0.16f)
        val legSpacing = when {
            isSideProfile -> 10f * u
            role == ActorRole.CHICKEN_MENTOR -> 34f * u * torsoWidthFactor
            else -> 38f * u * torsoWidthFactor
        }
        val legThick = (if (role == ActorRole.CHICKEN_MENTOR) 18f * u else 33f * u) * pose.limbSquashX
        val thighLen = legH * 0.54f * pose.limbStretchY
        val shinLen = legH * 0.46f * pose.limbStretchY
        val shoeColor = if (role == ActorRole.CHICKEN_MENTOR) pal.secondary else 0xFF111827.toInt()

        // 1. ARTICULATED LEGS (Hip -> Thigh -> Knee Matrix -> Shin -> Directional Shoe)
        drawArticulatedLeg(
            canvas = canvas,
            hipX = rootX - legSpacing * pose.facingDir,
            hipY = hipY,
            thighLen = thighLen,
            shinLen = shinLen,
            thickness = legThick,
            hipAngleDeg = pose.leftHipDeg * pose.facingDir,
            kneeAngleDeg = pose.leftKneeDeg * pose.facingDir,
            facingDir = pose.facingDir,
            fillPaint = makeFillPaint(
                if (role == ActorRole.CHICKEN_MENTOR) pal.secondary else pal.primary,
                if (role == ActorRole.CHICKEN_MENTOR) darkenColor(pal.secondary, 0.78f) else pal.primaryShadow,
                rootX - 60f * u, hipY, rootX, rootY
            ),
            outlinePaint = outlinePaint,
            shoeColor = shoeColor,
            silhouetteOutlineOnly = silhouetteOutlineOnly,
            u = u
        )
        drawArticulatedLeg(
            canvas = canvas,
            hipX = rootX + legSpacing * pose.facingDir,
            hipY = hipY,
            thighLen = thighLen,
            shinLen = shinLen,
            thickness = legThick,
            hipAngleDeg = pose.rightHipDeg * pose.facingDir,
            kneeAngleDeg = pose.rightKneeDeg * pose.facingDir,
            facingDir = pose.facingDir,
            fillPaint = makeFillPaint(
                if (role == ActorRole.CHICKEN_MENTOR) pal.secondary else pal.primary,
                if (role == ActorRole.CHICKEN_MENTOR) darkenColor(pal.secondary, 0.78f) else pal.primaryShadow,
                rootX, hipY, rootX + 60f * u, rootY
            ),
            outlinePaint = outlinePaint,
            shoeColor = shoeColor,
            silhouetteOutlineOnly = silhouetteOutlineOnly,
            u = u
        )

        // 2. FAR ARM (behind torso, anchored tightly inside torso shoulder socket)
        val shoulderOffset = (if (role == ActorRole.CHICKEN_MENTOR) 64f * u else 54f * u) * torsoWidthFactor
        val totalArmLen = (if (isChibi) 115f * u else 142f * u) * pose.limbStretchY
        val upperArmLen = totalArmLen * 0.52f
        val forearmLen = totalArmLen * 0.48f
        val armThick = (if (role == ActorRole.CHICKEN_MENTOR) 32f * u else 27f * u) * pose.limbSquashX

        val backShoulderX = if (isSideProfile) rootX - 6f * u * pose.facingDir else rootX - shoulderOffset * pose.facingDir
        drawArticulatedArm(
            canvas = canvas,
            shoulderX = backShoulderX,
            shoulderY = shoulderY,
            upperLen = upperArmLen,
            forearmLen = forearmLen,
            thickness = armThick,
            shoulderDeg = pose.leftShoulderDeg * pose.facingDir,
            elbowDeg = pose.leftElbowDeg * pose.facingDir,
            handTiltDeg = 0f,
            sleevePaint = makeFillPaint(pal.primary, pal.primaryShadow, rootX - 90f * u, shoulderY, rootX, hipY),
            handPaint = makeFillPaint(
                if (role == ActorRole.CHICKEN_MENTOR) pal.primary else pal.skin,
                if (role == ActorRole.CHICKEN_MENTOR) pal.primaryShadow else pal.skinShadow
            ),
            cuffColor = pal.secondary,
            outlinePaint = outlinePaint,
            silhouetteOutlineOnly = silhouetteOutlineOnly,
            strokeW = strokeW,
            isWing = role == ActorRole.CHICKEN_MENTOR,
            isThumbsUp = false,
            handProp = HandProp.NONE,
            globalTimeSec = globalTimeSec,
            facingDir = pose.facingDir,
            u = u
        )

        // 3. TORSO (with 18-deg 3/4 perspective or true side profile)
        val halfTorsoW = when (role) {
            ActorRole.CHICKEN_MENTOR -> 88f * u
            ActorRole.POOR_MAN -> 82f * u
            else -> 74f * u
        } * torsoWidthFactor
        val torsoTopOffset = if (role == ActorRole.POOR_MAN) 12f * u else 0f
        val torsoBotPad = when (role) {
            ActorRole.CHICKEN_MENTOR -> 18f * u
            ActorRole.POOR_MAN -> 26f * u
            else -> 15f * u
        }
        val torsoRect = RectF(rootX - halfTorsoW, neckY + torsoTopOffset, rootX + halfTorsoW, hipY + torsoBotPad)
        val torsoPaint = makeFillPaint(pal.primary, pal.primaryShadow, torsoRect.left, torsoRect.top, torsoRect.right, torsoRect.bottom)
        canvas.drawRoundRect(torsoRect, 38f * u, 38f * u, torsoPaint)
        if (!silhouetteOutlineOnly) {
            canvas.drawOval(
                RectF(rootX - halfTorsoW * 0.6f, torsoRect.top - 10f * u, rootX + halfTorsoW * 0.6f, torsoRect.top + 32f * u),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
            )
            val detailShift = pose.facingDir * pose.perspectiveProfile * (if (isSideProfile) 2.1f else 1.5f)
            drawRoleTorsoDetails(canvas, torsoRect, rootX, u, role, pal, detailShift, pose.outfit)
            if (style == AnimationStyle.DARK_SUSPENSE || style == AnimationStyle.TECH_EXPLAINER) {
                canvas.drawArc(torsoRect, -65f, 110f, false, rimPaint)
            }
        }
        if (strokeW > 0f) canvas.drawRoundRect(torsoRect, 38f * u, 38f * u, outlinePaint)

        // 4. HEAD GROUP (Hierarchical Matrix pivot at neckY + spring lag + spring hair/hat)
        canvas.save()
        val headPivotY = neckY
        val headMatrix = Matrix().apply {
            postRotate(pose.headTiltDeg, rootX, headPivotY)
            postScale(headScale, headScale, rootX, headPivotY)
        }
        canvas.concat(headMatrix)

        val headRadiusX = (if (role == ActorRole.CHICKEN_MENTOR) 78f * u else 84f * u) *
            (if (isSideProfile) 0.90f else 1f)
        val headRadiusY = if (role == ActorRole.CHICKEN_MENTOR) 76f * u else 88f * u
        val headCenterY = neckY - headRadiusY + 12f * u
        val headRect = RectF(
            rootX - headRadiusX,
            headCenterY - headRadiusY,
            rootX + headRadiusX,
            headCenterY + headRadiusY
        )

        // Side-profile nose silhouette behind outline if human in side profile
        if (isSideProfile && role != ActorRole.CHICKEN_MENTOR) {
            val noseX = rootX + pose.facingDir * (headRadiusX + 12f * u)
            val nosePath = Path().apply {
                moveTo(rootX + pose.facingDir * (headRadiusX * 0.82f), headCenterY - 10f * u)
                lineTo(noseX, headCenterY + 6f * u)
                lineTo(rootX + pose.facingDir * (headRadiusX * 0.85f), headCenterY + 16f * u)
                close()
            }
            val nosePaint = makeFillPaint(pal.skin, pal.skinShadow, headRect.left, headRect.top, headRect.right, headRect.bottom)
            canvas.drawPath(nosePath, nosePaint)
            if (strokeW > 0f) canvas.drawPath(nosePath, outlinePaint)
        }

        // Chicken comb (rotated with hairHatTiltDeg spring!)
        if (role == ActorRole.CHICKEN_MENTOR) {
            canvas.save()
            canvas.rotate(pose.hairHatTiltDeg, rootX, headRect.top + 8f * u)
            drawChickenComb(
                canvas,
                rootX + pose.facingDir * pose.perspectiveProfile * 10f * u,
                headRect.top,
                u,
                pal.accent,
                outlinePaint,
                silhouetteOutlineOnly,
                strokeW
            )
            canvas.restore()
        }

        val headPaint = makeFillPaint(
            if (role == ActorRole.CHICKEN_MENTOR) pal.primary else pal.skin,
            if (role == ActorRole.CHICKEN_MENTOR) pal.primaryShadow else pal.skinShadow,
            headRect.left, headRect.top, headRect.right, headRect.bottom
        )
        canvas.drawOval(headRect, headPaint)

        if (!silhouetteOutlineOnly) {
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2AFFFFFF }
            canvas.drawOval(
                RectF(headRect.left + 12f * u, headRect.top + 16f * u, headRect.left + 58f * u, headRect.bottom - 26f * u),
                highlightPaint
            )

            // Side ear on back edge during side/3-4 profile
            if (role != ActorRole.CHICKEN_MENTOR && pose.perspectiveProfile > 0.2f) {
                val earX = rootX - pose.facingDir * (headRadiusX * (if (isSideProfile) 0.45f else 0.92f))
                val earRect = RectF(earX - 11f * u, headCenterY - 8f * u, earX + 11f * u, headCenterY + 18f * u)
                canvas.drawOval(earRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.skinShadow })
                if (strokeW > 0f) canvas.drawOval(earRect, outlinePaint)
            }

            drawFacialFeatures(
                canvas = canvas,
                cx = rootX + pose.facingDir * pose.perspectiveProfile * 22f * u,
                cy = headCenterY,
                u = u,
                role = role,
                pal = pal,
                pose = pose,
                style = style,
                outlinePaint = outlinePaint,
                isSideProfile = isSideProfile,
                globalTimeSec = globalTimeSec
            )

            canvas.save()
            canvas.rotate(pose.hairHatTiltDeg, rootX, headRect.top + 12f * u)
            drawRoleHeadgear(
                canvas = canvas,
                rootX = rootX,
                headRect = headRect,
                u = u,
                role = role,
                pal = pal,
                turnShift = pose.facingDir * pose.perspectiveProfile,
                silhouetteOnly = false,
                headgear = pose.headgear,
                outlinePaint = outlinePaint,
                strokeW = strokeW,
                gender = pose.gender,
                age = pose.age,
                outfit = pose.outfit
            )
            canvas.restore()

            if (style == AnimationStyle.DARK_SUSPENSE || style == AnimationStyle.TECH_EXPLAINER) {
                canvas.drawArc(headRect, -55f, 100f, false, rimPaint)
            }
        } else {
            val useRichTopHat = role == ActorRole.RICH_MAN && pose.outfit == CharacterOutfit.OFFICE_SUIT && pose.gender == CharacterGender.MALE
            if (useRichTopHat || pose.headgear != ActorHeadgear.NONE || pose.gender == CharacterGender.FEMALE) {
                canvas.save()
                canvas.rotate(pose.hairHatTiltDeg, rootX, headRect.top + 12f * u)
                drawRoleHeadgear(
                    canvas = canvas,
                    rootX = rootX,
                    headRect = headRect,
                    u = u,
                    role = role,
                    pal = pal,
                    turnShift = pose.facingDir * pose.perspectiveProfile,
                    silhouetteOnly = true,
                    headgear = pose.headgear,
                    outlinePaint = outlinePaint,
                    strokeW = strokeW,
                    gender = pose.gender,
                    age = pose.age,
                    outfit = pose.outfit
                )
                canvas.restore()
            }
        }
        if (strokeW > 0f) canvas.drawOval(headRect, outlinePaint)
        canvas.restore()

        // 5. NEAR / RIGHT ARM (in front of torso + articulated elbow + hands-on Prop inside right-hand pivot Matrix)
        val frontShoulderX = if (isSideProfile) rootX + 8f * u * pose.facingDir else rootX + shoulderOffset * pose.facingDir
        drawArticulatedArm(
            canvas = canvas,
            shoulderX = frontShoulderX,
            shoulderY = shoulderY,
            upperLen = upperArmLen,
            forearmLen = forearmLen,
            thickness = armThick,
            shoulderDeg = pose.rightShoulderDeg * pose.facingDir,
            elbowDeg = pose.rightElbowDeg * pose.facingDir,
            handTiltDeg = pose.rightHandTiltDeg * pose.facingDir,
            sleevePaint = makeFillPaint(pal.primary, pal.primaryShadow, rootX, shoulderY, rootX + 90f * u, hipY),
            handPaint = makeFillPaint(
                if (role == ActorRole.CHICKEN_MENTOR) pal.primary else pal.skin,
                if (role == ActorRole.CHICKEN_MENTOR) pal.primaryShadow else pal.skinShadow
            ),
            cuffColor = pal.secondary,
            outlinePaint = outlinePaint,
            silhouetteOutlineOnly = silhouetteOutlineOnly,
            strokeW = strokeW,
            isWing = role == ActorRole.CHICKEN_MENTOR,
            isThumbsUp = pose.showThumbsUp,
            handProp = pose.handProp,
            globalTimeSec = globalTimeSec,
            facingDir = pose.facingDir,
            u = u
        )
    }

    private fun drawArticulatedLeg(
        canvas: Canvas,
        hipX: Float,
        hipY: Float,
        thighLen: Float,
        shinLen: Float,
        thickness: Float,
        hipAngleDeg: Float,
        kneeAngleDeg: Float,
        facingDir: Float,
        fillPaint: Paint,
        outlinePaint: Paint,
        shoeColor: Int,
        silhouetteOutlineOnly: Boolean,
        u: Float
    ) {
        canvas.save()
        val hipMatrix = Matrix().apply {
            postRotate(hipAngleDeg, hipX, hipY)
        }
        canvas.concat(hipMatrix)

        // Upper Thigh segment
        val thighRect = RectF(hipX - thickness * 0.5f, hipY, hipX + thickness * 0.5f, hipY + thighLen + 6f * u)
        canvas.drawRoundRect(thighRect, thickness * 0.45f, thickness * 0.45f, fillPaint)
        if (outlinePaint.strokeWidth > 0f) canvas.drawRoundRect(thighRect, thickness * 0.45f, thickness * 0.45f, outlinePaint)

        // Knee Pivot Matrix
        canvas.save()
        val kneeY = hipY + thighLen
        val kneeMatrix = Matrix().apply {
            postTranslate(hipX, kneeY)
            postRotate(kneeAngleDeg)
        }
        canvas.concat(kneeMatrix)

        val shinRect = RectF(-thickness * 0.46f, -4f * u, thickness * 0.46f, shinLen)
        canvas.drawRoundRect(shinRect, thickness * 0.42f, thickness * 0.42f, fillPaint)
        if (outlinePaint.strokeWidth > 0f) canvas.drawRoundRect(shinRect, thickness * 0.42f, thickness * 0.42f, outlinePaint)

        // Directional shoe anchored at ankle
        val ankleY = shinLen
        val shoeLeft = if (facingDir >= 0f) -thickness * 0.55f else -thickness * 1.05f
        val shoeRight = if (facingDir >= 0f) thickness * 1.05f else thickness * 0.55f
        val shoeRect = RectF(
            shoeLeft,
            ankleY - thickness * 0.36f,
            shoeRight,
            ankleY + thickness * 0.36f
        )
        val sp = if (silhouetteOutlineOnly) fillPaint else Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shoeColor }
        canvas.drawRoundRect(shoeRect, thickness * 0.34f, thickness * 0.34f, sp)
        if (!silhouetteOutlineOnly) {
            canvas.drawLine(
                shoeRect.left + thickness * 0.16f,
                shoeRect.bottom - thickness * 0.12f,
                shoeRect.right - thickness * 0.16f,
                shoeRect.bottom - thickness * 0.12f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x55FFFFFF
                    strokeWidth = (thickness * 0.10f).coerceAtLeast(2f)
                    strokeCap = Paint.Cap.ROUND
                }
            )
        }
        if (outlinePaint.strokeWidth > 0f) canvas.drawRoundRect(shoeRect, thickness * 0.34f, thickness * 0.34f, outlinePaint)

        canvas.restore() // knee
        canvas.restore() // hip
    }

    private fun drawArticulatedArm(
        canvas: Canvas,
        shoulderX: Float,
        shoulderY: Float,
        upperLen: Float,
        forearmLen: Float,
        thickness: Float,
        shoulderDeg: Float,
        elbowDeg: Float,
        handTiltDeg: Float,
        sleevePaint: Paint,
        handPaint: Paint,
        cuffColor: Int,
        outlinePaint: Paint,
        silhouetteOutlineOnly: Boolean,
        strokeW: Float,
        isWing: Boolean,
        isThumbsUp: Boolean,
        handProp: HandProp,
        globalTimeSec: Float,
        facingDir: Float,
        u: Float
    ) {
        canvas.save()
        val shoulderMatrix = Matrix().apply {
            postRotate(shoulderDeg, shoulderX, shoulderY)
        }
        canvas.concat(shoulderMatrix)

        if (isWing) {
            val wingLen = upperLen + forearmLen * 0.85f
            val wingRect = RectF(
                shoulderX - thickness * 0.58f,
                shoulderY - thickness * 0.44f,
                shoulderX + thickness * 0.58f,
                shoulderY + wingLen * 0.62f
            )
            canvas.drawOval(wingRect, sleevePaint)
            if (strokeW > 0f) canvas.drawOval(wingRect, outlinePaint)

            // Wing tip pivot at elbow/forearm
            canvas.save()
            val wingTipMatrix = Matrix().apply {
                postTranslate(shoulderX, shoulderY + upperLen * 0.8f)
                postRotate(elbowDeg * 0.65f)
            }
            canvas.concat(wingTipMatrix)
            val tipRect = RectF(-thickness * 0.45f, -thickness * 0.40f, thickness * 0.45f, forearmLen * 0.82f)
            canvas.drawOval(tipRect, sleevePaint)
            if (strokeW > 0f) canvas.drawOval(tipRect, outlinePaint)

            if (handProp != HandProp.NONE) {
                canvas.save()
                val handM = Matrix().apply {
                    postTranslate(0f, forearmLen * 0.72f)
                    postRotate(handTiltDeg)
                }
                canvas.concat(handM)
                PuppetPropRenderer.drawHandProp(
                    canvas = canvas,
                    handX = 0f,
                    handY = 0f,
                    u = u,
                    prop = handProp,
                    globalTimeSec = globalTimeSec,
                    silhouetteOnly = silhouetteOutlineOnly,
                    outlinePaint = outlinePaint,
                    facingDir = facingDir
                )
                canvas.restore()
            }
            canvas.restore()
            canvas.restore()
            return
        }

        // Upper Arm: rounded cap centered on (shoulderX, shoulderY) so rotation never detaches the shoulder
        val upperRect = RectF(
            shoulderX - thickness * 0.5f,
            shoulderY - thickness * 0.48f,
            shoulderX + thickness * 0.5f,
            shoulderY + upperLen + thickness * 0.42f
        )
        canvas.drawRoundRect(upperRect, thickness * 0.45f, thickness * 0.45f, sleevePaint)
        if (strokeW > 0f) canvas.drawRoundRect(upperRect, thickness * 0.45f, thickness * 0.45f, outlinePaint)

        // Elbow Pivot Matrix
        canvas.save()
        val elbowMatrix = Matrix().apply {
            postTranslate(shoulderX, shoulderY + upperLen)
            postRotate(elbowDeg)
        }
        canvas.concat(elbowMatrix)

        val forearmRect = RectF(-thickness * 0.46f, -thickness * 0.42f, thickness * 0.46f, forearmLen)
        canvas.drawRoundRect(forearmRect, thickness * 0.42f, thickness * 0.42f, sleevePaint)
        if (!silhouetteOutlineOnly) {
            val cuffRect = RectF(-thickness * 0.52f, forearmLen - 16f * u, thickness * 0.52f, forearmLen - 5f * u)
            canvas.drawRoundRect(cuffRect, 5f * u, 5f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cuffColor })
        }
        if (strokeW > 0f) canvas.drawRoundRect(forearmRect, thickness * 0.42f, thickness * 0.42f, outlinePaint)

        // Hand Pivot Matrix
        canvas.save()
        val handMatrix = Matrix().apply {
            postTranslate(0f, forearmLen)
            postRotate(handTiltDeg)
        }
        canvas.concat(handMatrix)

        // Draw Prop first inside hand pivot so fingers/palm overlap handle cleanly
        if (handProp != HandProp.NONE) {
            PuppetPropRenderer.drawHandProp(
                canvas = canvas,
                handX = 0f,
                handY = 0f,
                u = u,
                prop = handProp,
                globalTimeSec = globalTimeSec,
                silhouetteOnly = silhouetteOutlineOnly,
                outlinePaint = outlinePaint,
                facingDir = facingDir
            )
        }

        canvas.drawCircle(0f, 0f, thickness * 0.56f, handPaint)
        if (strokeW > 0f) canvas.drawCircle(0f, 0f, thickness * 0.56f, outlinePaint)

        if (isThumbsUp && !silhouetteOutlineOnly) {
            val thumbRect = RectF(-9f * u, -34f * u, 9f * u, 0f)
            canvas.drawRoundRect(thumbRect, 8f * u, 8f * u, handPaint)
            if (strokeW > 0f) canvas.drawRoundRect(thumbRect, 8f * u, 8f * u, outlinePaint)
        }

        canvas.restore() // hand
        canvas.restore() // elbow
        canvas.restore() // shoulder
    }

    private fun drawRoleTorsoDetails(
        canvas: Canvas,
        torso: RectF,
        rootX: Float,
        u: Float,
        role: ActorRole,
        pal: RolePalette,
        turnShift: Float,
        outfit: CharacterOutfit = CharacterOutfit.CASUAL_TSHIRT
    ) {
        val cx = (rootX + turnShift * 11f * u).coerceIn(torso.left + 26f * u, torso.right - 26f * u)
        val stitchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44000000
            setStyle(Paint.Style.STROKE)
            strokeWidth = 3f * u
        }

        if (role == ActorRole.CHICKEN_MENTOR) {
            val bib = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.primaryShadow }
            canvas.drawArc(
                RectF(cx - 46f * u, torso.top + 12f * u, cx + 46f * u, torso.top + 92f * u),
                0f, 180f, true, bib
            )
            val bowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.accent }
            val leftWing = Path().apply {
                moveTo(cx, torso.top + 28f * u)
                lineTo(cx - 20f * u, torso.top + 14f * u)
                lineTo(cx - 20f * u, torso.top + 42f * u)
                close()
            }
            val rightWing = Path().apply {
                moveTo(cx, torso.top + 28f * u)
                lineTo(cx + 20f * u, torso.top + 14f * u)
                lineTo(cx + 20f * u, torso.top + 42f * u)
                close()
            }
            canvas.drawPath(leftWing, bowPaint)
            canvas.drawPath(rightWing, bowPaint)
            canvas.drawCircle(cx, torso.top + 28f * u, 7f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.secondary })
            return
        }

        when (outfit) {
            CharacterOutfit.MARKET_APRON -> {
                // Crisp cream bib apron + straps + front market coin pocket
                val apronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFEF3C7.toInt() }
                val strapW = 14f * u
                canvas.drawRect(cx - 34f * u, torso.top + 4f * u, cx - 34f * u + strapW, torso.top + 46f * u, apronPaint)
                canvas.drawRect(cx + 34f * u - strapW, torso.top + 4f * u, cx + 34f * u, torso.top + 46f * u, apronPaint)
                val apronRect = RectF(torso.left + 16f * u, torso.top + 42f * u, torso.right - 16f * u, torso.bottom - 4f * u)
                canvas.drawRoundRect(apronRect, 14f * u, 14f * u, apronPaint)
                canvas.drawRoundRect(apronRect, 14f * u, 14f * u, stitchPaint)
                val pocket = RectF(cx - 30f * u, torso.top + 86f * u, cx + 30f * u, torso.top + 130f * u)
                canvas.drawRoundRect(pocket, 8f * u, 8f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFDE68A.toInt() })
                canvas.drawRoundRect(pocket, 8f * u, 8f * u, stitchPaint)
            }
            CharacterOutfit.OFFICE_SUIT -> {
                val shirt = Path().apply {
                    moveTo(cx - 26f * u, torso.top)
                    lineTo(cx, torso.top + 88f * u)
                    lineTo(cx + 26f * u, torso.top)
                    close()
                }
                canvas.drawPath(shirt, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                val lapelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lightenColor(pal.primary, 1.25f) }
                val leftLapel = Path().apply {
                    moveTo(cx - 26f * u, torso.top)
                    lineTo(cx - 40f * u, torso.top + 48f * u)
                    lineTo(cx - 8f * u, torso.top + 82f * u)
                    close()
                }
                val rightLapel = Path().apply {
                    moveTo(cx + 26f * u, torso.top)
                    lineTo(cx + 40f * u, torso.top + 48f * u)
                    lineTo(cx + 8f * u, torso.top + 82f * u)
                    close()
                }
                canvas.drawPath(leftLapel, lapelPaint)
                canvas.drawPath(rightLapel, lapelPaint)
                val tie = Path().apply {
                    moveTo(cx - 9f * u, torso.top + 10f * u)
                    lineTo(cx + 9f * u, torso.top + 10f * u)
                    lineTo(cx + 13f * u, torso.top + 92f * u)
                    lineTo(cx, torso.top + 114f * u)
                    lineTo(cx - 13f * u, torso.top + 92f * u)
                    close()
                }
                canvas.drawPath(tie, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.secondary })
                canvas.drawRoundRect(
                    RectF(cx - 10f * u, torso.top + 4f * u, cx + 10f * u, torso.top + 20f * u),
                    5f * u, 5f * u,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkenColor(pal.secondary, 0.85f) }
                )
            }
            CharacterOutfit.STUDENT_UNIFORM -> {
                // Crisp white collar + school necktie/ribbon + crest badge
                val collar = Path().apply {
                    moveTo(cx - 34f * u, torso.top + 2f * u)
                    lineTo(cx, torso.top + 44f * u)
                    lineTo(cx + 34f * u, torso.top + 2f * u)
                    lineTo(cx + 42f * u, torso.top + 24f * u)
                    lineTo(cx, torso.top + 56f * u)
                    lineTo(cx - 42f * u, torso.top + 24f * u)
                    close()
                }
                canvas.drawPath(collar, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                val rib = Path().apply {
                    moveTo(cx - 10f * u, torso.top + 28f * u)
                    lineTo(cx + 10f * u, torso.top + 28f * u)
                    lineTo(cx + 14f * u, torso.top + 84f * u)
                    lineTo(cx, torso.top + 96f * u)
                    lineTo(cx - 14f * u, torso.top + 84f * u)
                    close()
                }
                canvas.drawPath(rib, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEF4444.toInt() })
                val crestX = (cx + 28f * u).coerceAtMost(torso.right - 22f * u)
                canvas.drawCircle(crestX, torso.top + 66f * u, 13f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFACC15.toInt() })
            }
            CharacterOutfit.MEDICAL_SCRUBS -> {
                // Scrub V-neck + stethoscope curve + hospital ID tag
                val vNeck = Path().apply {
                    moveTo(cx - 24f * u, torso.top)
                    lineTo(cx, torso.top + 38f * u)
                    lineTo(cx + 24f * u, torso.top)
                    close()
                }
                canvas.drawPath(vNeck, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.skin })
                val stethPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF1E293B.toInt()
                    setStyle(Paint.Style.STROKE)
                    strokeWidth = 6.5f * u
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawArc(RectF(cx - 32f * u, torso.top - 4f * u, cx + 32f * u, torso.top + 64f * u), 10f, 160f, false, stethPaint)
                canvas.drawCircle(cx + 24f * u, torso.top + 62f * u, 10f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCBD5E1.toInt() })
                val idRect = RectF(cx - 42f * u, torso.top + 56f * u, cx - 14f * u, torso.top + 88f * u)
                canvas.drawRoundRect(idRect, 4f * u, 4f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                canvas.drawRect(idRect.left + 4f * u, idRect.top + 6f * u, idRect.right - 4f * u, idRect.top + 14f * u,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEF4444.toInt() })
            }
            CharacterOutfit.HOODIE -> {
                // Hoodie neck trim + hanging drawstrings + kangaroo front pouch
                canvas.drawRoundRect(
                    RectF(torso.left + 12f * u, torso.top - 4f * u, torso.right - 12f * u, torso.top + 28f * u),
                    14f * u, 14f * u,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.primaryShadow }
                )
                val cordP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF8FAFC.toInt(); strokeWidth = 5f * u; strokeCap = Paint.Cap.ROUND }
                canvas.drawLine(cx - 14f * u, torso.top + 24f * u, cx - 16f * u, torso.top + 74f * u, cordP)
                canvas.drawLine(cx + 14f * u, torso.top + 24f * u, cx + 16f * u, torso.top + 74f * u, cordP)
                val pouch = RectF(torso.left + 20f * u, torso.bottom - 68f * u, torso.right - 20f * u, torso.bottom - 14f * u)
                canvas.drawRoundRect(pouch, 14f * u, 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.primaryShadow })
                canvas.drawRoundRect(pouch, 14f * u, 14f * u, stitchPaint)
            }
            CharacterOutfit.WORK_UNIFORM -> {
                // Hi-vis reflective safety stripes + utility chest pocket
                val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFEF08A.toInt() }
                canvas.drawRect(torso.left + 4f * u, torso.top + 48f * u, torso.right - 4f * u, torso.top + 64f * u, stripePaint)
                canvas.drawRect(torso.left + 4f * u, torso.bottom - 44f * u, torso.right - 4f * u, torso.bottom - 28f * u, stripePaint)
                val px = (cx + 16f * u).coerceAtMost(torso.right - 36f * u)
                val pocket = RectF(px, torso.top + 74f * u, px + 26f * u, torso.top + 104f * u)
                canvas.drawRoundRect(pocket, 5f * u, 5f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.primaryShadow })
                canvas.drawRoundRect(pocket, 5f * u, 5f * u, stitchPaint)
            }
            CharacterOutfit.CASUAL_TSHIRT -> {
                // Clean crew-neck collar + subtle chest stripe/pocket
                canvas.drawArc(
                    RectF(cx - 26f * u, torso.top - 12f * u, cx + 26f * u, torso.top + 26f * u),
                    0f, 180f, true,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.skin }
                )
                canvas.drawArc(
                    RectF(cx - 30f * u, torso.top - 12f * u, cx + 30f * u, torso.top + 32f * u),
                    10f, 160f, false,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lightenColor(pal.primary, 1.25f); style = Paint.Style.STROKE; strokeWidth = 6f * u }
                )
                val px = (cx + 18f * u).coerceAtMost(torso.right - 34f * u)
                val pocket = RectF(px, torso.top + 54f * u, px + 24f * u, torso.top + 80f * u)
                canvas.drawRoundRect(pocket, 5f * u, 5f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.primaryShadow })
                canvas.drawRoundRect(pocket, 5f * u, 5f * u, stitchPaint)
            }
        }
    }

    private fun drawChickenComb(
        canvas: Canvas,
        cx: Float,
        headTop: Float,
        u: Float,
        color: Int,
        outlinePaint: Paint,
        silhouetteOnly: Boolean,
        strokeW: Float
    ) {
        val combPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (silhouetteOnly) Color.BLACK else color
            if (silhouetteOnly) {
                setStyle(Paint.Style.FILL_AND_STROKE)
                strokeWidth = strokeW
            }
        }
        for ((dx, r) in listOf(-20f to 20f, 4f to 26f, 28f to 18f)) {
            canvas.drawCircle(cx + dx * u, headTop - 4f * u, r * u, combPaint)
            if (strokeW > 0f && !silhouetteOnly) {
                canvas.drawCircle(cx + dx * u, headTop - 4f * u, r * u, outlinePaint)
            }
        }
    }

    private fun drawFacialFeatures(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        u: Float,
        role: ActorRole,
        pal: RolePalette,
        pose: RigPose,
        style: AnimationStyle,
        outlinePaint: Paint,
        isSideProfile: Boolean,
        globalTimeSec: Float
    ) {
        val isChibi = style == AnimationStyle.CHIBI_STORYTIME
        val eyeXOffset = if (isChibi) 31f * u else 27f * u
        val eyeY = cy - 10f * u
        val eyeR = if (isChibi) 21f * u else 15.5f * u

        if (isChibi || pose.showThumbsUp || pose.expression == ActorExpression.PANIC_SWEAT_FIRE) {
            val blush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (pose.expression == ActorExpression.PANIC_SWEAT_FIRE) 0x77EF4444 else 0x66FF6B8B
            }
            if (isSideProfile) {
                canvas.drawOval(RectF(cx - 12f * u, eyeY + 18f * u, cx + 18f * u, eyeY + 34f * u), blush)
            } else {
                canvas.drawOval(RectF(cx - 54f * u, eyeY + 18f * u, cx - 26f * u, eyeY + 34f * u), blush)
                canvas.drawOval(RectF(cx + 26f * u, eyeY + 18f * u, cx + 54f * u, eyeY + 34f * u), blush)
            }
        }

        val scleraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val upperShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x220F172A }
        val irisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.iris }
        val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF090D16.toInt() }
        val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val eyeRim = Paint(outlinePaint).apply { strokeWidth = maxOf(3f * u, outlinePaint.strokeWidth * 0.65f) }
        val browPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.hair
            setStyle(Paint.Style.STROKE)
            strokeWidth = 8f * u
            strokeCap = Paint.Cap.ROUND
        }

        val sides = if (isSideProfile) listOf(pose.facingDir) else listOf(-1f, 1f)
        val turnVal = pose.facingDir * pose.perspectiveProfile

        for (side in sides) {
            val turnScale = when {
                isSideProfile -> 0.88f
                (side < 0f && turnVal < -0.1f) || (side > 0f && turnVal > 0.1f) ->
                    1f - abs(turnVal) * 0.22f
                else -> 1f
            }
            val ex = if (isSideProfile) cx + pose.facingDir * 14f * u else cx + side * eyeXOffset

            when (pose.expression) {
                ActorExpression.TONGUE_STING -> {
                    // Wincing >< eyes
                    val wincePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF0E1018.toInt()
                        setStyle(Paint.Style.STROKE)
                        strokeWidth = 6f * u
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    val dir = if (isSideProfile) -pose.facingDir else -side
                    val chevron = Path().apply {
                        moveTo(ex - dir * 13f * u, eyeY - 11f * u)
                        lineTo(ex + dir * 10f * u, eyeY)
                        lineTo(ex - dir * 13f * u, eyeY + 11f * u)
                    }
                    canvas.drawPath(chevron, wincePaint)
                }
                ActorExpression.SLEEPING_ZZZ -> {
                    // Closed U-shaped sleeping eyelids
                    val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF0E1018.toInt()
                        setStyle(Paint.Style.STROKE)
                        strokeWidth = 5.5f * u
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawArc(
                        RectF(ex - eyeR * 0.95f, eyeY - eyeR * 0.35f, ex + eyeR * 0.95f, eyeY + eyeR * 0.65f),
                        12f, 156f, false, lidPaint
                    )
                }
                else -> {
                    canvas.save()
                    canvas.scale(turnScale, pose.eyeScaleY, ex, eyeY)
                    canvas.drawCircle(ex, eyeY, eyeR, scleraPaint)
                    canvas.drawArc(
                        RectF(ex - eyeR, eyeY - eyeR, ex + eyeR, eyeY + eyeR),
                        195f, 150f, true, upperShadowPaint
                    )
                    canvas.drawCircle(ex, eyeY, eyeR, eyeRim)

                    val gazeX = ex + pose.facingDir * 3.5f * u
                    canvas.drawCircle(gazeX, eyeY + 0.5f * u, eyeR * 0.64f * pose.pupilScale, irisPaint)
                    canvas.drawCircle(gazeX, eyeY + 0.5f * u, eyeR * 0.38f * pose.pupilScale, pupilPaint)
                    canvas.drawCircle(gazeX - eyeR * 0.26f, eyeY - eyeR * 0.26f, eyeR * 0.23f, shinePaint)
                    canvas.drawCircle(gazeX + eyeR * 0.20f, eyeY + eyeR * 0.22f, eyeR * 0.11f, shinePaint)
                    canvas.restore()

                    if (pose.eyeScaleY < 0.65f) {
                        val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF0E1018.toInt()
                            setStyle(Paint.Style.STROKE)
                            strokeWidth = 4.5f * u
                            strokeCap = Paint.Cap.ROUND
                        }
                        canvas.drawArc(
                            RectF(ex - eyeR * 0.9f, eyeY - eyeR * 0.4f, ex + eyeR * 0.9f, eyeY + eyeR * 0.5f),
                            15f, 150f, false, lidPaint
                        )
                    }
                    if (pose.gender == CharacterGender.FEMALE && role != ActorRole.CHICKEN_MENTOR && pose.eyeScaleY >= 0.65f) {
                        val lashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF0E1018.toInt()
                            setStyle(Paint.Style.STROKE)
                            strokeWidth = 3.5f * u
                            strokeCap = Paint.Cap.ROUND
                        }
                        val lashSide = if (isSideProfile) pose.facingDir else side
                        val lx0 = ex + lashSide * eyeR * 0.72f
                        val ly0 = eyeY - eyeR * 0.55f
                        canvas.drawLine(lx0, ly0, lx0 + lashSide * 8f * u, ly0 - 7f * u, lashPaint)
                        canvas.drawLine(lx0 + lashSide * 2f * u, ly0 + 4f * u, lx0 + lashSide * 10f * u, ly0 - 1f * u, lashPaint)
                    }
                }
            }

            // Eyebrow
            val browY = eyeY - eyeR - 11f * u + pose.eyebrowOffset * u
            canvas.save()
            val slant = if (pose.expression == ActorExpression.ANGRY_EVIL) -24f else pose.eyebrowSlantDeg
            canvas.rotate(side * slant, ex, browY)
            val browPath = Path().apply {
                moveTo(ex - 18f * u, browY + 3f * u)
                quadTo(ex, browY - 5f * u, ex + 18f * u, browY + 3f * u)
            }
            canvas.drawPath(browPath, browPaint)
            canvas.restore()
        }

        if (!isSideProfile && role != ActorRole.CHICKEN_MENTOR) {
            val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pal.skinShadow
                setStyle(Paint.Style.STROKE)
                strokeWidth = 4f * u
                strokeCap = Paint.Cap.ROUND
            }
            val nx = cx + turnVal * 5f * u
            canvas.drawArc(
                RectF(nx - 6f * u, eyeY + 10f * u, nx + 9f * u, cy + 16f * u),
                -60f, 125f, false, nosePaint
            )
        }

        if (pose.age == CharacterAge.ELDERLY && role != ActorRole.CHICKEN_MENTOR) {
            val wrinklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pal.skinShadow
                setStyle(Paint.Style.STROKE)
                strokeWidth = 3f * u
                strokeCap = Paint.Cap.ROUND
            }
            // Forehead & cheek smile lines
            canvas.drawArc(RectF(cx - 26f * u, eyeY - 38f * u, cx + 26f * u, eyeY - 26f * u), 200f, 140f, false, wrinklePaint)
            if (!isSideProfile) {
                canvas.drawArc(RectF(cx - 46f * u, cy + 14f * u, cx - 28f * u, cy + 36f * u), 100f, 130f, false, wrinklePaint)
                canvas.drawArc(RectF(cx + 28f * u, cy + 14f * u, cx + 46f * u, cy + 36f * u), -50f, 130f, false, wrinklePaint)
            }
            if (role != ActorRole.RICH_MAN) {
                val wireSpec = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF64748B.toInt()
                    setStyle(Paint.Style.STROKE)
                    strokeWidth = 3.8f * u
                }
                if (isSideProfile) {
                    canvas.drawCircle(cx + pose.facingDir * 14f * u, eyeY, eyeR + 4.5f * u, wireSpec)
                } else {
                    canvas.drawCircle(cx - eyeXOffset, eyeY, eyeR + 4.5f * u, wireSpec)
                    canvas.drawCircle(cx + eyeXOffset, eyeY, eyeR + 4.5f * u, wireSpec)
                    canvas.drawLine(cx - eyeXOffset + eyeR + 4.5f * u, eyeY, cx + eyeXOffset - eyeR - 4.5f * u, eyeY, wireSpec)
                }
            }
        }

        if (pose.tearDropLevel > 0f) {
            val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC38BDF8.toInt() }
            val ty = eyeY + 14f * u + pose.tearDropLevel * 32f * u
            if (isSideProfile) {
                canvas.drawCircle(cx + pose.facingDir * 14f * u, ty, 6f * u, tearPaint)
            } else {
                canvas.drawCircle(cx - eyeXOffset, ty, 6f * u, tearPaint)
                canvas.drawCircle(cx + eyeXOffset, ty, 6f * u, tearPaint)
            }
        }

        if (role == ActorRole.RICH_MAN && pose.outfit == CharacterOutfit.OFFICE_SUIT && !isSideProfile) {
            val goldRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pal.secondary
                setStyle(Paint.Style.STROKE)
                strokeWidth = 4.5f * u
            }
            canvas.drawCircle(cx + eyeXOffset, eyeY, eyeR + 5f * u, goldRim)
            canvas.drawLine(cx + eyeXOffset + eyeR + 4f * u, eyeY + 4f * u, cx + eyeXOffset + 22f * u, cy + 44f * u, goldRim)
        }
        if (role == ActorRole.CHICKEN_MENTOR) {
            val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1F2937.toInt()
                setStyle(Paint.Style.STROKE)
                strokeWidth = 4f * u
            }
            if (isSideProfile) {
                canvas.drawCircle(cx + pose.facingDir * 14f * u, eyeY, eyeR + 5f * u, specPaint)
            } else {
                canvas.drawCircle(cx - eyeXOffset, eyeY, eyeR + 5f * u, specPaint)
                canvas.drawCircle(cx + eyeXOffset, eyeY, eyeR + 5f * u, specPaint)
                canvas.drawLine(cx - eyeXOffset + eyeR + 5f * u, eyeY, cx + eyeXOffset - eyeR - 5f * u, eyeY, specPaint)
            }
        }

        val mouthY = cy + 32f * u
        if (role == ActorRole.CHICKEN_MENTOR) {
            val beakColor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.secondary }
            val shapeGap = when (pose.mouthShape) {
                MouthShape.OPEN_WIDE -> maxOf(pose.mouthOpen * 24f, 16f)
                MouthShape.ROUND_O -> maxOf(pose.mouthOpen * 18f, 12f)
                MouthShape.SMILE_TEETH -> maxOf(pose.mouthOpen * 12f, 7f)
                MouthShape.CLOSED_M -> 0f
            }
            val openGap = shapeGap * u
            val beakShift = if (isSideProfile) pose.facingDir * 44f * u else 0f
            val bx = cx + beakShift
            val upperBeak = Path().apply {
                moveTo(bx - 22f * u, mouthY)
                lineTo(bx + 22f * u, mouthY)
                lineTo(bx + (if (isSideProfile) pose.facingDir * 24f * u else 0f), mouthY - 16f * u)
                close()
            }
            val lowerBeak = Path().apply {
                moveTo(bx - 18f * u, mouthY + openGap)
                lineTo(bx + 18f * u, mouthY + openGap)
                lineTo(bx + (if (isSideProfile) pose.facingDir * 18f * u else 0f), mouthY + openGap + 15f * u)
                close()
            }
            if (openGap > 3f * u) {
                canvas.drawRect(
                    bx - 16f * u, mouthY - 2f * u, bx + 16f * u, mouthY + openGap + 2f * u,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7F1D1D.toInt() }
                )
            }
            canvas.drawPath(upperBeak, beakColor)
            canvas.drawPath(lowerBeak, beakColor)
        } else {
            val mx = if (isSideProfile) cx + pose.facingDir * 26f * u else cx
            val mouthH = (5f + pose.mouthOpen * 28f) * u
            val mouthW = (if (isSideProfile) 14f + pose.mouthOpen * 6f else 25f + pose.mouthOpen * 9f) * u
            val mouthRect = RectF(mx - mouthW, mouthY - mouthH * 0.35f, mx + mouthW, mouthY + mouthH * 0.65f)
            val mouthFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7F1D1D.toInt() }

            when (pose.expression) {
                ActorExpression.ANGRY_EVIL -> {
                    canvas.drawRoundRect(mouthRect, 10f * u, 10f * u, mouthFill)
                    // Jagged sharp zig-zag teeth
                    val teethPath = Path().apply {
                        moveTo(mouthRect.left + 3f * u, mouthRect.top + 2f * u)
                        val steps = 6
                        val stepW = (mouthRect.width() - 6f * u) / steps
                        for (i in 0..steps) {
                            val tx = mouthRect.left + 3f * u + i * stepW
                            val ty = if (i % 2 == 0) mouthRect.top + 2f * u else mouthRect.top + mouthRect.height() * 0.45f
                            lineTo(tx, ty)
                        }
                        lineTo(mouthRect.right - 3f * u, mouthRect.top + 2f * u)
                        close()
                    }
                    canvas.drawPath(teethPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                    if (outlinePaint.strokeWidth > 0f) {
                        canvas.drawRoundRect(mouthRect, 10f * u, 10f * u, Paint(outlinePaint).apply { strokeWidth = 3f * u })
                    }
                }
                ActorExpression.PANIC_SWEAT_FIRE -> {
                    // Wavy scream mouth + animated shooting mouth flames
                    canvas.drawOval(mouthRect, mouthFill)
                    val outerFlame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5722.toInt() }
                    val innerFlame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD600.toInt() }
                    for (i in -1..1) {
                        val flicker = 1f + 0.25f * sin(globalTimeSec * 18f + i * 1.3f)
                        val fx = mx + i * 14f * u
                        val fPath = Path().apply {
                            moveTo(fx - 12f * u, mouthRect.centerY())
                            quadTo(fx + pose.facingDir * 14f * u, mouthRect.top - 22f * u * flicker, fx + pose.facingDir * 8f * u, mouthRect.bottom + 26f * u * flicker)
                            quadTo(fx - 8f * u, mouthRect.bottom + 8f * u, fx - 12f * u, mouthRect.centerY())
                            close()
                        }
                        canvas.drawPath(fPath, outerFlame)
                        canvas.drawCircle(fx + pose.facingDir * 4f * u, mouthRect.centerY() + 4f * u, 8f * u * flicker, innerFlame)
                    }
                }
                ActorExpression.TONGUE_STING -> {
                    canvas.drawOval(mouthRect, mouthFill)
                    // Swollen stinging cartoon tongue protruding with comic lightning zigzags
                    val tongueRect = RectF(mx - 16f * u, mouthRect.centerY() - 2f * u, mx + 22f * u, mouthRect.bottom + 24f * u)
                    canvas.drawOval(tongueRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFB7185.toInt() })
                    if (outlinePaint.strokeWidth > 0f) {
                        canvas.drawOval(tongueRect, Paint(outlinePaint).apply { strokeWidth = 3f * u })
                    }
                    val stingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFD600.toInt()
                        setStyle(Paint.Style.STROKE)
                        strokeWidth = 4f * u
                        strokeCap = Paint.Cap.ROUND
                    }
                    val zig = Path().apply {
                        moveTo(tongueRect.right + 4f * u, tongueRect.centerY() - 12f * u)
                        lineTo(tongueRect.right + 16f * u, tongueRect.centerY() - 2f * u)
                        lineTo(tongueRect.right + 8f * u, tongueRect.centerY() + 4f * u)
                        lineTo(tongueRect.right + 20f * u, tongueRect.centerY() + 14f * u)
                    }
                    canvas.drawPath(zig, stingPaint)
                }
                ActorExpression.SLEEPING_ZZZ -> {
                    // Small breathing O mouth
                    val sleepMouth = RectF(mx - 11f * u, mouthY - 6f * u, mx + 11f * u, mouthY + 12f * u * (0.7f + pose.mouthOpen))
                    canvas.drawOval(sleepMouth, mouthFill)
                }
                ActorExpression.SHOCKED_WIDE_EYES -> {
                    val shockRect = RectF(mx - mouthW * 0.85f, mouthY - mouthH * 0.45f, mx + mouthW * 0.85f, mouthY + mouthH * 0.78f)
                    canvas.drawOval(shockRect, mouthFill)
                    if (outlinePaint.strokeWidth > 0f) {
                        canvas.drawOval(shockRect, Paint(outlinePaint).apply { strokeWidth = 3.5f * u })
                    }
                }
                ActorExpression.HAPPY_TALK -> {
                    when (pose.mouthShape) {
                        MouthShape.OPEN_WIDE -> {
                            val wideRect = RectF(
                                mx - mouthW * 1.05f,
                                mouthY - mouthH * 0.35f,
                                mx + mouthW * 1.05f,
                                mouthY + maxOf(mouthH * 0.82f, 18f * u)
                            )
                            canvas.drawOval(wideRect, mouthFill)
                            val teethRect = RectF(wideRect.left + 4f * u, wideRect.top + 1f * u, wideRect.right - 4f * u, wideRect.top + wideRect.height() * 0.32f)
                            canvas.drawRoundRect(teethRect, 4f * u, 4f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                            val tongue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFB7185.toInt() }
                            canvas.drawArc(wideRect, 15f, 150f, true, tongue)
                            if (outlinePaint.strokeWidth > 0f) {
                                canvas.drawOval(wideRect, Paint(outlinePaint).apply { strokeWidth = 2.8f * u })
                            }
                        }
                        MouthShape.ROUND_O -> {
                            val radius = (11f + pose.mouthOpen * 7f) * u
                            val roundRect = RectF(mx - radius, mouthY - radius * 0.7f, mx + radius, mouthY + radius * 1.1f)
                            canvas.drawOval(roundRect, mouthFill)
                            if (outlinePaint.strokeWidth > 0f) {
                                canvas.drawOval(roundRect, Paint(outlinePaint).apply { strokeWidth = 3f * u })
                            }
                        }
                        MouthShape.SMILE_TEETH -> {
                            val smileW = if (isSideProfile) 19f * u else 31f * u
                            val smileH = (11f + pose.mouthOpen * 9f) * u
                            val smileRect = RectF(mx - smileW, mouthY - smileH * 0.25f, mx + smileW, mouthY + smileH)
                            canvas.drawArc(smileRect, 0f, 180f, true, mouthFill)
                            canvas.drawArc(smileRect, 4f, 172f, true, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                            if (outlinePaint.strokeWidth > 0f) {
                                canvas.drawArc(smileRect, 0f, 180f, true, Paint(outlinePaint).apply { strokeWidth = 2.8f * u })
                            }
                        }
                        MouthShape.CLOSED_M -> {
                            val closedW = if (isSideProfile) 14f * u else 22f * u
                            val lipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = 0xFF3F1D1D.toInt()
                                setStyle(Paint.Style.STROKE)
                                strokeWidth = 4.2f * u
                                strokeCap = Paint.Cap.ROUND
                            }
                            val lipPath = Path().apply {
                                moveTo(mx - closedW, mouthY + 2f * u)
                                quadTo(mx, mouthY + 6f * u, mx + closedW, mouthY + 1f * u)
                            }
                            canvas.drawPath(lipPath, lipPaint)
                        }
                    }
                }
            }

            if (role == ActorRole.RICH_MAN && pose.outfit == CharacterOutfit.OFFICE_SUIT && pose.gender == CharacterGender.MALE) {
                val stache = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.hair }
                if (isSideProfile) {
                    canvas.drawOval(RectF(mx - 12f * u, mouthY - 16f * u, mx + 24f * u, mouthY - 2f * u), stache)
                } else {
                    canvas.drawOval(RectF(mx - 32f * u, mouthY - 16f * u, mx, mouthY - 2f * u), stache)
                    canvas.drawOval(RectF(mx, mouthY - 16f * u, mx + 32f * u, mouthY - 2f * u), stache)
                }
            }
        }
    }

    private fun drawRoleHeadgear(
        canvas: Canvas,
        rootX: Float,
        headRect: RectF,
        u: Float,
        role: ActorRole,
        pal: RolePalette,
        turnShift: Float,
        silhouetteOnly: Boolean = false,
        headgear: ActorHeadgear = ActorHeadgear.NONE,
        outlinePaint: Paint = Paint(),
        strokeW: Float = 0f,
        gender: CharacterGender = CharacterGender.MALE,
        age: CharacterAge = CharacterAge.MIDDLE_AGED,
        outfit: CharacterOutfit = CharacterOutfit.CASUAL_TSHIRT
    ) {
        val cx = rootX + turnShift * 6f * u
        if (headgear != ActorHeadgear.NONE) {
            when (headgear) {
                ActorHeadgear.CHEF_HAT -> {
                    val puffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (silhouetteOnly) Color.BLACK else Color.WHITE
                        if (silhouetteOnly) {
                            setStyle(Paint.Style.FILL_AND_STROKE)
                            strokeWidth = strokeW
                        }
                    }
                    val bandRect = RectF(cx - 58f * u, headRect.top - 26f * u, cx + 58f * u, headRect.top + 8f * u)
                    canvas.drawCircle(cx - 34f * u, headRect.top - 50f * u, 32f * u, puffPaint)
                    canvas.drawCircle(cx + 34f * u, headRect.top - 50f * u, 32f * u, puffPaint)
                    canvas.drawCircle(cx, headRect.top - 64f * u, 38f * u, puffPaint)
                    canvas.drawRoundRect(bandRect, 8f * u, 8f * u, puffPaint)
                    if (!silhouetteOnly && strokeW > 0f) {
                        canvas.drawCircle(cx - 34f * u, headRect.top - 50f * u, 32f * u, outlinePaint)
                        canvas.drawCircle(cx + 34f * u, headRect.top - 50f * u, 32f * u, outlinePaint)
                        canvas.drawCircle(cx, headRect.top - 64f * u, 38f * u, outlinePaint)
                        canvas.drawRoundRect(bandRect, 8f * u, 8f * u, outlinePaint)
                    }
                    return
                }
                ActorHeadgear.HARD_HAT -> {
                    val hatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (silhouetteOnly) Color.BLACK else 0xFFFACC15.toInt()
                        if (silhouetteOnly) {
                            setStyle(Paint.Style.FILL_AND_STROKE)
                            strokeWidth = strokeW
                        }
                    }
                    val dome = RectF(cx - 82f * u, headRect.top - 58f * u, cx + 82f * u, headRect.top + 36f * u)
                    val brim = RectF(cx - 94f * u, headRect.top - 4f * u, cx + 98f * u, headRect.top + 14f * u)
                    canvas.drawArc(dome, 180f, 180f, true, hatPaint)
                    canvas.drawRoundRect(brim, 8f * u, 8f * u, hatPaint)
                    if (!silhouetteOnly && strokeW > 0f) {
                        canvas.drawArc(dome, 180f, 180f, true, outlinePaint)
                        canvas.drawRoundRect(brim, 8f * u, 8f * u, outlinePaint)
                        val ridge = RectF(cx - 12f * u, headRect.top - 64f * u, cx + 12f * u, headRect.top - 4f * u)
                        canvas.drawRoundRect(ridge, 6f * u, 6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEAB308.toInt() })
                        canvas.drawRoundRect(ridge, 6f * u, 6f * u, outlinePaint)
                    }
                    return
                }
                ActorHeadgear.CROWN -> {
                    val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (silhouetteOnly) Color.BLACK else 0xFFFFD600.toInt()
                        if (silhouetteOnly) {
                            setStyle(Paint.Style.FILL_AND_STROKE)
                            strokeWidth = strokeW
                        }
                    }
                    val crownPath = Path().apply {
                        moveTo(cx - 58f * u, headRect.top + 6f * u)
                        lineTo(cx - 64f * u, headRect.top - 56f * u)
                        lineTo(cx - 28f * u, headRect.top - 26f * u)
                        lineTo(cx, headRect.top - 70f * u)
                        lineTo(cx + 28f * u, headRect.top - 26f * u)
                        lineTo(cx + 64f * u, headRect.top - 56f * u)
                        lineTo(cx + 58f * u, headRect.top + 6f * u)
                        close()
                    }
                    canvas.drawPath(crownPath, crownPaint)
                    if (!silhouetteOnly) {
                        if (strokeW > 0f) canvas.drawPath(crownPath, outlinePaint)
                        canvas.drawCircle(cx, headRect.top - 16f * u, 8f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEF4444.toInt() })
                        canvas.drawCircle(cx - 34f * u, headRect.top - 14f * u, 6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3B82F6.toInt() })
                        canvas.drawCircle(cx + 34f * u, headRect.top - 14f * u, 6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10B981.toInt() })
                    }
                    return
                }
                ActorHeadgear.NONE -> Unit
            }
        }
        if (gender == CharacterGender.FEMALE && role != ActorRole.CHICKEN_MENTOR) {
            val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (silhouetteOnly) Color.BLACK else pal.hair
                if (silhouetteOnly) {
                    setStyle(Paint.Style.FILL_AND_STROKE)
                    strokeWidth = strokeW
                }
            }
            // Flowing side bob/shoulder tresses + top bangs (or elderly bun)
            val leftHair = RectF(headRect.left - 18f * u, headRect.top + 8f * u, headRect.left + 22f * u, headRect.bottom + 26f * u)
            val rightHair = RectF(headRect.right - 22f * u, headRect.top + 8f * u, headRect.right + 18f * u, headRect.bottom + 26f * u)
            canvas.drawRoundRect(leftHair, 16f * u, 16f * u, hairPaint)
            canvas.drawRoundRect(rightHair, 16f * u, 16f * u, hairPaint)
            val crownArc = RectF(headRect.left - 14f * u, headRect.top - 14f * u, headRect.right + 14f * u, headRect.top + 60f * u)
            canvas.drawArc(crownArc, 180f, 180f, true, hairPaint)
            if (age == CharacterAge.ELDERLY) {
                canvas.drawCircle(cx - turnShift * 18f * u, headRect.top - 18f * u, 28f * u, hairPaint)
                if (!silhouetteOnly && strokeW > 0f) {
                    canvas.drawCircle(cx - turnShift * 18f * u, headRect.top - 18f * u, 28f * u, outlinePaint)
                }
            }
            if (!silhouetteOnly && strokeW > 0f) {
                canvas.drawRoundRect(leftHair, 16f * u, 16f * u, outlinePaint)
                canvas.drawRoundRect(rightHair, 16f * u, 16f * u, outlinePaint)
                canvas.drawArc(crownArc, 180f, 180f, false, outlinePaint)
            }
            return
        }
        when {
            role == ActorRole.RICH_MAN && outfit == CharacterOutfit.OFFICE_SUIT -> {
                val hatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (silhouetteOnly) Color.BLACK else 0xFF111827.toInt()
                    if (silhouetteOnly) {
                        setStyle(Paint.Style.FILL_AND_STROKE)
                        strokeWidth = 20f * u
                    }
                }
                val brim = RectF(cx - 94f * u, headRect.top - 12f * u, cx + 94f * u, headRect.top + 12f * u)
                val crown = RectF(cx - 58f * u, headRect.top - 96f * u, cx + 58f * u, headRect.top + 2f * u)
                canvas.drawRoundRect(crown, 12f * u, 12f * u, hatPaint)
                canvas.drawRoundRect(brim, 10f * u, 10f * u, hatPaint)
                if (!silhouetteOnly) {
                    val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.secondary }
                    canvas.drawRect(cx - 58f * u, headRect.top - 22f * u, cx + 58f * u, headRect.top - 4f * u, band)
                }
            }
            role == ActorRole.HERO -> {
                val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.hair }
                val spikes = Path().apply {
                    moveTo(headRect.left + 8f * u, headRect.top + 38f * u)
                    lineTo(cx - 52f * u, headRect.top - 24f * u)
                    lineTo(cx - 22f * u, headRect.top + 2f * u)
                    lineTo(cx + 8f * u, headRect.top - 34f * u)
                    lineTo(cx + 32f * u, headRect.top + 4f * u)
                    lineTo(cx + 62f * u, headRect.top - 18f * u)
                    lineTo(headRect.right - 8f * u, headRect.top + 38f * u)
                    close()
                }
                canvas.drawPath(spikes, hairPaint)
                if (!silhouetteOnly && strokeW > 0f) canvas.drawPath(spikes, outlinePaint)
            }
            role != ActorRole.CHICKEN_MENTOR -> {
                val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.hair }
                val arcRect = RectF(headRect.left - 4f * u, headRect.top - 10f * u, headRect.right + 4f * u, headRect.top + 56f * u)
                canvas.drawArc(arcRect, 180f, 180f, true, hairPaint)
                if (!silhouetteOnly && strokeW > 0f) canvas.drawArc(arcRect, 180f, 180f, false, outlinePaint)
            }
        }
    }

    private fun drawWhiteboardStickman(
        canvas: Canvas,
        rootX: Float,
        rootY: Float,
        u: Float,
        role: ActorRole,
        pose: RigPose,
        globalTimeSec: Float
    ) {
        val wobbleSeed = (globalTimeSec * 4f).toInt().toFloat()
        val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1D26.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 10f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            pathEffect = DiscretePathEffect(18f * u, (2.8f + (wobbleSeed % 2f) * 0.5f) * u)
        }
        val redMarker = Paint(inkPaint).apply {
            color = 0xFFD9383A.toInt()
            strokeWidth = 8f * u
        }

        canvas.save()
        canvas.rotate(pose.bodyTiltDeg, rootX, rootY - 120f * u)

        val hipY = rootY - 135f * u
        val shoulderY = hipY - 160f * u
        val headCy = shoulderY - 76f * u

        val thighLen = 70f * u
        val shinLen = 65f * u
        for ((side, hipDeg, kneeDeg) in listOf(
            Triple(-1f, pose.leftHipDeg * pose.facingDir, pose.leftKneeDeg * pose.facingDir),
            Triple(1f, pose.rightHipDeg * pose.facingDir, pose.rightKneeDeg * pose.facingDir)
        )) {
            val hRad = Math.toRadians((90f + hipDeg).toDouble())
            val kx = rootX + side * 12f * u + cos(hRad).toFloat() * thighLen
            val ky = hipY + sin(hRad).toFloat() * thighLen
            val sRad = Math.toRadians((90f + hipDeg + kneeDeg).toDouble())
            val fx = kx + cos(sRad).toFloat() * shinLen
            val fy = ky + sin(sRad).toFloat() * shinLen
            canvas.drawLine(rootX, hipY, kx, ky, inkPaint)
            canvas.drawLine(kx, ky, fx, fy, inkPaint)
        }
        canvas.drawLine(rootX, hipY, rootX, shoulderY, inkPaint)

        val lUpperRad = Math.toRadians((90f + pose.leftShoulderDeg * pose.facingDir).toDouble())
        val lForeRad = Math.toRadians((90f + (pose.leftShoulderDeg + pose.leftElbowDeg) * pose.facingDir).toDouble())
        val rUpperRad = Math.toRadians((90f + pose.rightShoulderDeg * pose.facingDir).toDouble())
        val rForeRad = Math.toRadians((90f + (pose.rightShoulderDeg + pose.rightElbowDeg) * pose.facingDir).toDouble())

        val upperLen = 64f * u * pose.limbStretchY
        val foreLen = 58f * u * pose.limbStretchY
        val lEx = rootX + cos(lUpperRad).toFloat() * upperLen
        val lEy = shoulderY + 18f * u + sin(lUpperRad).toFloat() * upperLen
        val lx = lEx + cos(lForeRad).toFloat() * foreLen
        val ly = lEy + sin(lForeRad).toFloat() * foreLen

        val rEx = rootX + cos(rUpperRad).toFloat() * upperLen
        val rEy = shoulderY + 18f * u + sin(rUpperRad).toFloat() * upperLen
        val rx = rEx + cos(rForeRad).toFloat() * foreLen
        val ry = rEy + sin(rForeRad).toFloat() * foreLen

        canvas.drawLine(rootX, shoulderY + 18f * u, lEx, lEy, inkPaint)
        canvas.drawLine(lEx, lEy, lx, ly, inkPaint)
        canvas.drawLine(rootX, shoulderY + 18f * u, rEx, rEy, inkPaint)
        canvas.drawLine(rEx, rEy, rx, ry, inkPaint)

        if (pose.handProp != HandProp.NONE) {
            canvas.save()
            canvas.translate(rx, ry)
            canvas.rotate(pose.rightHandTiltDeg * pose.facingDir)
            PuppetPropRenderer.drawHandProp(
                canvas = canvas,
                handX = 0f,
                handY = 0f,
                u = u,
                prop = pose.handProp,
                globalTimeSec = globalTimeSec,
                silhouetteOnly = false,
                outlinePaint = inkPaint,
                facingDir = pose.facingDir
            )
            canvas.restore()
        }

        val headFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFAF6EE.toInt() }
        canvas.drawCircle(rootX, headCy, 66f * u, headFill)
        canvas.drawCircle(rootX, headCy, 66f * u, inkPaint)

        if (role == ActorRole.RICH_MAN) {
            canvas.save()
            canvas.rotate(pose.hairHatTiltDeg, rootX, headCy - 66f * u)
            canvas.drawRect(rootX - 42f * u, headCy - 110f * u, rootX + 42f * u, headCy - 66f * u, inkPaint)
            canvas.drawLine(rootX - 66f * u, headCy - 66f * u, rootX + 66f * u, headCy - 66f * u, inkPaint)
            canvas.restore()
        } else if (role == ActorRole.CHICKEN_MENTOR) {
            canvas.drawCircle(rootX - 10f * u, headCy - 72f * u, 14f * u, redMarker)
            canvas.drawCircle(rootX + 12f * u, headCy - 76f * u, 16f * u, redMarker)
        }

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1D26.toInt() }
        val eyeScale = pose.eyeScaleY.coerceAtLeast(0.15f)
        val faceX = rootX + pose.facingDir * pose.perspectiveProfile * 12f * u
        canvas.drawOval(RectF(faceX - 28f * u, headCy - 18f * u * eyeScale, faceX - 14f * u, headCy - 4f * u * eyeScale + 6f * u), dotPaint)
        canvas.drawOval(RectF(faceX + 14f * u, headCy - 18f * u * eyeScale, faceX + 28f * u, headCy - 4f * u * eyeScale + 6f * u), dotPaint)

        val mh = (4f + pose.mouthOpen * 20f) * u
        canvas.drawOval(RectF(faceX - 18f * u, headCy + 20f * u, faceX + 18f * u, headCy + 20f * u + mh), redMarker)

        canvas.restore()
    }

    private fun drawAnimeEmotes(
        canvas: Canvas,
        cx: Float,
        topY: Float,
        u: Float,
        pose: RigPose,
        globalTimeSec: Float,
        style: AnimationStyle
    ) {
        val pulse = 0.85f + 0.15f * sin(globalTimeSec * 8f)
        if (pose.expression == ActorExpression.SLEEPING_ZZZ) {
            val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF38BDF8.toInt()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val zOutline = Paint(zPaint).apply {
                color = 0xFF0E1018.toInt()
                setStyle(Paint.Style.STROKE)
                strokeWidth = 6f * u
                strokeJoin = Paint.Join.ROUND
            }
            val letters = listOf("Z", "z", "z")
            for (i in 0 until 3) {
                val phase = (globalTimeSec * 0.65f + i * 0.33f) % 1f
                val zx = cx + 66f * u + i * 24f * u + sin(globalTimeSec * 3f + i) * 10f * u
                val zy = topY + 34f * u - phase * 110f * u
                val sz = (46f - i * 8f) * u
                zPaint.textSize = sz
                zOutline.textSize = sz
                val alpha = ((1f - phase * 0.6f) * 255f).toInt().coerceIn(60, 255)
                zPaint.alpha = alpha
                zOutline.alpha = alpha
                canvas.drawText(letters[i], zx, zy, zOutline)
                canvas.drawText(letters[i], zx, zy, zPaint)
            }
            return
        }

        if (pose.expression == ActorExpression.PANIC_SWEAT_FIRE) {
            val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38BDF8.toInt() }
            val dropEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0E1018.toInt()
                setStyle(Paint.Style.STROKE)
                strokeWidth = 3.5f * u
            }
            for (i in 0 until 3) {
                val dir = if (i % 2 == 0) 1f else -1f
                val prog = (globalTimeSec * 1.4f + i * 0.35f) % 1f
                val dx = cx + dir * (84f + prog * 42f) * u
                val dy = topY + 26f * u + prog * 48f * u
                val path = Path().apply {
                    moveTo(dx, dy - 18f * u)
                    quadTo(dx + 16f * u, dy + 10f * u, dx, dy + 18f * u)
                    quadTo(dx - 16f * u, dy + 10f * u, dx, dy - 18f * u)
                }
                canvas.drawPath(path, dropPaint)
                canvas.drawPath(path, dropEdge)
            }
        }

        if (pose.expression == ActorExpression.SHOCKED_WIDE_EYES) {
            val shockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFD600.toInt()
                setStyle(Paint.Style.STROKE)
                strokeWidth = 6f * u * pulse
                strokeCap = Paint.Cap.ROUND
            }
            for (side in listOf(-1f, 1f)) {
                for (a in listOf(-32f, -52f, -12f)) {
                    val rad = Math.toRadians(a.toDouble())
                    val sx0 = cx + side * 98f * u
                    val sy0 = topY + 24f * u
                    canvas.drawLine(
                        sx0, sy0,
                        sx0 + side * cos(rad).toFloat() * 34f * u,
                        sy0 + sin(rad).toFloat() * 34f * u,
                        shockPaint
                    )
                }
            }
        }

        when (pose.emoteType) {
            1 -> {
                val dropX = cx + 92f * u
                val dropY = topY + 40f * u + (globalTimeSec * 30f % 24f) * u
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38BDF8.toInt() }
                val path = Path().apply {
                    moveTo(dropX, dropY - 24f * u)
                    quadTo(dropX + 22f * u, dropY + 12f * u, dropX, dropY + 24f * u)
                    quadTo(dropX - 22f * u, dropY + 12f * u, dropX, dropY - 24f * u)
                }
                canvas.drawPath(path, p)
            }
            2 -> {
                val vx = cx + 96f * u
                val vy = topY + 26f * u
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFEF4444.toInt()
                    setStyle(Paint.Style.STROKE)
                    strokeWidth = 7f * u * pulse
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawArc(RectF(vx - 26f * u, vy - 26f * u, vx, vy), 0f, 90f, false, p)
                canvas.drawArc(RectF(vx + 6f * u, vy - 26f * u, vx + 32f * u, vy), 90f, 90f, false, p)
                canvas.drawArc(RectF(vx - 26f * u, vy + 6f * u, vx, vy + 32f * u), 270f, 90f, false, p)
                canvas.drawArc(RectF(vx + 6f * u, vy + 6f * u, vx + 32f * u, vy + 32f * u), 180f, 90f, false, p)
            }
            3 -> if (style == AnimationStyle.CHIBI_STORYTIME) {
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE066.toInt() }
                for (i in 0 until 3) {
                    val sx = cx + (i - 1) * 105f * u
                    val sy = topY + 18f * u - (i % 2) * 32f * u + sin(globalTimeSec * 5f + i) * 10f * u
                    val s = (16f * pulse) * u
                    val star = Path().apply {
                        moveTo(sx, sy - s)
                        lineTo(sx + s * 0.3f, sy - s * 0.3f)
                        lineTo(sx + s, sy)
                        lineTo(sx + s * 0.3f, sy + s * 0.3f)
                        lineTo(sx, sy + s)
                        lineTo(sx - s * 0.3f, sy + s * 0.3f)
                        lineTo(sx - s, sy)
                        lineTo(sx - s * 0.3f, sy - s * 0.3f)
                        close()
                    }
                    canvas.drawPath(star, sp)
                }
            }
        }
    }

    fun drawMotionGraphicsOverlay(
        canvas: Canvas,
        stageW: Float,
        stageH: Float,
        scene: SceneScript,
        style: AnimationStyle,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float
    ) {
        MotionGraphicsOverlayRenderer.drawOverlays(
            canvas = canvas,
            w = stageW,
            h = stageH,
            scene = scene,
            style = style,
            sceneElapsedSec = sceneElapsedSec,
            sceneDurationSec = sceneDurationSec,
            globalTimeSec = globalTimeSec
        )
    }

    private fun darkenColor(color: Int, factor: Float): Int = scaleColor(color, factor)
    private fun lightenColor(color: Int, factor: Float): Int = scaleColor(color, factor)

    private fun scaleColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }
}
