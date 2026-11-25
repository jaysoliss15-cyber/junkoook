package com.example

import com.example.art.AiHordeImageClient
import com.example.art.EnvironmentRenderer
import com.example.art.MagicByteValidator
import com.example.art.SceneArtGenerator
import com.example.data.BuiltInPrompts
import com.example.llm.JsonSanitizer
import com.example.llm.LlmDirectorClient
import com.example.llm.LlmHttpException
import com.example.llm.LlmProviderPreset
import com.example.llm.MalformedSceneJsonException
import com.example.llm.TagalogNarrationValidator
import com.example.llm.UniversalModelDetector
import com.example.tts.ElevenLabsClient
import com.example.tts.ElevenLabsHttpException
import com.example.model.ActorAction
import com.example.model.ActorExpression
import com.example.model.ActorHeadgear
import com.example.model.ActorRole
import com.example.model.AnimationStyle
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
import com.example.model.MouthShape
import com.example.model.PointerCallout
import com.example.model.ProductionMode
import com.example.model.PronunciationEntry
import com.example.model.SceneScript
import com.example.model.ShotFraming
import com.example.model.ShotType
import com.example.model.StatusPill
import com.example.model.StudioSettings
import com.example.model.WordTiming
import com.example.model.WorldFx
import com.example.render.CameraController
import com.example.render.CharacterSpriteProcessor
import com.example.render.DynamicDiagramRenderer
import com.example.render.MetaphorVisualRenderer
import com.example.render.PixelCutawayRenderer
import com.example.render.ProceduralPuppetEngine
import com.example.render.PuppetSpringSolver
import com.example.tts.AudioPcmStitcher
import com.example.tts.TagalogPhoneticNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class ToonForgeCoreTest {

    @Test
    fun `jsonSanitizer strips think tags and markdown fences`() {
        val raw = """
            <think>
            Let me carefully plan the Tagalog scenes first...
            </think>
            ```json
            [
              {
                "scene_id": 1,
                "tagalog_narration": "Alam mo ba kung bakit laging ubos ang sahod mo?"
              }
            ]
            ```
        """.trimIndent()

        val extracted = JsonSanitizer.extractJsonArrayText(raw)
        assertTrue(extracted.startsWith("["))
        assertTrue(extracted.endsWith("]"))
        assertFalse(extracted.contains("<think>"))
        assertFalse(extracted.contains("```"))
    }

    @Test(expected = MalformedSceneJsonException::class)
    fun `jsonSanitizer rejects response without array brackets`() {
        JsonSanitizer.extractJsonArrayText("<think>only thoughts</think> no json here")
    }

    @Test
    fun `tagalogPhoneticNormalizer expands peso currency percentages and acronyms`() {
        val input = "Nakatipid ng ₱1,500 at 50% ang OFW gamit ang GCash budget."
        val out = TagalogPhoneticNormalizer.normalize(input)
        assertTrue(out.contains("isang libo limang daan piso"))
        assertTrue(out.contains("limampu porsyento"))
        assertTrue(out.contains("oh ef double-u"))
        assertTrue(out.contains("ji-kash"))
        assertTrue(out.contains("badyet"))
    }

    @Test
    fun `tagalogNarrationValidator accepts natural Tagalog and rejects gibberish or literal idioms`() {
        assertTrue(
            TagalogNarrationValidator.isValidTagalogNarration(
                "Huwag umasa sa iisang kita lang dahil mabilis magbago ang panahon."
            )
        )
        assertTrue(
            TagalogNarrationValidator.isValidTagalogNarration(
                "Nakakagulat pero ₱1,500 lang ang kailangan para magsimula ang OFW sa pag-iipon!"
            )
        )

        // Rejects literal English idiom slop
        assertFalse(
            TagalogNarrationValidator.isValidTagalogNarration(
                "Huwag mong ilagay ang lahat ng itlog sa isang basket ngayon."
            )
        )
        // Rejects gibberish consonant-smash words
        assertFalse(
            TagalogNarrationValidator.isValidTagalogNarration(
                "Alam mo ba kung bakit xzqprt ang sahod mo?"
            )
        )
        // Rejects non-Latin leakage
        assertFalse(
            TagalogNarrationValidator.isValidTagalogNarration(
                "Alam mo ba kung bakit привет ang sahod mo?"
            )
        )
        // Rejects pure English with zero Tagalog particles
        assertFalse(
            TagalogNarrationValidator.isValidTagalogNarration(
                "Never put every single coin inside one small pocket forever."
            )
        )
        // Cleans stray stage directions before TTS
        assertEquals(
            "Grabe talaga ang pagtitipid ng batang ito!",
            TagalogNarrationValidator.cleanForTts("Narrator: [excited] Grabe talaga ang pagtitipid ng batang ito!")
        )
    }

    @Test
    fun `magicByteValidator validates PNG JPEG WebP and rejects HTML`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
        val webp = "RIFF1234WEBP".toByteArray(Charsets.US_ASCII)
        val html = "<html><body>401 Unauthorized</body></html>".toByteArray(Charsets.US_ASCII)

        assertEquals(MagicByteValidator.ImageFormat.PNG, MagicByteValidator.detectFormat(png))
        assertEquals(MagicByteValidator.ImageFormat.JPEG, MagicByteValidator.detectFormat(jpeg))
        assertEquals(MagicByteValidator.ImageFormat.WEBP, MagicByteValidator.detectFormat(webp))
        assertFalse(MagicByteValidator.isValidImageBytes(html))
    }

    @Test
    fun `twoLayerPromptComposition enforces natural Tagalog and locks json schema`() {
        val customPrompt = "Custom Pinoy comedy tone with punchy jokes."
        val composed = BuiltInPrompts.composeSystemPrompt(customPrompt, 30, AnimationStyle.PAPER_DIORAMA)
        assertTrue(composed.startsWith(customPrompt))
        assertTrue(composed.contains("MANDATORY TECHNICAL JSON FORMAT (DO NOT OVERRIDE)"))
        assertTrue(composed.contains("STRICT NATURAL TAGALOG LANGUAGE RULES"))
        assertTrue(composed.contains("\"shot_framing\""))
        assertTrue(composed.contains("\"tagalog_narration\""))
        assertTrue(composed.contains("\"world_fx\""))
        assertTrue(composed.contains("\"status_pill\""))
        assertTrue(composed.contains("\"hud_widget\""))
    }

    @Test
    fun `techExplainerAndStudioProModelsMaintainSafeDefaults`() {
        assertEquals(9, AnimationStyle.entries.size)
        assertTrue(AnimationStyle.TECH_EXPLAINER.usesNetworkArt)
        assertEquals("HUD EXPLAINER", AnimationStyle.TECH_EXPLAINER.badge)
        assertEquals("Viral HUD & Motion Explainer", AnimationStyle.TECH_EXPLAINER.displayName)
        assertTrue(AnimationStyle.CROSS_SECTION.usesNetworkArt)
        assertEquals("CUTAWAY 2D", AnimationStyle.CROSS_SECTION.badge)

        assertEquals(3, ProductionMode.entries.size)
        assertEquals(ProductionMode.CLIP_AUTO_EDITOR, ProductionMode.fromRaw("clip_auto_editor"))
        assertEquals(ProductionMode.CROSS_SECTION_EXPLAINER, ProductionMode.fromRaw("CROSS_SECTION_EXPLAINER"))
        assertEquals(ProductionMode.FULL_AI_STORY, ProductionMode.fromRaw("invalid"))

        assertEquals(ShotFraming.MEDIUM_WAIST_UP, ShotFraming.fromRaw("medium_waist_up"))
        assertEquals(ShotFraming.CLOSE_UP_FACE, ShotFraming.fromRaw("close_up_face"))
        assertEquals(ShotFraming.RULE_OF_THIRDS_LEFT, ShotFraming.fromRaw("RULE_OF_THIRDS_LEFT"))
        assertEquals(ShotFraming.AUTO, ShotFraming.fromRaw("unknown"))

        assertEquals(StatusPill.GOAL, StatusPill.fromRaw("goal"))
        assertEquals(StatusPill.PROBLEM, StatusPill.fromRaw("PROBLEM"))
        assertEquals(StatusPill.NONE, StatusPill.fromRaw("unknown_value"))
        assertEquals(StatusPill.NONE, StatusPill.fromRaw(null))

        assertEquals(HudWidget.PROGRESS_GAUGE, HudWidget.fromRaw("progress_gauge"))
        assertEquals(HudWidget.WAVEFORM_GRAPH, HudWidget.fromRaw("WAVEFORM_GRAPH"))
        assertEquals(HudWidget.ANIMATED_CHECKLIST, HudWidget.fromRaw("ANIMATED_CHECKLIST"))
        assertEquals(HudWidget.NONE, HudWidget.fromRaw("invalid"))

        val scene = SceneScript(
            sceneId = 1,
            tagalogNarration = "Alam mo ba kung bakit mahalaga ang pag-iipon?",
            backgroundPrompt = "bg",
            cameraMotion = CameraMotion.STATIC,
            actorRole = ActorRole.HERO,
            actorAction = ActorAction.TALK_BOUNCE,
            worldFx = WorldFx.NONE
        )
        assertEquals(StatusPill.NONE, scene.statusPill)
        assertEquals(HudWidget.NONE, scene.hudWidget)
        assertEquals(ShotFraming.AUTO, scene.shotFraming)
        assertFalse(ProceduralPuppetEngine.hasActiveHudOverlay(scene, AnimationStyle.PAPER_DIORAMA))
        assertTrue(ProceduralPuppetEngine.hasActiveHudOverlay(scene, AnimationStyle.TECH_EXPLAINER))
        assertTrue(ProceduralPuppetEngine.hasActiveHudOverlay(scene, AnimationStyle.CROSS_SECTION))
        assertTrue(
            ProceduralPuppetEngine.hasActiveHudOverlay(
                scene,
                AnimationStyle.PAPER_DIORAMA,
                ProductionMode.CLIP_AUTO_EDITOR
            )
        )
        assertEquals(ShotFraming.MEDIUM_WAIST_UP, ProceduralPuppetEngine.resolveShotFraming(scene))
        assertEquals(ShotFraming.CLOSE_UP_FACE, ProceduralPuppetEngine.resolveShotFraming(scene.copy(sceneId = 4)))

        val techComposed = BuiltInPrompts.composeSystemPrompt("Base", 30, AnimationStyle.TECH_EXPLAINER)
        assertTrue(techComposed.contains("IMPORTANT FOR Viral HUD & Motion Explainer"))
        val cutawayComposed = BuiltInPrompts.composeSystemPrompt("Base", 30, AnimationStyle.CROSS_SECTION)
        assertTrue(cutawayComposed.contains("IMPORTANT FOR 2D Cutaway & Engineering Diagram"))
        assertTrue(cutawayComposed.contains("CLOSE_UP_FACE"))
    }

    @Test
    fun `audioPcmStitcher computes sample accurate offsets and mixes SFX plus auto-ducking BGM`() {
        // 44100 samples * 2 bytes = 88200 bytes = exactly 1000.0 ms
        val oneSecPcm = ByteArray(88200)
        val halfSecPcm = ByteArray(44100)
        val stitchedDry = AudioPcmStitcher.stitchScenes(
            decodedPcmPerScene = listOf(oneSecPcm, halfSecPcm),
            localTimingsPerScene = listOf(
                listOf(WordTiming("Una", 100.0, 400.0)),
                listOf(WordTiming("Pangalawa", 50.0, 350.0))
            ),
            narrationPerScene = listOf("Una", "Pangalawa")
        )
        assertEquals(1500.0, stitchedDry.totalDurationMs, 0.01)
        assertEquals(0.0, stitchedDry.sceneStartMs[0], 0.01)
        assertEquals(1000.0, stitchedDry.sceneStartMs[1], 0.01)
        assertEquals(1050.0, stitchedDry.sceneWordTimings[1][0].startMs, 0.01)
        assertEquals(1350.0, stitchedDry.sceneWordTimings[1][0].endMs, 0.01)

        // Mix with scenes containing WHIP_PAN, COIN_RAIN, StatusPill, and ANIMATED_CHECKLIST
        val twoSecSilent = ByteArray(44100 * 2 * 2)
        val sampleScenes = listOf(
            SceneScript(
                sceneId = 1,
                tagalogNarration = "Alam mo ba ang sikreto sa mabilis na pag-iipon?",
                backgroundPrompt = "warm village street",
                cameraMotion = CameraMotion.WHIP_PAN_LEFT,
                actorRole = ActorRole.CHICKEN_MENTOR,
                actorAction = ActorAction.POINT_STICK,
                worldFx = WorldFx.COIN_RAIN,
                statusPill = StatusPill.SECRET,
                calloutBanner = "90% LAKAS",
                hudWidget = HudWidget.ANIMATED_CHECKLIST,
                checklistItems = listOf("Una", "Pangalawa", "Pangatlo")
            )
        )
        val stitchedMixed = AudioPcmStitcher.stitchScenes(
            decodedPcmPerScene = listOf(twoSecSilent),
            localTimingsPerScene = listOf(emptyList()),
            narrationPerScene = listOf(sampleScenes[0].tagalogNarration),
            scenes = sampleScenes,
            style = AnimationStyle.TECH_EXPLAINER
        )
        assertEquals(twoSecSilent.size, stitchedMixed.unifiedPcm44100Mono.size)

        // Verify non-silent BGM + SFX energy present and safely within 16-bit PCM bounds
        val shorts = ShortArray(stitchedMixed.unifiedPcm44100Mono.size / 2)
        ByteBuffer.wrap(stitchedMixed.unifiedPcm44100Mono).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        var maxAmp = 0
        for (s in shorts) {
            val a = abs(s.toInt())
            if (a > maxAmp) maxAmp = a
        }
        assertTrue("Expected mixed BGM + SFX to produce audible non-zero PCM", maxAmp > 200)
        assertTrue("Expected mixed PCM not to overflow 16-bit range", maxAmp <= Short.MAX_VALUE.toInt())
    }

    @Test
    fun `editablePronunciationDictionaryAppliesWithoutMutatingCaptionDisplay`() {
        val dict = listOf(
            PronunciationEntry("Nike", "Nayk"),
            PronunciationEntry("WiFi", "Way-Fay"),
            PronunciationEntry("₱", "pesos"),
            PronunciationEntry("%", "percent")
        )
        val raw = "Bumili ng Nike at WiFi gamit ang ₱500 at 25% discount!"
        val spoken = TagalogPhoneticNormalizer.normalize(raw, dict)
        assertTrue(spoken.contains("Nayk"))
        assertTrue(spoken.contains("Way-Fay"))
        assertTrue(spoken.contains("limang daan pesos"))
        assertTrue(spoken.contains("dalawampu't lima percent"))
        // Original caption string remains untouched for display
        assertTrue(raw.contains("Nike") && raw.contains("WiFi") && raw.contains("₱500") && raw.contains("25%"))
    }

    @Test
    fun `characterSpriteAlphaErosionStripsEdgeHalosWhilePreservingCore`() {
        // 7x7 ARGB sprite with transparent outer border, 1px bright white semi-transparent halo ring, and 3x3 solid core
        val w = 7
        val h = 7
        val src = IntArray(w * h)
        for (y in 1..5) {
            for (x in 1..5) {
                val isCore = x in 2..4 && y in 2..4
                src[y * w + x] = if (isCore) {
                    0xFFFF4422.toInt()
                } else {
                    0x99FFFFFF.toInt() // 1px white halo edge
                }
            }
        }

        val eroded = CharacterSpriteProcessor.erodeAlphaBuffer(src, w, h, radiusPx = 1.5f)
        // Outer halo ring (touching alpha=0 neighbors) should be stripped cleanly
        assertEquals(0, (eroded[1 * w + 1] ushr 24) and 0xFF)
        assertEquals(0, (eroded[1 * w + 3] ushr 24) and 0xFF)
        // Center solid pixel remains 100% opaque
        assertEquals(255, (eroded[3 * w + 3] ushr 24) and 0xFF)
    }

    @Test
    fun `customBgmDucksTo10PercentDuringSpeechAnd28PercentInPausesAndProblemPlaysThud`() {
        assertEquals(0.10f, AudioPcmStitcher.BGM_DUCKED_LEVEL, 0.001f)
        assertEquals(0.28f, AudioPcmStitcher.BGM_PAUSE_LEVEL, 0.001f)

        // 1 second of silent dry voice + 100ms custom constant-amplitude BGM
        val oneSecDry = ByteArray(44100 * 2)
        val bgmShorts = ShortArray(4410) { 10_000 }
        val bgmBytes = ByteArray(bgmShorts.size * 2)
        ByteBuffer.wrap(bgmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(bgmShorts)

        val problemScene = listOf(
            SceneScript(
                sceneId = 2,
                tagalogNarration = "Malaking problema ito kung hindi ka mag-iipon ngayon.",
                backgroundPrompt = "city",
                cameraMotion = CameraMotion.STATIC,
                actorRole = ActorRole.NARRATOR,
                actorAction = ActorAction.TALK_BOUNCE,
                worldFx = WorldFx.NONE,
                statusPill = StatusPill.PROBLEM
            )
        )
        val mixed = AudioPcmStitcher.stitchScenes(
            decodedPcmPerScene = listOf(oneSecDry),
            localTimingsPerScene = listOf(emptyList()),
            narrationPerScene = listOf(problemScene[0].tagalogNarration),
            scenes = problemScene,
            style = AnimationStyle.CROSS_SECTION,
            customBgmPcm44100Mono = bgmBytes
        )
        val outShorts = ShortArray(mixed.unifiedPcm44100Mono.size / 2)
        ByteBuffer.wrap(mixed.unifiedPcm44100Mono).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(outShorts)
        var peak = 0
        for (s in outShorts) {
            val a = abs(s.toInt())
            if (a > peak) peak = a
        }
        // Custom BGM (~2800 in pauses) + PROBLEM sub-bass thud (~9800 peak) must produce clear non-zero audio
        assertTrue("Expected custom BGM + PROBLEM thud to exceed 3000 amplitude", peak > 3000)
    }

    @Test
    fun `characterPoseHandPropShotTypeAndCameraControllerWorkCohesively`() {
        assertEquals(8, CharacterPose.entries.size)
        assertEquals(10, HandProp.entries.size)
        assertEquals(6, ShotType.entries.size)
        assertEquals(-230f, ProceduralPuppetEngine.HUD_SAFE_LIFT_PX, 0.01f)

        assertEquals(CharacterPose.WALK_ACROSS_LEFT_TO_RIGHT, CharacterPose.fromRaw("walk_across_left_to_right"))
        assertEquals(CharacterPose.TWO_CHARACTER_CONVERSATION, CharacterPose.fromRaw("TWO_CHARACTER_CONVERSATION"))
        assertEquals(CharacterPose.AUTO, CharacterPose.fromRaw("invalid"))
        assertEquals(HandProp.LIGHTBULB, HandProp.fromRaw("lightbulb"))
        assertEquals(HandProp.MONEY_BAG, HandProp.fromRaw("MONEY_BAG"))
        assertEquals(HandProp.NONE, HandProp.fromRaw(null))
        assertEquals(ShotType.TRACKING_FOLLOW, ShotType.fromRaw("tracking_follow"))
        assertEquals(ShotType.WHIP_CUT, ShotType.fromRaw("WHIP_CUT"))
        assertEquals(ShotType.AUTO, ShotType.fromRaw("invalid"))

        val schemaPrompt = BuiltInPrompts.composeSystemPrompt("Base", 30, AnimationStyle.PAPER_DIORAMA)
        assertTrue(schemaPrompt.contains("\"character_pose\""))
        assertTrue(schemaPrompt.contains("\"hand_prop\""))
        assertTrue(schemaPrompt.contains("\"shot_type\""))

        val scene = SceneScript(
            sceneId = 1,
            tagalogNarration = "Lakad lang nang lakad habang may hawak na ilaw ng ideya!",
            backgroundPrompt = "warm street",
            cameraMotion = CameraMotion.STATIC,
            actorRole = ActorRole.HERO,
            actorAction = ActorAction.WALK_IN,
            worldFx = WorldFx.NONE,
            characterPose = CharacterPose.WALK_ACROSS_LEFT_TO_RIGHT,
            handProp = HandProp.LIGHTBULB,
            shotType = ShotType.TRACKING_FOLLOW,
            secondaryActorRole = ActorRole.CHICKEN_MENTOR
        )

        // CameraController zoom & tracking verification
        val mediumCam = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f,
            scene = scene.copy(shotType = ShotType.MEDIUM_WAIST_UP, characterPose = CharacterPose.THREE_QUARTER_EXPLAIN),
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 0, sceneElapsedSec = 1.0f, sceneDurationSec = 4f, globalTimeSec = 1.0f,
            hasActiveHud = true, isCutaway = false
        )
        assertEquals(1.45f, mediumCam.scale, 0.01f)

        val closeCam = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f,
            scene = scene.copy(shotType = ShotType.CLOSE_UP_REACTION, characterPose = CharacterPose.SHOCK_AIRBORNE_RECOIL),
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 1, sceneElapsedSec = 1.0f, sceneDurationSec = 4f, globalTimeSec = 5.0f,
            hasActiveHud = false, isCutaway = false
        )
        assertEquals(1.90f, closeCam.scale, 0.01f)

        val trackStart = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f, scene = scene,
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 0, sceneElapsedSec = 0.1f, sceneDurationSec = 4f, globalTimeSec = 0.1f,
            hasActiveHud = false, isCutaway = false
        )
        val trackLate = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f, scene = scene,
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 0, sceneElapsedSec = 2.8f, sceneDurationSec = 4f, globalTimeSec = 2.8f,
            hasActiveHud = false, isCutaway = false
        )
        assertTrue(
            "TRACKING_FOLLOW should pan horizontally as walking character moves left-to-right",
            trackStart.translateX > trackLate.translateX + 100f
        )

        val whipStart = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f,
            scene = scene.copy(shotType = ShotType.WHIP_CUT),
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 2, sceneElapsedSec = 0.05f, sceneDurationSec = 4f, globalTimeSec = 8.05f,
            hasActiveHud = false, isCutaway = false
        )
        assertTrue("WHIP_CUT should emit directional blur strength early in cut", whipStart.whipBlurStrength > 0.7f)

        // Spring solver follow-through lag verification during recoil launch
        val recoilSpring = PuppetSpringSolver.solve(
            pose = CharacterPose.SHOCK_AIRBORNE_RECOIL,
            sceneElapsedSec = 0.25f,
            sceneDurationSec = 4.0f
        )
        assertTrue(
            "Spring follow-through should produce non-zero head/hair lag during airborne recoil",
            abs(recoilSpring.hairHatTiltDeg) > 0.5f && abs(recoilSpring.trailingArmLagDeg) > 0.5f
        )

        // Two-character partner fallback verification
        assertEquals(
            ActorRole.CHICKEN_MENTOR,
            ProceduralPuppetEngine.resolvePartnerRole(ActorRole.HERO, null, sceneId = 1)
        )
        assertEquals(
            ActorRole.RICH_MAN,
            ProceduralPuppetEngine.resolvePartnerRole(ActorRole.HERO, null, sceneId = 2)
        )
    }

    @Test
    fun `viral2dExplainerEnumsMetaphorsAndSchemaStayBackwardCompatible`() {
        assertEquals(6, ActorExpression.entries.size)
        assertEquals(4, ActorHeadgear.entries.size)
        assertEquals(14, MetaphorVisual.entries.size)

        assertFalse(AnimationStyle.VIRAL_2D_EXPLAINER.usesNetworkArt)
        assertEquals("2D VIRAL", AnimationStyle.VIRAL_2D_EXPLAINER.badge)

        assertEquals(ActorExpression.PANIC_SWEAT_FIRE, ActorExpression.fromRaw("panic_sweat_fire"))
        assertEquals(ActorExpression.TONGUE_STING, ActorExpression.fromRaw("TONGUE_STING"))
        assertEquals(ActorExpression.HAPPY_TALK, ActorExpression.fromRaw("unknown"))

        assertEquals(ActorHeadgear.CHEF_HAT, ActorHeadgear.fromRaw("chef_hat"))
        assertEquals(ActorHeadgear.HARD_HAT, ActorHeadgear.fromRaw("HARD_HAT"))
        assertEquals(ActorHeadgear.CROWN, ActorHeadgear.fromRaw("crown"))
        assertEquals(ActorHeadgear.NONE, ActorHeadgear.fromRaw(null))

        assertEquals(MetaphorVisual.CHOMP_CHAIN, MetaphorVisual.fromRaw("chomp_chain"))
        assertEquals(MetaphorVisual.TISSUE_CROSS_SECTION, MetaphorVisual.fromRaw("TISSUE_CROSS_SECTION"))
        assertEquals(MetaphorVisual.BOILING_POT_PROCESS, MetaphorVisual.fromRaw("boiling_pot_process"))
        assertEquals(MetaphorVisual.SUNBURST_SPOTLIGHT, MetaphorVisual.fromRaw("sunburst_spotlight"))
        assertEquals(MetaphorVisual.NONE, MetaphorVisual.fromRaw("invalid"))

        val defaultScene = SceneScript(
            sceneId = 2,
            tagalogNarration = "Bakit nasisira ang tiyan kapag sobrang anghang?",
            backgroundPrompt = "kitchen",
            cameraMotion = CameraMotion.STATIC,
            actorRole = ActorRole.HERO,
            actorAction = ActorAction.TALK_BOUNCE,
            worldFx = WorldFx.NONE,
            statusPill = StatusPill.PROBLEM
        )
        assertEquals(ActorExpression.HAPPY_TALK, defaultScene.expression)
        assertEquals(ActorHeadgear.NONE, defaultScene.headgear)
        assertEquals(MetaphorVisual.NONE, defaultScene.metaphorVisual)

        assertEquals(
            MetaphorVisual.NONE,
            MetaphorVisualRenderer.resolveMetaphorVisual(defaultScene, AnimationStyle.VIRAL_2D_EXPLAINER)
        )
        assertEquals(
            MetaphorVisual.CHOMP_CHAIN,
            MetaphorVisualRenderer.resolveMetaphorVisual(
                defaultScene.copy(metaphorVisual = MetaphorVisual.CHOMP_CHAIN),
                AnimationStyle.VIRAL_2D_EXPLAINER
            )
        )
        assertEquals(
            MetaphorVisual.NONE,
            MetaphorVisualRenderer.resolveMetaphorVisual(defaultScene, AnimationStyle.PAPER_DIORAMA)
        )
        assertTrue(MetaphorVisualRenderer.isDiagramAlongsideCharacter(MetaphorVisual.BOILING_POT_PROCESS))
        assertFalse(MetaphorVisualRenderer.isDiagramAlongsideCharacter(MetaphorVisual.SUNBURST_SPOTLIGHT))

        assertEquals(
            ActorExpression.PANIC_SWEAT_FIRE,
            ProceduralPuppetEngine.resolveExpression(defaultScene, AnimationStyle.VIRAL_2D_EXPLAINER)
        )
        assertEquals(
            ActorExpression.HAPPY_TALK,
            ProceduralPuppetEngine.resolveExpression(defaultScene, AnimationStyle.PAPER_DIORAMA)
        )

        val viralPrompt = BuiltInPrompts.composeSystemPrompt("Base", 30, AnimationStyle.VIRAL_2D_EXPLAINER)
        assertTrue(viralPrompt.contains("\"expression\""))
        assertTrue(viralPrompt.contains("\"headgear\""))
        assertTrue(viralPrompt.contains("\"metaphor_visual\""))
        assertTrue(viralPrompt.contains("\"topic_pill\""))
        assertTrue(viralPrompt.contains("CHOMP_CHAIN"))
    }

    @Test
    fun `jsonSanitizer repairs truncated json array preserving completed scenes`() {
        val truncated = """
            ```json
            [
              {
                "scene_id": 1,
                "tagalog_narration": "Alam mo ba kung bakit laging kulang ang ipon mo?",
                "background_prompt": "warm street with glowing [lanterns] and {houses}",
                "camera_motion": "ZOOM_IN",
                "actor_role": "HERO",
                "actor_action": "TALK_BOUNCE",
                "world_fx": "COIN_RAIN",
                "checklist_items": ["Magtabi agad", "Iwasan ang utang"]
              },
              {
                "scene_id": 2,
                "tagalog_narration": "Heto ang pangalawang
        """.trimIndent()

        val repaired = JsonSanitizer.extractJsonArrayText(truncated)
        assertTrue(repaired.startsWith("["))
        assertTrue(repaired.endsWith("]"))
        assertTrue(repaired.contains("\"scene_id\": 1"))
        assertTrue(repaired.contains("\"checklist_items\": [\"Magtabi agad\", \"Iwasan ang utang\"]"))
        assertFalse(repaired.contains("\"scene_id\": 2"))
        assertFalse(repaired.contains("Heto ang pangalawang"))
    }

    @Test
    fun `universalModelDetector infers providers builds probes and ranks models`() {
        // Presets exclude AI Horde text fallback and include DeepSeek first
        assertEquals(
            listOf("DEEPSEEK", "GEMINI", "GROQ", "OPENROUTER", "CUSTOM"),
            LlmProviderPreset.entries.map { it.name }
        )
        assertEquals("https://api.deepseek.com", LlmProviderPreset.DEEPSEEK.baseUrl)

        // Key-prefix provider auto-fill
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai",
            UniversalModelDetector.inferBaseUrlFromApiKey("AIzaSyDummyTestKey123")
        )
        assertEquals(
            "https://api.groq.com/openai/v1",
            UniversalModelDetector.inferBaseUrlFromApiKey("gsk_dummy_test")
        )
        assertEquals(
            "https://openrouter.ai/api/v1",
            UniversalModelDetector.inferBaseUrlFromApiKey("sk-or-v1-dummy")
        )
        assertEquals(null, UniversalModelDetector.inferBaseUrlFromApiKey("0000000000"))

        // Endpoint path discovery order
        assertEquals(
            listOf(
                "https://proxy.example.com/models",
                "https://proxy.example.com/v1/models",
                "https://proxy.example.com/openai/v1/models"
            ),
            UniversalModelDetector.buildCandidateModelUrls("https://proxy.example.com///")
        )
        assertEquals(
            listOf(
                "https://api.groq.com/openai/v1/models"
            ),
            UniversalModelDetector.buildCandidateModelUrls("https://api.groq.com/openai/v1")
        )
        assertEquals(
            listOf(
                "https://generativelanguage.googleapis.com/v1beta/openai/models"
            ),
            UniversalModelDetector.buildCandidateModelUrls("https://generativelanguage.googleapis.com/v1beta/openai")
        )

        // Resolved chat completions URLs
        assertEquals(
            "https://proxy.example.com/v1",
            UniversalModelDetector.resolveChatBaseUrlFromModelsUrl("https://proxy.example.com/v1/models")
        )
        assertEquals(
            "https://proxy.example.com/v1/chat/completions",
            UniversalModelDetector.buildCandidateChatUrls(
                baseUrl = "https://proxy.example.com",
                detectedChatBaseUrl = "https://proxy.example.com/v1"
            ).first()
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            UniversalModelDetector.buildCandidateChatUrls(
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai"
            ).first()
        )

        // Filtering & ranking
        val rankedChatOnly = UniversalModelDetector.filterAndRankModels(
            listOf("whisper-1", "text-embedding-3-small", "dall-e-3", "zeta-chat", "qwen-2.5", "gemini-2.5-flash", "alpha-llm"),
            showAllRaw = false
        )
        assertEquals(listOf("gemini-2.5-flash", "qwen-2.5", "alpha-llm", "zeta-chat"), rankedChatOnly.map { it.id })
        assertTrue(rankedChatOnly[0].isRecommended)
        assertFalse(rankedChatOnly[2].isRecommended)

        val rawAll = UniversalModelDetector.filterAndRankModels(
            listOf("whisper-1", "gemini-2.5-flash"),
            showAllRaw = true
        )
        assertEquals(2, rawAll.size)
    }

    @Test
    fun `elevenLabsClient cleans proxy urls cleanly`() {
        assertEquals("https://api.elevenlabs.io", ElevenLabsClient.cleanTtsBaseUrl("  "))
        assertEquals("https://tts-proxy.example.com", ElevenLabsClient.cleanTtsBaseUrl("https://tts-proxy.example.com/v1/"))
        assertEquals("https://tts-proxy.example.com", ElevenLabsClient.cleanTtsBaseUrl("https://tts-proxy.example.com/"))
    }

    @Test
    fun `pixelCutawayExplainer supports all 6 stages pointer callouts and 270x480 coordinate normalization`() {
        assertEquals(6, CutawayStage.entries.size)
        assertTrue(AnimationStyle.PIXEL_CUTAWAY_EXPLAINER.usesNetworkArt)
        assertEquals("16-BIT PIXEL", AnimationStyle.PIXEL_CUTAWAY_EXPLAINER.badge)
        assertEquals(270, PixelCutawayRenderer.PIXEL_WIDTH)
        assertEquals(480, PixelCutawayRenderer.PIXEL_HEIGHT)

        assertEquals(CutawayStage.IMMUNE_CLEANUP, CutawayStage.fromRaw("immune_cleanup"))
        assertEquals(
            CutawayStage.STRAND_AND_VESSEL_REBUILD,
            CutawayStage.fromRaw("STRAND_AND_VESSEL_REBUILD")
        )
        assertEquals(null, CutawayStage.fromRaw("unknown_stage"))

        // Coordinate normalization across [0..1], 270x480, and 1080x1920 spaces
        assertEquals(135f to 240f, PixelCutawayRenderer.normalizeCalloutPoint(0.5f, 0.5f))
        assertEquals(140f to 220f, PixelCutawayRenderer.normalizeCalloutPoint(140f, 220f))
        assertEquals(135f to 200f, PixelCutawayRenderer.normalizeCalloutPoint(540f, 800f))

        val explicitScene = SceneScript(
            sceneId = 3,
            tagalogNarration = "Dumating na ang mga white blood cells para linisin ang sugat.",
            backgroundPrompt = "pixel cross-section",
            cameraMotion = CameraMotion.STATIC,
            actorRole = ActorRole.NARRATOR,
            actorAction = ActorAction.TALK_BOUNCE,
            worldFx = WorldFx.NONE,
            cutawayStage = CutawayStage.CELL_MIGRATION_CLOSE,
            pointerCallouts = listOf(
                PointerCallout("  new blood vessels ", 150f, 210f),
                PointerCallout("HEALED", 135f, 120f),
                PointerCallout("IGNORED THIRD", 100f, 100f)
            )
        )
        assertEquals(CutawayStage.CELL_MIGRATION_CLOSE, ProceduralPuppetEngine.resolveCutawayStage(explicitScene))
        val resolvedCallouts = ProceduralPuppetEngine.resolvePointerCallouts(explicitScene)
        assertEquals(2, resolvedCallouts.size)
        assertEquals("NEW BLOOD VESSELS", resolvedCallouts[0].label)
        assertEquals("HEALED", resolvedCallouts[1].label)

        val inferredImmuneScene = SceneScript(
            sceneId = 1,
            tagalogNarration = "Pumasok ang white blood cells sa cross-section.",
            backgroundPrompt = "pixel skin",
            cameraMotion = CameraMotion.STATIC,
            actorRole = ActorRole.NARRATOR,
            actorAction = ActorAction.TALK_BOUNCE,
            worldFx = WorldFx.NONE
        )
        assertEquals(CutawayStage.IMMUNE_CLEANUP, ProceduralPuppetEngine.resolveCutawayStage(inferredImmuneScene))
        assertEquals(
            "WHITE BLOOD CELLS",
            ProceduralPuppetEngine.resolvePointerCallouts(inferredImmuneScene).first().label
        )

        val prompt = BuiltInPrompts.composeSystemPrompt("Base", 30, AnimationStyle.PIXEL_CUTAWAY_EXPLAINER)
        assertTrue(prompt.contains("\"cutaway_stage\""))
        assertTrue(prompt.contains("\"cutaway_subject\""))
        assertTrue(prompt.contains("\"pointer_callouts\""))
        assertTrue(prompt.contains("STRAND_AND_VESSEL_REBUILD"))
        assertTrue(prompt.contains("SURFACE_HEALED_ZOOM_OUT"))
    }

    @Test
    fun `v5MultiEnvironmentPollinationsCharacterCustomizationCutawaysAndMetaphorsWork`() {
        // 1. Multi-Environment resolution + Pollinations keyless fallback config
        assertEquals(11, EnvironmentTheme.entries.size)
        assertEquals(
            EnvironmentTheme.BRIGHT_MORNING_KITCHEN,
            EnvironmentRenderer.resolveEnvironmentTheme(
                prompt = "bright sunny morning kitchen with coffee cup on wooden table",
                explicitTheme = null,
                style = AnimationStyle.PAPER_DIORAMA
            )
        )
        assertEquals(
            EnvironmentTheme.SUNNY_MARKET_STREET,
            EnvironmentRenderer.resolveEnvironmentTheme(
                prompt = "busy palengke street with sari-sari store and sunshine",
                explicitTheme = null,
                style = AnimationStyle.FLAT_VECTOR
            )
        )
        assertEquals(
            EnvironmentTheme.COZY_BEDROOM_MIDNIGHT,
            EnvironmentRenderer.resolveEnvironmentTheme(
                prompt = "late night bedroom doomscrolling",
                explicitTheme = null,
                style = AnimationStyle.DARK_SUSPENSE
            )
        )
        assertEquals(
            EnvironmentTheme.BANK_VAULT_FINANCE,
            EnvironmentRenderer.resolveEnvironmentTheme(
                prompt = "kitchen table",
                explicitTheme = EnvironmentTheme.BANK_VAULT_FINANCE,
                style = AnimationStyle.PAPER_DIORAMA
            )
        )
        assertEquals(25_000L, AiHordeImageClient.POLL_TIMEOUT_MS)
        assertEquals(20L, AiHordeImageClient.POLLINATIONS_TIMEOUT_SEC)
        val pollUrl = AiHordeImageClient.buildPollinationsUrl("sunny pinoy kitchen")
        assertTrue(pollUrl.startsWith("https://image.pollinations.ai/prompt/"))
        assertTrue(pollUrl.contains("width=540&height=960&model=flux&nologo=true"))

        // 2. Removed obsolete floating sky FX safely map to NONE + new hand props work
        assertEquals(4, WorldFx.entries.size)
        assertEquals(WorldFx.NONE, WorldFx.fromRaw("FLOATING_HOUSES"))
        assertEquals(WorldFx.NONE, WorldFx.fromRaw("GIANT_HAND_GRAB"))
        assertEquals(WorldFx.NONE, WorldFx.fromRaw("EGG_CRACK"))
        assertEquals(HandProp.COFFEE_CUP, HandProp.fromRaw("coffee_cup"))
        assertEquals(HandProp.BASKET_GOODS, HandProp.fromRaw("EGG_BASKET"))

        // 3. Character appearance customization & pure-Kotlin hex color parser
        assertEquals(2, CharacterGender.entries.size)
        assertEquals(4, CharacterAge.entries.size)
        val tinderaScene = SceneScript(
            sceneId = 1,
            tagalogNarration = "Ang sikreto ng matandang tindera sa palengke tuwing umaga.",
            backgroundPrompt = "palengke morning",
            cameraMotion = CameraMotion.STATIC,
            actorRole = ActorRole.HERO,
            actorAction = ActorAction.TALK_BOUNCE,
            worldFx = WorldFx.NONE
        )
        assertEquals(CharacterGender.FEMALE, ProceduralPuppetEngine.resolveCharacterGender(tinderaScene))
        assertEquals(CharacterAge.ELDERLY, ProceduralPuppetEngine.resolveCharacterAge(tinderaScene))
        assertEquals(0xFFE11D48.toInt(), ProceduralPuppetEngine.parseHexColorOrNull("#E11D48"))
        assertEquals(null, ProceduralPuppetEngine.parseHexColorOrNull("invalid_hex"))

        // 4. 6-subject dynamic 16-bit cutaway resolution & pointer callouts
        assertEquals(6, CutawaySubject.entries.size)
        val brainScene = SceneScript(
            sceneId = 2,
            tagalogNarration = "Ano ang nangyayari sa utak at neuron mo kapag puro doomscrolling?",
            backgroundPrompt = "brain synapse cutaway",
            cameraMotion = CameraMotion.STATIC,
            actorRole = ActorRole.NARRATOR,
            actorAction = ActorAction.TALK_BOUNCE,
            worldFx = WorldFx.NONE,
            cutawayStage = CutawayStage.STRAND_AND_VESSEL_REBUILD
        )
        val resolvedBrainSubject = ProceduralPuppetEngine.resolveCutawaySubject(brainScene)
        assertEquals(CutawaySubject.BRAIN_NEURON_SIGNAL, resolvedBrainSubject)
        assertEquals(
            "SYNAPSE BRIDGE",
            ProceduralPuppetEngine.resolvePointerCallouts(brainScene, CutawayStage.STRAND_AND_VESSEL_REBUILD, resolvedBrainSubject)
                .first().label
        )

        // 5. Expanded 14 MetaphorVisuals & topic matching
        val moneyTreeScene = SceneScript(
            sceneId = 1,
            tagalogNarration = "Paano nagiging puno ng pera ang tamang tanim ng ipon?",
            backgroundPrompt = "bright office",
            cameraMotion = CameraMotion.STATIC,
            actorRole = ActorRole.CHICKEN_MENTOR,
            actorAction = ActorAction.POINT_STICK,
            worldFx = WorldFx.COIN_RAIN
        )
        assertEquals(
            MetaphorVisual.GROWING_MONEY_TREE,
            MetaphorVisualRenderer.resolveMetaphorVisual(moneyTreeScene, AnimationStyle.VIRAL_2D_EXPLAINER)
        )
        val rocketScene = moneyTreeScene.copy(
            tagalogNarration = "Paano gumagana ang Rule of 72 at compound interest chart?"
        )
        assertEquals(
            MetaphorVisual.ROCKET_GROWTH_CHART,
            MetaphorVisualRenderer.resolveMetaphorVisual(rocketScene, AnimationStyle.VIRAL_2D_EXPLAINER)
        )
        assertTrue(MetaphorVisualRenderer.isDiagramAlongsideCharacter(MetaphorVisual.CRACKING_EGG_CHICK))
        assertTrue(MetaphorVisualRenderer.isDiagramAlongsideCharacter(MetaphorVisual.ROCKET_GROWTH_CHART))
        assertTrue(MetaphorVisualRenderer.isDiagramAlongsideCharacter(MetaphorVisual.DOMINO_CHAIN_REACTION))

        // 6. V5 Enum fromRaw, JsonSanitizer extraction & BuiltInPrompts schema checks (JVM-safe)
        assertEquals(EnvironmentTheme.BRIGHT_MORNING_KITCHEN, EnvironmentTheme.fromRaw("bright_morning_kitchen"))
        assertEquals(CutawaySubject.BATTERY_CIRCUIT_CHARGE, CutawaySubject.fromRaw("battery_circuit_charge"))
        assertEquals(CharacterGender.FEMALE, CharacterGender.fromRaw("female"))
        assertEquals(CharacterAge.ELDERLY, CharacterAge.fromRaw("elderly"))
        assertEquals(MetaphorVisual.CRACKING_EGG_CHICK, MetaphorVisual.fromRaw("cracking_egg_chick"))
        assertEquals(MetaphorVisual.ROCKET_GROWTH_CHART, MetaphorVisual.fromRaw("ROCKET_GROWTH_CHART"))
        assertEquals(MetaphorVisual.DOMINO_CHAIN_REACTION, MetaphorVisual.fromRaw("domino_chain_reaction"))

        val rawJson = """
            ```json
            [
              {
                "scene_id": 1,
                "tagalog_narration": "Huwag umasa sa iisang kita lang dahil mabilis magbago ang buhay.",
                "background_prompt": "bright morning kitchen with sunlight through window",
                "camera_motion": "ZOOM_IN",
                "actor_role": "HERO",
                "actor_action": "TALK_BOUNCE",
                "world_fx": "COIN_RAIN",
                "environment_theme": "BRIGHT_MORNING_KITCHEN",
                "cutaway_subject": "BATTERY_CIRCUIT_CHARGE",
                "character_gender": "FEMALE",
                "character_age": "YOUNG",
                "outfit_color_hex": "#E11D48",
                "hand_prop": "COFFEE_CUP",
                "metaphor_visual": "CRACKING_EGG_CHICK"
              }
            ]
            ```
        """.trimIndent()
        val extracted = JsonSanitizer.extractJsonArrayText(rawJson)
        assertTrue(extracted.contains("\"environment_theme\": \"BRIGHT_MORNING_KITCHEN\""))
        assertTrue(extracted.contains("\"cutaway_subject\": \"BATTERY_CIRCUIT_CHARGE\""))
        assertTrue(extracted.contains("\"character_gender\": \"FEMALE\""))
        assertTrue(extracted.contains("\"outfit_color_hex\": \"#E11D48\""))
        assertTrue(extracted.contains("\"hand_prop\": \"COFFEE_CUP\""))

        val v5Prompt = BuiltInPrompts.composeSystemPrompt("Base", 30, AnimationStyle.VIRAL_2D_EXPLAINER)
        assertTrue(v5Prompt.contains("\"environment_theme\""))
        assertTrue(v5Prompt.contains("\"character_gender\""))
        assertTrue(v5Prompt.contains("\"character_age\""))
        assertTrue(v5Prompt.contains("\"outfit_color_hex\""))
        assertTrue(v5Prompt.contains("COFFEE_CUP"))
        assertTrue(v5Prompt.contains("BASKET_GOODS"))
        assertTrue(v5Prompt.contains("CRACKING_EGG_CHICK"))
        assertFalse(v5Prompt.contains("FLOATING_HOUSES"))
        assertFalse(v5Prompt.contains("GIANT_HAND_GRAB"))
    }

    @Test
    fun `exactTargetDurationLockSystemCalculatesBudgetGuardsLengthAlignsPausesAndClampsSpeed`() {
        // 1. Strict Spoken-Character & Scene Count Formula
        val budget30 = LlmDirectorClient.calculateDurationBudget(30)
        assertEquals(6, budget30.targetSceneCount) // (30 / 4.8).roundToInt() = 6
        assertEquals(423, budget30.targetSpokenChars) // ((30 - 6*0.45) * 15.5).roundToInt() = 423
        assertEquals(381, budget30.minChars)
        assertEquals(457, budget30.maxChars)

        val budget60 = LlmDirectorClient.calculateDurationBudget(60)
        assertEquals(13, budget60.targetSceneCount) // (60 / 4.8).roundToInt() = 13
        assertEquals(839, budget60.targetSpokenChars) // ((60 - 13*0.45) * 15.5).roundToInt() = 839

        val contract = LlmDirectorClient.buildDurationContractBlock(budget30)
        assertTrue(contract.contains("MANDATORY DURATION & LENGTH CONTRACT"))
        assertTrue(contract.contains("EXACTLY 6 scenes"))
        assertTrue(contract.contains("381 and 457"))

        // 2. Pre-TTS expansion merge & overshoot trimming guard (preserving closing scene)
        val shortScenes = (1..3).map { idx ->
            SceneScript(
                sceneId = idx,
                tagalogNarration = "Maikling eksena $idx tungkol sa ipon.",
                backgroundPrompt = "bright office",
                cameraMotion = CameraMotion.STATIC,
                actorRole = ActorRole.HERO,
                actorAction = ActorAction.TALK_BOUNCE,
                worldFx = WorldFx.NONE
            )
        }
        val extraScenes = (1..3).map { idx ->
            SceneScript(
                sceneId = idx,
                tagalogNarration = "Mas detalyadong paliwanag $idx kung paano nakakatulong ang pagtitipid araw-araw.",
                backgroundPrompt = "sunny market",
                cameraMotion = CameraMotion.ZOOM_IN,
                actorRole = ActorRole.CHICKEN_MENTOR,
                actorAction = ActorAction.POINT_STICK,
                worldFx = WorldFx.COIN_RAIN
            )
        }
        val merged = LlmDirectorClient.mergeExpandedScenes(shortScenes, extraScenes, budget30)
        assertEquals(6, merged.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), merged.map { it.sceneId })

        val oversizedScenes = (1..10).map { idx ->
            SceneScript(
                sceneId = idx,
                tagalogNarration = if (idx == 10) {
                    "Kaya simula ngayon, magtabi agad ng bente porsyento bago gumastos."
                } else {
                    "Napakahabang paliwanag bilang $idx tungkol sa maling diskarte sa pera at gastos ng marami sa atin ngayon."
                },
                backgroundPrompt = "office",
                cameraMotion = CameraMotion.STATIC,
                actorRole = ActorRole.HERO,
                actorAction = ActorAction.TALK_BOUNCE,
                worldFx = WorldFx.NONE
            )
        }
        val trimmed = LlmDirectorClient.trimExcessScenesPreservingEnding(oversizedScenes, budget30)
        assertTrue(trimmed.size < oversizedScenes.size)
        assertTrue(LlmDirectorClient.totalSpokenChars(trimmed) <= (budget30.maxChars * 1.15).toInt())
        assertEquals(oversizedScenes.last().tagalogNarration, trimmed.last().tagalogNarration)
        assertEquals((1..trimmed.size).toList(), trimmed.map { it.sceneId })

        // 3. Smart Inter-Scene Breathing Pauses (350..850ms) & <= 1200ms Outro Hold
        val plan = AudioPcmStitcher.computeBreathingPauses(
            sceneCount = 6,
            totalSpeechMs = 26_600.0,
            targetDurationMs = 30_000.0
        )
        assertEquals(5, plan.transitionPausesMs.size)
        plan.transitionPausesMs.forEach { pause ->
            assertTrue("Expected breathing pause in 350..850ms, got $pause", pause in 350.0..850.0)
        }
        assertTrue("Expected outro hold <= 1200ms, got ${plan.outroHoldMs}", plan.outroHoldMs in 0.0..1200.0)
        assertEquals(3400.0, plan.totalAddedMs, 1.0)

        // End-to-end stitchScenes alignment within +/- 1.5s of 30s target
        val sceneSpeechBytes = ((26_600.0 / 6.0 / 1000.0) * 44100.0).toInt() * 2
        val pcmChunks = List(6) { ByteArray(sceneSpeechBytes) }
        val stitched = AudioPcmStitcher.stitchScenes(
            decodedPcmPerScene = pcmChunks,
            localTimingsPerScene = List(6) { emptyList() },
            narrationPerScene = List(6) { "Isang maikling Tagalog sentence dito." },
            targetDurationMs = 30_000.0
        )
        assertTrue(
            "Expected aligned PCM duration within +/-1500ms of 30000ms, got ${stitched.totalDurationMs}",
            abs(stitched.totalDurationMs - 30_000.0) <= 1500.0
        )

        // 4. Narration Speed bounds & StudioSettings default
        assertEquals(1.0f, StudioSettings().narrationSpeed, 0.001f)
        assertEquals(0.85, ElevenLabsClient.clampNarrationSpeed(0.50f), 0.001)
        assertEquals(1.00, ElevenLabsClient.clampNarrationSpeed(1.00f), 0.001)
        assertEquals(1.15, ElevenLabsClient.clampNarrationSpeed(1.50f), 0.001)
    }

    @Test
    fun `v6TopicFirstDynamicEngineWardrobeCutawayAndDiagramsWork`() {
        // 1. New V6 enums & backward-compatible aliases
        assertEquals(7, CharacterOutfit.entries.size)
        assertEquals(4, FlowDirection.entries.size)
        assertEquals(6, DynamicDiagramType.entries.size)
        assertEquals(4, CharacterAge.entries.size)
        assertEquals(CharacterAge.YOUNG_ADULT, CharacterAge.fromRaw("YOUNG"))
        assertEquals(CharacterAge.MIDDLE_AGED, CharacterAge.fromRaw("ADULT"))
        assertEquals(CharacterAge.CHILD, CharacterAge.fromRaw("TEEN"))
        assertEquals(CharacterOutfit.MEDICAL_SCRUBS, CharacterOutfit.fromRaw("medical_scrubs"))
        assertEquals(CharacterOutfit.WORK_UNIFORM, CharacterOutfit.fromRaw("work_uniform"))
        assertEquals(FlowDirection.RADIAL_BURST, FlowDirection.fromRaw("radial_burst"))
        assertEquals(DynamicDiagramType.BEFORE_AFTER_SPLIT, DynamicDiagramType.fromRaw("before_after_split"))
        assertEquals(DynamicDiagramType.SCALE_WEIGH_OFF, DynamicDiagramType.fromRaw("SCALE_WEIGH_OFF"))
        assertEquals(HandProp.BOOK_NOTEBOOK, HandProp.fromRaw("book_notebook"))
        assertEquals(HandProp.TOOL_WRENCH, HandProp.fromRaw("tool_wrench"))
        assertEquals(EnvironmentTheme.HOSPITAL_CLINIC, EnvironmentTheme.fromRaw("hospital_clinic"))

        // 2. Topic-First Decoupling: neutral topics do NOT get forced skin wounds or protein chains
        val studyScene = SceneScript(
            sceneId = 1,
            tagalogNarration = "Paano gumawa ng maayos na study schedule ang isang estudyante?",
            backgroundPrompt = "bright classroom chalkboard",
            cameraMotion = CameraMotion.STATIC,
            actorRole = ActorRole.HERO,
            actorAction = ActorAction.POINT_STICK,
            worldFx = WorldFx.NONE
        )
        assertEquals(null, ProceduralPuppetEngine.resolveCutawaySubjectOrNull(studyScene))
        assertEquals(MetaphorVisual.NONE, MetaphorVisualRenderer.resolveMetaphorVisual(studyScene, AnimationStyle.VIRAL_2D_EXPLAINER))
        assertEquals(CharacterOutfit.STUDENT_UNIFORM, ProceduralPuppetEngine.resolveCharacterOutfit(studyScene))
        assertEquals(CharacterAge.YOUNG_ADULT, ProceduralPuppetEngine.resolveCharacterAge(studyScene))

        // Hospital/doctor scene routes to HOSPITAL_CLINIC + MEDICAL_SCRUBS
        assertEquals(
            EnvironmentTheme.HOSPITAL_CLINIC,
            EnvironmentRenderer.resolveEnvironmentTheme(
                prompt = "modern hospital clinic examination room with doctor",
                explicitTheme = null,
                style = AnimationStyle.PAPER_DIORAMA
            )
        )
        val doctorScene = studyScene.copy(
            tagalogNarration = "Payo ng doktor sa klinika tungkol sa tamang pag-inom ng tubig.",
            backgroundPrompt = "hospital clinic room"
        )
        assertEquals(CharacterOutfit.MEDICAL_SCRUBS, ProceduralPuppetEngine.resolveCharacterOutfit(doctorScene))

        // 3. DynamicDiagramRenderer routes data-driven diagrams cleanly
        val compareScene = studyScene.copy(
            leftLabel = "WALANG PLANO",
            rightLabel = "MAY SCHEDULE"
        )
        assertEquals(
            DynamicDiagramType.BEFORE_AFTER_SPLIT,
            DynamicDiagramRenderer.resolveDiagramType(compareScene, AnimationStyle.VIRAL_2D_EXPLAINER)
        )
        assertTrue(DynamicDiagramRenderer.isDiagramAlongsideCharacter(DynamicDiagramType.BEFORE_AFTER_SPLIT))

        val stepsScene = studyScene.copy(
            step1 = "Gumawa ng listahan",
            step2 = "Mag-focus 25 mins",
            step3 = "Magpahinga 5 mins"
        )
        assertEquals(
            DynamicDiagramType.STEP_FLOW_ARROWS,
            DynamicDiagramRenderer.resolveDiagramType(stepsScene, AnimationStyle.PAPER_DIORAMA)
        )

        val meterScene = studyScene.copy(
            hudLabel = "FOCUS SCORE",
            hudFromValue = 25,
            hudToValue = 92
        )
        assertEquals(
            DynamicDiagramType.METER_GAUGE_SHIFT,
            DynamicDiagramRenderer.resolveDiagramType(meterScene, AnimationStyle.VIRAL_2D_EXPLAINER)
        )
        assertFalse(DynamicDiagramRenderer.isDiagramAlongsideCharacter(DynamicDiagramType.SUNBURST_HERO_CALLOUT))

        // 4. BuiltInPrompts Topic-First Decoupling & V6 JSON schema verification
        val v6Prompt = BuiltInPrompts.composeSystemPrompt("Base", 30, AnimationStyle.PIXEL_CUTAWAY_EXPLAINER)
        assertTrue(v6Prompt.contains("TOPIC-FIRST DECOUPLING CONTRACT"))
        assertTrue(v6Prompt.contains("\"character_outfit\""))
        assertTrue(v6Prompt.contains("\"hair_color_hex\""))
        assertTrue(v6Prompt.contains("\"cutaway_diagram_prompt\""))
        assertTrue(v6Prompt.contains("\"flow_direction\""))
        assertTrue(v6Prompt.contains("\"diagram_type\""))
        assertTrue(v6Prompt.contains("\"left_label\""))
        assertTrue(v6Prompt.contains("\"step_1\""))
        assertTrue(v6Prompt.contains("HOSPITAL_CLINIC"))
    }

    @Test
    fun `v7ProductionReadyStudioDefaultsErrorReportingAndFallbackLogicWork`() {
        // 1. StudioSettings defaults & credential trimming
        val defaults = StudioSettings()
        assertEquals("N2IVS1w4EtoT3dr4eOWO", defaults.selectedVoiceId)
        assertEquals("eleven_multilingual_v2", defaults.ttsModelId)
        assertEquals("https://api.elevenlabs.io", defaults.ttsBaseUrl)
        assertEquals(1.0f, defaults.narrationSpeed, 0.001f)

        val dirtySettings = StudioSettings(
            llmBaseUrl = "  https://api.deepseek.com \n",
            llmApiKey = "\r sk-test-123 \n",
            selectedModelId = " deepseek-chat \n",
            ttsBaseUrl = " https://api.elevenlabs.io \n",
            ttsModelId = " eleven_flash_v2_5 \n",
            selectedVoiceId = " N2IVS1w4EtoT3dr4eOWO \n"
        )
        assertEquals("https://api.deepseek.com", dirtySettings.effectiveLlmBaseUrl)
        assertEquals("sk-test-123", dirtySettings.effectiveLlmApiKey)
        assertEquals("deepseek-chat", dirtySettings.effectiveSelectedModelId)
        assertEquals("https://api.elevenlabs.io", dirtySettings.effectiveTtsBaseUrl)
        assertEquals("eleven_flash_v2_5", dirtySettings.effectiveTtsModelId)
        assertEquals("N2IVS1w4EtoT3dr4eOWO", dirtySettings.effectiveVoiceId)

        // 2. LLM timeouts & max_tokens + exact HTTP error formatting
        assertEquals(30L, LlmDirectorClient.CONNECT_TIMEOUT_SEC)
        assertEquals(90L, LlmDirectorClient.READ_TIMEOUT_SEC)
        assertEquals(30L, LlmDirectorClient.WRITE_TIMEOUT_SEC)
        assertEquals(4096, LlmDirectorClient.MAX_TOKENS)

        val llmErr = LlmHttpException(
            statusCode = 401,
            endpointUrl = "https://api.deepseek.com/chat/completions",
            modelId = "deepseek-chat",
            rawBody = "{\"error\":\"Invalid API key\"}"
        )
        assertTrue(llmErr.message.orEmpty().contains("401"))
        assertTrue(llmErr.message.orEmpty().contains("https://api.deepseek.com/chat/completions"))
        assertTrue(llmErr.message.orEmpty().contains("deepseek-chat"))
        assertTrue(llmErr.message.orEmpty().contains("Invalid API key"))

        // 3. ElevenLabs 422 model detection, payload model_id, proportional timings & error formatting
        assertEquals("N2IVS1w4EtoT3dr4eOWO", ElevenLabsClient.DEFAULT_VOICE_ID)
        assertEquals("eleven_multilingual_v2", ElevenLabsClient.DEFAULT_TTS_MODEL_ID)
        assertEquals("eleven_flash_v2_5", ElevenLabsClient.FALLBACK_TTS_MODEL_ID)

        assertTrue(
            ElevenLabsClient.isUnsupportedModel422(
                "{\"detail\":{\"status\":\"model_id_unsupported\",\"message\":\"The model_id is unsupported\"}}"
            )
        )
        assertFalse(ElevenLabsClient.isUnsupportedModel422("{\"detail\":\"Invalid text input\"}"))

        assertEquals("eleven_flash_v2_5", ElevenLabsClient.cleanCredential("  eleven_flash_v2_5\r\n "))
        assertEquals("https://tts-proxy.example.com", ElevenLabsClient.cleanTtsBaseUrl(" https://tts-proxy.example.com/v1/\n"))
        assertEquals(1.05, ElevenLabsClient.clampNarrationSpeed(1.05f), 0.001)

        val timings = ElevenLabsClient.buildProportionalWordTimings(
            displayWords = listOf("Kamusta", "kaibigan", "ko!"),
            actualDurationMs = 3000.0
        )
        assertEquals(3, timings.size)
        assertTrue(timings.first().startMs >= 0.0)
        assertTrue(timings.last().endMs <= 3000.0)
        assertTrue(timings[0].endMs <= timings[1].startMs + 0.01)
        assertTrue(timings[1].endMs <= timings[2].startMs + 0.01)

        val ttsErr = ElevenLabsHttpException(
            statusCode = 422,
            endpointUrl = "https://api.elevenlabs.io/v1/text-to-speech/N2IVS1w4EtoT3dr4eOWO",
            voiceId = "N2IVS1w4EtoT3dr4eOWO",
            modelId = "eleven_multilingual_v2",
            rawBody = "{\"detail\":\"Voice limit reached\"}"
        )
        assertTrue(ttsErr.message.orEmpty().contains("422"))
        assertTrue(ttsErr.message.orEmpty().contains("N2IVS1w4EtoT3dr4eOWO"))
        assertTrue(ttsErr.message.orEmpty().contains("eleven_multilingual_v2"))
        assertTrue(ttsErr.message.orEmpty().contains("Voice limit reached"))
    }

    @Test
    fun `zeroShortcutAntiLazinessQualityGateWorksAcrossScriptArtPuppetCameraAndTts`() {
        // 1. Two-Stage Director Prompts & Spoken-Char 94% Gate
        val budget30 = LlmDirectorClient.calculateDurationBudget(30)
        assertEquals(6, budget30.targetSceneCount)
        assertEquals(398, LlmDirectorClient.requiredMinSpokenChars(budget30)) // (423 * 0.94).roundToInt() = 398
        assertEquals(2, LlmDirectorClient.MAX_REPAIR_PASSES)

        val blueprintPrompt = LlmDirectorClient.buildNarrativeBlueprintPrompt(
            topic = "Paano mag-ipon ng ₱1,000 gamit ang 50-30-20 rule",
            durationSeconds = 30,
            style = AnimationStyle.PAPER_DIORAMA,
            budget = budget30
        )
        assertTrue(blueprintPrompt.contains("EXACTLY 6 scenes"))
        assertTrue(blueprintPrompt.contains("15.5 Tagalog spoken chars/sec"))
        assertTrue(blueprintPrompt.contains(">= 11 Tagalog words"))

        val prodPrompt = LlmDirectorClient.buildSceneProductionPrompt(
            topic = "Paano mag-ipon ng ₱1,000 gamit ang 50-30-20 rule",
            durationSeconds = 30,
            style = AnimationStyle.PAPER_DIORAMA,
            budget = budget30,
            blueprint = "1. Hook\n2. Explain"
        )
        assertTrue(prodPrompt.contains("LOCKED PASS 1 NARRATIVE BLUEPRINT"))
        assertTrue(prodPrompt.contains("ZERO shortcuts"))

        // 2. Kotlin Anti-Laziness Validator rejects short narration, vague visual fillers, missing dimensions & consecutive repeats
        val goodVisual = "Young Filipino worker leaning forward at wooden market counter while smiling with bright eyes, holding a steaming coffee cup in warm golden morning sunlight."
        val lazyScenes = listOf(
            SceneScript(
                sceneId = 1,
                tagalogNarration = "Maikli ito.", // < 11 words and < 60 chars
                backgroundPrompt = "generic simple background etc",
                visualDescription = "A person in a generic simple background with coffee, etc.",
                cameraMotion = CameraMotion.ZOOM_IN,
                actorRole = ActorRole.HERO,
                actorAction = ActorAction.TALK_BOUNCE,
                worldFx = WorldFx.NONE,
                characterPose = CharacterPose.POINT_AND_GESTURE,
                shotFraming = ShotFraming.MEDIUM_WAIST_UP
            ),
            SceneScript(
                sceneId = 2,
                tagalogNarration = "Alam mo ba kung bakit nauubos agad ang sahod mo tuwing kinsenas ng buwan?",
                backgroundPrompt = goodVisual,
                visualDescription = goodVisual,
                cameraMotion = CameraMotion.ZOOM_IN, // repeats Scene 1
                actorRole = ActorRole.HERO,
                actorAction = ActorAction.TALK_BOUNCE,
                worldFx = WorldFx.NONE,
                characterPose = CharacterPose.POINT_AND_GESTURE, // repeats Scene 1
                shotFraming = ShotFraming.MEDIUM_WAIST_UP // repeats Scene 1
            )
        )
        val badReport = LlmDirectorClient.validateSceneQuality(lazyScenes, budget30)
        assertFalse(badReport.isValid)
        assertEquals(listOf(3, 4, 5, 6), badReport.missingSceneIds)
        val s1Reasons = badReport.sceneIssues.first { it.sceneId == 1 }.reasons.joinToString(" | ")
        assertTrue(s1Reasons.contains("words (minimum 11)"))
        assertTrue(s1Reasons.contains("chars (minimum 60)"))
        assertTrue(s1Reasons.contains("vague filler"))
        val s2Reasons = badReport.sceneIssues.first { it.sceneId == 2 }.reasons.joinToString(" | ")
        assertTrue(s2Reasons.contains("camera_motion"))
        assertTrue(s2Reasons.contains("character_pose"))
        assertTrue(s2Reasons.contains("shot_framing"))

        // 3. Targeted repair prompt & mergeRepairedScenes produces a valid 6-scene script >= 94% target chars
        val repairPrompt = LlmDirectorClient.buildTargetedRepairPrompt(
            workingScenes = lazyScenes,
            targetSceneIds = listOf(1, 2, 3, 4, 5, 6),
            report = badReport,
            budget = budget30,
            blueprint = "1-6"
        )
        assertTrue(repairPrompt.contains("TARGETED SCENE QUALITY REPAIR PASS"))
        assertTrue(repairPrompt.contains("Scene 1 failure reason(s)"))

        val cameras = listOf(
            CameraMotion.ZOOM_IN,
            CameraMotion.PAN_LEFT,
            CameraMotion.ZOOM_OUT,
            CameraMotion.PAN_RIGHT,
            CameraMotion.TRACKING_FOLLOW,
            CameraMotion.IMPACT_SHAKE
        )
        val poses = listOf(
            CharacterPose.THREE_QUARTER_EXPLAIN,
            CharacterPose.WALK_ACROSS_LEFT_TO_RIGHT,
            CharacterPose.SIT_SLUMPED,
            CharacterPose.POINT_AND_GESTURE,
            CharacterPose.SHOCK_AIRBORNE_RECOIL,
            CharacterPose.CELEBRATE_JUMP
        )
        val framings = listOf(
            ShotFraming.CLOSE_UP_FACE,
            ShotFraming.WIDE_FULL_BODY,
            ShotFraming.MEDIUM_WAIST_UP,
            ShotFraming.RULE_OF_THIRDS_LEFT,
            ShotFraming.RULE_OF_THIRDS_RIGHT,
            ShotFraming.CLOSE_UP_FACE
        )
        val repairedAll = (1..6).map { idx ->
            SceneScript(
                sceneId = idx,
                tagalogNarration = "Alam mo ba kung paano nakakaipon ng isang libong piso linggo-linggo ang masipag na tindera?",
                backgroundPrompt = goodVisual,
                visualDescription = goodVisual,
                cameraMotion = cameras[idx - 1],
                actorRole = ActorRole.HERO,
                actorAction = ActorAction.TALK_BOUNCE,
                worldFx = WorldFx.NONE,
                characterPose = poses[idx - 1],
                shotFraming = framings[idx - 1]
            )
        }
        val merged = LlmDirectorClient.mergeRepairedScenes(lazyScenes, repairedAll, budget30.targetSceneCount)
        val cleanReport = LlmDirectorClient.validateSceneQuality(merged, budget30)
        assertTrue("Expected repaired script to pass quality gate, got: ${cleanReport.summaryMessage()}", cleanReport.isValid)

        // 4. Verified Multi-Retry Art Pipeline constants & validators
        assertEquals(2, SceneArtGenerator.MAX_CONCURRENT_JOBS)
        assertEquals(3, AiHordeImageClient.PRIMARY_MAX_RETRIES)
        assertEquals(listOf(700L, 1600L), AiHordeImageClient.PRIMARY_BACKOFF_MS.toList())
        assertEquals(15_360, MagicByteValidator.MIN_VERIFIED_IMAGE_BYTES)
        assertEquals(480, MagicByteValidator.MIN_VERIFIED_WIDTH_PX)
        assertEquals(800, MagicByteValidator.MIN_VERIFIED_HEIGHT_PX)
        assertFalse(MagicByteValidator.meetsMinimumFileSize(ByteArray(1024)))
        assertTrue(MagicByteValidator.meetsMinimumFileSize(ByteArray(16_000)))
        assertFalse(MagicByteValidator.meetsMinimumDimensions(320, 960))
        assertFalse(MagicByteValidator.meetsMinimumDimensions(540, 600))
        assertTrue(MagicByteValidator.meetsMinimumDimensions(540, 960))

        // 5. Continuous Micro-Acting, Phoneme MouthShape & Non-Static CameraController
        assertEquals(0.35f, ProceduralPuppetEngine.BREATHING_FREQ_HZ, 0.001f)
        assertEquals(2.6f, ProceduralPuppetEngine.EYE_BLINK_PERIOD_SEC, 0.001f)
        assertEquals(MouthShape.CLOSED_M, ProceduralPuppetEngine.resolveMouthShape(false, "alam", 0.5f, 0.9f))
        assertEquals(MouthShape.CLOSED_M, ProceduralPuppetEngine.resolveMouthShape(true, "baha", 0.0f, 0.8f))
        assertEquals(MouthShape.OPEN_WIDE, ProceduralPuppetEngine.resolveMouthShape(true, "baha", 0.3f, 0.8f))
        assertEquals(MouthShape.ROUND_O, ProceduralPuppetEngine.resolveMouthShape(true, "bobo", 0.3f, 0.8f))
        assertEquals(MouthShape.SMILE_TEETH, ProceduralPuppetEngine.resolveMouthShape(true, "bibi", 0.3f, 0.8f))

        val staticScene = repairedAll.first().copy(
            cameraMotion = CameraMotion.STATIC,
            shotType = ShotType.WIDE_STAGE,
            shotFraming = ShotFraming.WIDE_FULL_BODY
        )
        val camEarly = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f, scene = staticScene,
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 0, sceneElapsedSec = 0.2f, sceneDurationSec = 4f, globalTimeSec = 0.2f,
            hasActiveHud = false, isCutaway = false
        )
        val camLate = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f, scene = staticScene,
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 0, sceneElapsedSec = 3.5f, sceneDurationSec = 4f, globalTimeSec = 3.5f,
            hasActiveHud = false, isCutaway = false
        )
        assertTrue(
            "Camera must continuously animate with zero static dead frames",
            abs(camLate.translateX - camEarly.translateX) > 10f && camLate.scale > camEarly.scale
        )

        val panLeft = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f, scene = staticScene.copy(cameraMotion = CameraMotion.PAN_LEFT),
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 0, sceneElapsedSec = 0.2f, sceneDurationSec = 4f, globalTimeSec = 0.2f,
            hasActiveHud = false, isCutaway = false
        )
        val panRight = CameraController.computeCameraTransform(
            width = 1080f, height = 1920f, scene = staticScene.copy(cameraMotion = CameraMotion.PAN_RIGHT),
            style = AnimationStyle.FLAT_VECTOR,
            sceneIndex = 0, sceneElapsedSec = 0.2f, sceneDurationSec = 4f, globalTimeSec = 0.2f,
            hasActiveHud = false, isCutaway = false
        )
        assertTrue("PAN_LEFT and PAN_RIGHT should pan in opposite directions", panLeft.translateX > 0f && panRight.translateX < 0f)

        // 6. Resilient ElevenLabs Chunk Retry & Minimum 600ms PCM Guard
        assertEquals(600.0, ElevenLabsClient.MIN_DECODED_AUDIO_DURATION_MS, 0.01)
        assertEquals(listOf(2000L, 4000L, 8000L), ElevenLabsClient.TRANSIENT_RETRY_BACKOFFS_MS.toList())
        assertTrue(ElevenLabsClient.isTransientHttpStatus(429))
        assertTrue(ElevenLabsClient.isTransientHttpStatus(500))
        assertTrue(ElevenLabsClient.isTransientHttpStatus(503))
        assertFalse(ElevenLabsClient.isTransientHttpStatus(400))
        assertFalse(ElevenLabsClient.isTransientHttpStatus(422))

        val validOneSec = ByteArray(88200) // 44100 samples * 2 bytes = 1000ms
        assertEquals(1000.0, ElevenLabsClient.validateDecodedPcmOrThrow(validOneSec, "https://api.elevenlabs.io", "v1", "m1"), 0.01)

        var emptyRejected = false
        try {
            ElevenLabsClient.validateDecodedPcmOrThrow(ByteArray(0), "https://api.elevenlabs.io", "v1", "m1")
        } catch (_: Exception) {
            emptyRejected = true
        }
        assertTrue(emptyRejected)

        var shortRejected = false
        try {
            ElevenLabsClient.validateDecodedPcmOrThrow(ByteArray(44100), "https://api.elevenlabs.io", "v1", "m1") // 500ms < 600ms
        } catch (_: Exception) {
            shortRejected = true
        }
        assertTrue(shortRejected)
    }

    @Test
    fun `jsonSanitizerUnwrapsObjectWrappersShowsRawSnippetAndDirectorEnforcesJsonObjectMode`() {
        // 1. Ignores [Scene 1], ["tag1", "tag2"], and non-scene object arrays earlier in the response
        val noisyPrefixResponse = """
            [Scene 1] Draft thoughts here.
            ["tag1", "tag2"]
            [{"label": "Intro Card", "weight": 1}]
            {"title": "Budgeting Demo", "tags": ["pinoy", "ipon"], "scenes": [{"scene_id": 1, "tagalog_narration": "Una"}]}
        """.trimIndent()
        val unwrappedScenes = JsonSanitizer.extractJsonArrayText(noisyPrefixResponse)
        assertTrue(unwrappedScenes.startsWith("["))
        assertTrue(unwrappedScenes.endsWith("]"))
        assertTrue(unwrappedScenes.contains("\"scene_id\": 1"))
        assertFalse(unwrappedScenes.contains("\"tags\""))
        assertFalse(unwrappedScenes.contains("Intro Card"))
        assertFalse(unwrappedScenes.contains("Scene 1"))

        // 2. Unwrap {"storyboard": [...]} inside ```json ... ``` fence with [Note] prose outside
        val fencedStoryboard = """
            [Note] Here is the generated storyboard:
            ```json
            {
              "storyboard": [
                {"scene_id": 1, "tagalog_narration": "Alam mo ba ang sikreto sa ipon?"},
                {"scene_id": 2, "tagalog_narration": "Heto ang pangalawang
        """.trimIndent() + "\n```"
        val unwrappedStoryboard = JsonSanitizer.extractJsonArrayText(fencedStoryboard)
        assertTrue(unwrappedStoryboard.startsWith("["))
        assertTrue(unwrappedStoryboard.endsWith("]"))
        assertTrue(unwrappedStoryboard.contains("\"scene_id\": 1"))
        assertFalse(unwrappedStoryboard.contains("\"scene_id\": 2"))

        // 3. Rejects responses containing only string arrays or no scene objects and includes raw snippet
        var stringArrayRejected = false
        try {
            JsonSanitizer.extractJsonArrayText("[Scene 1] Only tags [\"tag1\", \"tag2\"] here.")
        } catch (e: MalformedSceneJsonException) {
            stringArrayRejected = e.message.orEmpty().contains("Raw LLM output:")
        }
        assertTrue(stringArrayRejected)

        val longProse = "Pasensya na, ito ay isang kuwentong walang brackets! " + "X".repeat(300)
        var capturedMsg = ""
        try {
            JsonSanitizer.extractJsonArrayText(longProse)
        } catch (e: MalformedSceneJsonException) {
            capturedMsg = e.message.orEmpty()
        }
        assertTrue(capturedMsg.startsWith("No JSON array brackets found. Raw LLM output: "))
        assertTrue(capturedMsg.contains("Pasensya na, ito ay isang kuwentong walang brackets!"))
        assertTrue(capturedMsg.length <= "No JSON array brackets found. Raw LLM output: ".length + 220)

        // 4. Pass 1 stays strictly plain-text outline; Pass 2 places structured {"scenes": [...]} footer at VERY END
        val customMasterPrompt = "Custom Master Prompt with creative instructions."
        val pass1Sys = LlmDirectorClient.buildBlueprintSystemPrompt(customMasterPrompt)
        assertTrue(pass1Sys.contains("PASS 1 PLAIN TEXT ONLY"))
        assertTrue(pass1Sys.contains("Ignore any JSON output instructions during Pass 1"))

        val budget30 = LlmDirectorClient.calculateDurationBudget(30)
        val pass1User = LlmDirectorClient.buildNarrativeBlueprintPrompt("Ipon", 30, AnimationStyle.PAPER_DIORAMA, budget30)
        assertTrue(pass1User.contains("do NOT output JSON in Pass 1"))

        val expectedFooter = "CRITICAL OUTPUT FORMAT: Return ONLY a raw JSON object {\"scenes\": [ ... ]} with zero markdown fences or commentary."
        assertEquals(expectedFooter, LlmDirectorClient.STRICT_JSON_OUTPUT_FOOTER)

        val composedSys = BuiltInPrompts.composeSystemPrompt(customMasterPrompt, 30, AnimationStyle.PAPER_DIORAMA)
        assertTrue(composedSys.contains("{\"scenes\": [ ... ]}"))
        assertFalse(composedSys.contains("[\"ZOOM_IN\""))
        assertFalse(composedSys.contains("[A]"))
        assertFalse(composedSys.contains("[B]"))

        val pass2Sys = LlmDirectorClient.buildPass2SystemPrompt(composedSys, retryHint = "Retry now.")
        assertTrue(pass2Sys.endsWith(expectedFooter))

        val pass2User = LlmDirectorClient.buildSceneProductionPrompt("Ipon", 30, AnimationStyle.PAPER_DIORAMA, budget30, "1. Beat one")
        assertTrue(pass2User.endsWith(expectedFooter))

        val repairUser = LlmDirectorClient.buildTargetedRepairPrompt(
            workingScenes = emptyList(),
            targetSceneIds = listOf(1),
            report = LlmDirectorClient.validateSceneQuality(emptyList(), budget30),
            budget = budget30,
            blueprint = "1. Beat one"
        )
        assertTrue(repairUser.endsWith(expectedFooter))

        // 5. HTTP 400 response_format fallback detection & reasoning model retry logic
        assertTrue(LlmDirectorClient.isUnsupportedResponseFormatError(400, "{\"error\":\"Unsupported parameter: response_format\"}"))
        assertTrue(LlmDirectorClient.isUnsupportedResponseFormatError(400, "json_object mode not supported by this model"))
        assertFalse(LlmDirectorClient.isUnsupportedResponseFormatError(401, "Invalid response_format"))
        assertFalse(LlmDirectorClient.isUnsupportedResponseFormatError(400, "{\"error\":\"Invalid API key\"}"))

        assertEquals(0.7, LlmDirectorClient.DEFAULT_TEMPERATURE, 0.001)
        assertEquals(0.4, LlmDirectorClient.REASONING_RETRY_TEMPERATURE, 0.001)
        assertTrue(LlmDirectorClient.shouldRetryReasoningFallback("", "Thinking step 1...", 0.7))
        assertFalse(LlmDirectorClient.shouldRetryReasoningFallback("", "Thinking step 1...", 0.4))
        assertFalse(LlmDirectorClient.shouldRetryReasoningFallback("[{\"scene_id\":1}]", "Thinking step 1...", 0.7))
        assertEquals("[{\"scene_id\":1}]", LlmDirectorClient.stripThinkingBlocks("<think>internal</think>\n[{\"scene_id\":1}]"))
    }
}
