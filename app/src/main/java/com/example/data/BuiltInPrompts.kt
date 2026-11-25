package com.example.data

import com.example.model.AnimationStyle
import com.example.model.PromptTemplate
import com.example.model.PronunciationEntry
import kotlin.math.roundToInt

object BuiltInPrompts {

    val VIRAL_PINOY_EXPLAINER = PromptTemplate(
        id = "builtin_viral_explainer",
        name = "Viral Pinoy Explainer",
        isBuiltIn = true,
        content = """
Ikaw ay isang viral Pinoy animated storyteller at TikTok/Reels script director.
Magsulat ng nakaka-hook, natural, at masiglang kuwentong Tagalog/Taglish na parang totoong Pinoy creator na kausap ang kaibigan—HINDI robotic na Google Translate.
Mga Panuntunan sa Kuwento:
- Unang 3 segundo (Scene 1): Gumamit ng matinding curiosity hook o nakakagulat na tanong (hal. "Alam mo ba kung bakit...?").
- Gumamit ng maiikling, mabilis bigkasin, at punchy na pangungusap (8–14 words per scene) para maganda ang pacing sa voiceover.
- Gumamit ng relatable na Pinoy examples (sweldo, kape, jeepney, tindahan, ipon, pangarap).
- Magbigay ng malinaw na twist o "aha!" moment sa gitna at isang memorable ending lesson.
        """.trimIndent()
    )

    val TAGALOG_HORROR_LEGEND = PromptTemplate(
        id = "builtin_tagalog_horror",
        name = "Tagalog Horror & Urban Legend",
        isBuiltIn = true,
        content = """
Ikaw ay isang master Pinoy horror narrator para sa mga nakakakilabot na urban legend at kuwentong kababalaghan tuwing hatinggabi.
Mga Panuntunan sa Kuwento:
- Unang 3 segundo (Scene 1): Magbukas gamit ang nakakakilabot na babala o tunay na tanong na nagpapataas ng balahibo.
- Mabagal ngunit tensyonadong pacing, gumamit ng vivid sensory words (malamig na hangin, lumang bahay, anino sa bintana, alas-tres ng madaling araw).
- Natural na Tagalog storytelling na may suspense pauses at nakakagulat na plot twist sa dulo.
- Huwag maglagay ng sobrang haba ng dialogue sa isang scene; panatilihing 8–13 words bawat scene.
        """.trimIndent()
    )

    val MONEY_LIFE_PARABLE = PromptTemplate(
        id = "builtin_money_parable",
        name = "Money & Life Parable",
        isBuiltIn = true,
        content = """
Ikaw ay isang modernong Pinoy parable director na gumagawa ng maiikling pabula tungkol sa pera, disiplina, at diskarte sa buhay.
Mga Panuntunan sa Kuwento:
- Gumawa ng malinaw na contrast sa pagitan ng dalawang tauhan (hal. ang taong mayabang vs. ang taong tahimik na nag-iipon, o ang praktikal na mentor).
- Unang 3 segundo (Scene 1): Ipakita agad ang sitwasyong nakaka-relate ang karamihan sa pera o trabaho.
- Bawat scene ay may malinaw na visual action at emosyon (pagkabigla, pagsisisi, tagumpay).
- Tapusin sa isang maikling Tagalog proverb o wisdom line na tatatak sa manonood.
        """.trimIndent()
    )

    val DEFAULT_TEMPLATES: List<PromptTemplate> = listOf(
        VIRAL_PINOY_EXPLAINER,
        TAGALOG_HORROR_LEGEND,
        MONEY_LIFE_PARABLE
    )

    val DEFAULT_PRONUNCIATION_DICTIONARY: List<PronunciationEntry> = listOf(
        PronunciationEntry("Nike", "Nayk"),
        PronunciationEntry("WiFi", "Way-Fay"),
        PronunciationEntry("₱", "piso"),
        PronunciationEntry("%", "porsyento"),
        PronunciationEntry("GCash", "ji-kash"),
        PronunciationEntry("OFW", "oh ef double-u"),
        PronunciationEntry("BPO", "bi pi oh"),
        PronunciationEntry("ATM", "ey ti em"),
        PronunciationEntry("ROI", "ar oh ay"),
        PronunciationEntry("YouTube", "yutyub"),
        PronunciationEntry("TikTok", "tik-tok")
    )

    fun getDefaultById(id: String): PromptTemplate? = DEFAULT_TEMPLATES.firstOrNull { it.id == id }

    fun lockedSchemaInstructions(durationSeconds: Int, style: AnimationStyle): String {
        val sceneCount = (durationSeconds / 4.8).roundToInt().coerceIn(5, 22)
        val hudGuidance = when (style) {
            AnimationStyle.TECH_EXPLAINER ->
                "IMPORTANT FOR ${style.displayName}: Populate status_pill, callout_banner, hud_widget (with hud_label, hud_from_value, hud_to_value, checklist_items), and topic-driven diagram_type on key scenes."
            AnimationStyle.CROSS_SECTION ->
                "IMPORTANT FOR ${style.displayName}: Write a vivid topic-specific cutaway_diagram_prompt (e.g., engine combustion chamber, house ventilation, root system, banking flow—100% matching the user's topic!), flow_direction, status_pill, callout_banner, and hud_widget on EVERY scene."
            AnimationStyle.VIRAL_2D_EXPLAINER ->
                "IMPORTANT FOR ${style.displayName}: Populate expression, headgear (when fitting), topic_pill (short 1–3 word uppercase badge derived strictly from the topic), and data-driven diagram_type (BEFORE_AFTER_SPLIT, STEP_FLOW_ARROWS, METER_GAUGE_SHIFT, SCALE_WEIGH_OFF, or SUNBURST_HERO_CALLOUT) or topic-matched metaphor_visual on EVERY scene!"
            AnimationStyle.PIXEL_CUTAWAY_EXPLAINER ->
                "IMPORTANT FOR ${style.displayName}: Write a vivid topic-specific cutaway_diagram_prompt in 16-bit pixel art cutaway style, set flow_direction (UP, DOWN, LEFT_TO_RIGHT, RADIAL_BURST), and populate 1–2 topic-specific pointer_callouts (uppercase labels with x in 35..235 and y in 115..335) on EVERY scene! Only set cutaway_subject if the topic is actually about that biology/circuit subject; otherwise leave cutaway_diagram_prompt to drive the custom topic cutaway."
            else ->
                "OPTIONAL MOTION-GRAPHICS & DIAGRAMS: Include diagram_type (left_label/right_label/step_1..3) and HUD fields whenever visual comparison or causal steps clarify the topic."
        }
        return """
Return ONLY a valid JSON array containing exactly $sceneCount scene objects (each representing 3–5 seconds of narration for a ~${durationSeconds}s vertical 9:16 video in "${style.displayName}" aesthetic).
Do NOT output any prose, explanation, markdown fences, or <think> tags outside the JSON array.

TOPIC-FIRST DECOUPLING CONTRACT (CRITICAL ARCHITECTURE RULE):
- "${style.displayName}" defines ONLY the visual rendering aesthetic and camera feel—NEVER the story characters, props, or subject matter!
- 100% of characters, settings, diagram labels, cutaway subjects, and visual metaphors MUST come dynamically from the user's topic or imported prompt.
- FORBIDDEN GENERIC BIAS: NEVER insert unrelated skin wounds, "PROTEIN CHAIN", chicken mentors, or top-hat men unless the user's topic actually mentions skin wounds, protein, chickens, or wealthy top-hat tycoons.

STRICT NATURAL TAGALOG LANGUAGE RULES (MANDATORY):
- Write grammatically correct, everyday conversational Tagalog/Taglish that sounds like a real Filipino storyteller—never stiff or robotic Google Translate phrasing.
- FORBIDDEN: Do NOT translate English idioms word-for-word (e.g., NEVER translate "don't put all your eggs in one basket" literally; express the actual Filipino meaning naturally).
- Use proper Filipino grammar ("ng" vs "nang", "rito/dito", natural "po/naman/kasi/talaga/kaya" cadence) and keep every line clean, punchy, and free of stage directions.

ANTI-TEMPLATE VISUAL VARIETY RULES (MANDATORY):
- Make every scene dense and cinematic—ZERO lazy summaries or vague visual fillers ("generic", "simple background", "etc" are strictly forbidden).
- Vary "shot_framing", "character_pose", "camera_motion", and "environment_theme" on EVERY cut—NEVER repeat the exact same "camera_motion", "character_pose", or "shot_framing" on two consecutive scenes!
- Match character wardrobe & identity ("character_gender", "character_age", "character_outfit", "outfit_color_hex", "hair_color_hex", "hand_prop") to who is speaking in the topic (e.g. FEMALE + ELDERLY + MARKET_APRON + BASKET_GOODS for a tindera lola; YOUNG_ADULT + MEDICAL_SCRUBS for a nurse; CHILD + STUDENT_UNIFORM + BOOK_NOTEBOOK for a pupil).

Every object in the JSON array MUST have ALL required keys with exact names:
- "scene_id": integer starting at 1
- "tagalog_narration": string (natural conversational Tagalog/Taglish, MINIMUM 11 words and MINIMUM 60 characters per scene, no stage directions or speaker labels)
- "visual_description": string (MINIMUM 18 words in vivid English explicitly detailing ALL 4 items: 1. main subject & exact action/pose, 2. facial expression & hand prop, 3. specific environment/foreground/background elements, 4. lighting/atmosphere; NEVER use "generic", "simple background", or "etc")
- "background_prompt": string (vivid English background diorama description matching "${style.displayName}" aesthetic and time of day, no text, no people)
- "camera_motion": strictly one of ["ZOOM_IN", "ZOOM_OUT", "PAN_LEFT", "PAN_RIGHT", "TRACKING_FOLLOW", "IMPACT_SHAKE", "WHIP_PAN_LEFT", "WHIP_PAN_RIGHT", "SHAKE"] (NEVER "STATIC" or repeating the previous scene's motion!)
- "actor_role": strictly one of ["NARRATOR", "HERO", "RICH_MAN", "POOR_MAN", "CHICKEN_MENTOR"] (prefer "NARRATOR" or "HERO" paired with character_outfit/gender/age for everyday people!)
- "actor_action": strictly one of ["WALK_IN", "TALK_BOUNCE", "THUMBS_UP", "POINT_STICK", "SURPRISED_JUMP", "CRY_SHAKE"]
- "world_fx": strictly one of ["NONE", "COIN_RAIN", "FIRE_SPARKS", "CHALKBOARD_DRAW"]

$hudGuidance
Optional cinematic, wardrobe, cutaway & data-driven diagram keys per scene object:
- "environment_theme": one of ["BRIGHT_MORNING_KITCHEN", "SUNNY_MARKET_STREET", "MODERN_OFFICE_DESK", "COZY_BEDROOM_MIDNIGHT", "CLASSROOM_CHALKBOARD", "BANK_VAULT_FINANCE", "SUNNY_PARK_NATURE", "SCIENCE_LAB_INTERIOR", "HOSPITAL_CLINIC", "ABSTRACT_STUDIO_GRADIENT", "DRAMATIC_SUNSET_CITY"]
- "character_gender": one of ["MALE", "FEMALE"]
- "character_age": one of ["CHILD", "YOUNG_ADULT", "MIDDLE_AGED", "ELDERLY"]
- "character_outfit": one of ["CASUAL_TSHIRT", "MARKET_APRON", "OFFICE_SUIT", "STUDENT_UNIFORM", "MEDICAL_SCRUBS", "HOODIE", "WORK_UNIFORM"]
- "outfit_color_hex": vibrant hex string for shirt/outfit (e.g. "#E11D48", "#0EA5E9", "#16A34A")
- "hair_color_hex": hex string for hair color (e.g. "#1F2937", "#78350F", "#D1D5DB")
- "shot_framing": one of ["WIDE_FULL_BODY", "MEDIUM_WAIST_UP", "CLOSE_UP_FACE", "RULE_OF_THIRDS_LEFT", "RULE_OF_THIRDS_RIGHT"] (NEVER repeat the previous scene's framing!)
- "character_pose": one of ["WALK_ACROSS_LEFT_TO_RIGHT", "THREE_QUARTER_EXPLAIN", "SIT_SLUMPED", "SHOCK_AIRBORNE_RECOIL", "POINT_AND_GESTURE", "CELEBRATE_JUMP", "TWO_CHARACTER_CONVERSATION"] (NEVER repeat the previous scene's pose!)
- "hand_prop": one of ["NONE", "POINTER_STICK", "MONEY_BAG", "PHONE_SCREEN", "COFFEE_CUP", "BASKET_GOODS", "BOOK_NOTEBOOK", "TOOL_WRENCH", "MAGNIFYING_GLASS", "LIGHTBULB"]
- "shot_type": one of ["WIDE_STAGE", "MEDIUM_WAIST_UP", "CLOSE_UP_REACTION", "TRACKING_FOLLOW", "WHIP_CUT"]
- "secondary_actor_role": optional partner role for TWO_CHARACTER_CONVERSATION (["HERO", "NARRATOR", "RICH_MAN", "POOR_MAN", "CHICKEN_MENTOR"])
- "expression": one of ["HAPPY_TALK", "ANGRY_EVIL", "PANIC_SWEAT_FIRE", "SHOCKED_WIDE_EYES", "TONGUE_STING", "SLEEPING_ZZZ"]
- "headgear": one of ["NONE", "CHEF_HAT", "HARD_HAT", "CROWN"]
- "diagram_type": one of ["NONE", "BEFORE_AFTER_SPLIT", "STEP_FLOW_ARROWS", "METER_GAUGE_SHIFT", "SCALE_WEIGH_OFF", "SUNBURST_HERO_CALLOUT"]
- "left_label": short 1–3 word label for BEFORE_AFTER_SPLIT or SCALE_WEIGH_OFF left side
- "right_label": short 1–3 word label for BEFORE_AFTER_SPLIT or SCALE_WEIGH_OFF right side
- "step_1": short 1–3 word step for STEP_FLOW_ARROWS
- "step_2": short 1–3 word step for STEP_FLOW_ARROWS
- "step_3": short 1–3 word step for STEP_FLOW_ARROWS
- "metaphor_visual": optional topic-matched visual from ["NONE", "TIME_LAPSE_CLOCK", "BOILING_POT_PROCESS", "SUNBURST_SPOTLIGHT", "CRACKING_EGG_CHICK", "GROWING_MONEY_TREE", "BRAIN_BATTERY_DRAIN", "SCALE_BALANCE_COMPARE", "ROCKET_GROWTH_CHART", "LOCK_AND_KEY_SECRET", "SHIELD_BLOCKING_ARROWS", "DOMINO_CHAIN_REACTION", "CHOMP_CHAIN", "TISSUE_CROSS_SECTION"]
- "topic_pill": short 1–3 word uppercase badge text derived strictly from the topic
- "status_pill": one of ["NONE", "GOAL", "PROBLEM", "FIX", "SECRET"]
- "callout_banner": short 1–3 word punchy Tagalog/Taglish emphasis text derived from the topic
- "hud_widget": one of ["NONE", "PROGRESS_GAUGE", "WAVEFORM_GRAPH", "ANIMATED_CHECKLIST"]
- "hud_label": short uppercase metric title derived from the topic
- "hud_from_value": integer 0..100
- "hud_to_value": integer 0..100
- "checklist_items": array of 2–4 short strings (only when hud_widget is "ANIMATED_CHECKLIST")
- "cutaway_diagram_prompt": vivid English description of the topic-specific cross-section cutaway diagram (no text, no labels) for PIXEL_CUTAWAY_EXPLAINER or CROSS_SECTION
- "flow_direction": one of ["UP", "DOWN", "LEFT_TO_RIGHT", "RADIAL_BURST"]
- "cutaway_subject": optional ["BRAIN_NEURON_SIGNAL", "SKIN_WOUND_HEALING", "TOOTH_ENAMEL_DECAY", "HEART_ARTERY_FLOW", "BATTERY_CIRCUIT_CHARGE", "SOIL_ROOT_GROWTH"] (ONLY if topic matches!)
- "cutaway_stage": one of ["SURFACE_ZOOM_IN", "CROSS_SECTION_DAMAGE", "IMMUNE_CLEANUP", "STRAND_AND_VESSEL_REBUILD", "CELL_MIGRATION_CLOSE", "SURFACE_HEALED_ZOOM_OUT"]
- "pointer_callouts": array of 1–2 objects [{"label": "TOPIC FEATURE", "x": 135, "y": 175}] targeting features on a 270x480 cross-section
        """.trimIndent()
    }

    fun composeSystemPrompt(
        activeCreativePrompt: String,
        durationSeconds: Int,
        style: AnimationStyle
    ): String {
        val safeCreative = activeCreativePrompt.trim().ifEmpty { VIRAL_PINOY_EXPLAINER.content }
        return buildString {
            append(safeCreative)
            append("\n\n--- MANDATORY TECHNICAL JSON FORMAT (DO NOT OVERRIDE) ---\n")
            append(lockedSchemaInstructions(durationSeconds, style))
        }
    }

    val VIRAL_TAGALOG_IDEAS: List<String> = listOf(
        "Bakit laging ubos ang sahod tatlong araw pa lang pagkatapos ng payday?",
        "Ang sikreto ng matandang tindera kung paano siya nakabili ng tatlong bahay",
        "Huwag na huwag kang lilingon kapag may tumawag sa pangalan mo sa jeep ng alas-tres",
        "Bakit mas mabilis yumaman ang tahimik na nag-iipon kaysa sa mahilig mag-flex?",
        "Ang kuwento ng manok na nagturo kung bakit delikado ang isang source of income lang",
        "Ano ang nangyayari sa utak mo kapag puro doomscrolling ka bago matulog?",
        "Tatlong ugali ng mga taong akala mo simple lang pero milyonaryo pala",
        "Ang misteryo ng lumang bahay sa kanto na laging bukas ang ilaw tuwing hatinggabi",
        "Paano naging ₱100,000 ang ₱500 dahil sa isang simpleng Rule of 72?",
        "Bakit hindi sapat ang sipag kung mali naman ang direksyon ng barko mo?",
        "Ang aral ng basag na itlog: Bakit minsan kailangan mong masaktan bago lumakas",
        "Bakit mas nakakatakot ang utang na maliit pero nakakalimutang bayaran?"
    )

    data class VoicePreset(val id: String, val name: String, val tag: String)

    val VOICE_PRESETS: List<VoicePreset> = listOf(
        VoicePreset("N2IVS1w4EtoT3dr4eOWO", "Tagalog Creator (Default)", "Default"),
        VoicePreset("pNInz6obpgDQGcFmaJgB", "Adam (Deep Narrator)", "Storyteller"),
        VoicePreset("EXAVITQu4vr4xnSDxMaL", "Sarah (Warm & Clear)", "Explainer"),
        VoicePreset("TxGEqnHWrfWFTfGW9XjX", "Josh (Punchy Creator)", "Viral Reels"),
        VoicePreset("VR6AewLTigWG4xSOukaG", "Arnold (Dramatic)", "Horror / Suspense"),
        VoicePreset("21m00Tcm4TlvDq8ikWAM", "Rachel (Calm Host)", "Parable")
    )
}
