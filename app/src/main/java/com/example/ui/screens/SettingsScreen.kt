package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BuiltInPrompts
import com.example.llm.DetectedModel
import com.example.llm.LlmProviderPreset
import com.example.llm.UniversalModelDetector
import com.example.tts.DetectedVoice
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeCoral
import com.example.ui.theme.ForgeCrimson
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeMint
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.viewmodel.DetectionBannerState
import com.example.ui.viewmodel.MainViewModel
import java.util.Locale
import kotlin.math.roundToInt

private val EmeraldSuccess = Color(0xFF10B981)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val previewCustomSprites by viewModel.previewCustomSprites.collectAsStateWithLifecycle()
    val isCheckingCredits by viewModel.isCheckingCredits.collectAsStateWithLifecycle()

    val detectedModels by viewModel.detectedModels.collectAsStateWithLifecycle()
    val isDetectingModels by viewModel.isDetectingModels.collectAsStateWithLifecycle()
    val modelDetectionBanner by viewModel.modelDetectionBanner.collectAsStateWithLifecycle()
    val showAllRawModels by viewModel.showAllRawModels.collectAsStateWithLifecycle()
    val isModelSheetVisible by viewModel.isModelSheetVisible.collectAsStateWithLifecycle()
    val isPingingModel by viewModel.isPingingModel.collectAsStateWithLifecycle()
    val modelPingBanner by viewModel.modelPingBanner.collectAsStateWithLifecycle()

    val detectedVoices by viewModel.detectedVoices.collectAsStateWithLifecycle()
    val isDetectingVoices by viewModel.isDetectingVoices.collectAsStateWithLifecycle()
    val voiceDetectionBanner by viewModel.voiceDetectionBanner.collectAsStateWithLifecycle()

    val clipboardManager = LocalClipboardManager.current

    fun cleanInput(raw: String): String =
        raw.replace("\r", "").replace("\n", "").trim()

    var newElevenKey by remember { mutableStateOf("") }
    var showNewElevenKey by remember { mutableStateOf(false) }
    var ttsBaseUrl by remember { mutableStateOf(settings.effectiveTtsBaseUrl) }
    var ttsModelId by remember { mutableStateOf(settings.effectiveTtsModelId) }
    var voiceIdInput by remember { mutableStateOf(settings.effectiveVoiceId) }
    var voiceDropdownExpanded by remember { mutableStateOf(false) }

    var llmBaseUrl by remember { mutableStateOf(settings.effectiveLlmBaseUrl) }
    var llmApiKey by remember { mutableStateOf(settings.effectiveLlmApiKey) }
    var showLlmApiKey by remember { mutableStateOf(false) }
    var selectedModelId by remember { mutableStateOf(settings.effectiveSelectedModelId) }
    var selectedPreset by remember {
        mutableStateOf(UniversalModelDetector.matchPresetForUrl(settings.effectiveLlmBaseUrl))
    }

    var aiHordeKey by remember { mutableStateOf(settings.aiHordeKey) }
    var narrationSpeed by remember { mutableFloatStateOf(settings.narrationSpeed) }
    var showAdvancedArtKey by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        ttsBaseUrl = settings.effectiveTtsBaseUrl
        ttsModelId = settings.effectiveTtsModelId
        voiceIdInput = settings.effectiveVoiceId
        llmBaseUrl = settings.effectiveLlmBaseUrl
        llmApiKey = settings.effectiveLlmApiKey
        selectedModelId = settings.effectiveSelectedModelId
        selectedPreset = UniversalModelDetector.matchPresetForUrl(settings.effectiveLlmBaseUrl)
        aiHordeKey = settings.aiHordeKey
        narrationSpeed = settings.narrationSpeed
    }

    fun applyLlmKeyChange(rawNewKey: String) {
        val cleanKey = cleanInput(rawNewKey)
        llmApiKey = cleanKey
        val inferredUrl = UniversalModelDetector.inferBaseUrlFromApiKey(cleanKey)
        if (inferredUrl != null && UniversalModelDetector.isDefaultOrEmptyBaseUrl(llmBaseUrl)) {
            llmBaseUrl = inferredUrl
            selectedPreset = UniversalModelDetector.matchPresetForUrl(inferredUrl)
            if (selectedModelId.isBlank() && selectedPreset.defaultModelHint.isNotBlank()) {
                selectedModelId = selectedPreset.defaultModelHint
            }
        }
        viewModel.updateLlmConfigImmediate(
            baseUrl = llmBaseUrl,
            apiKey = llmApiKey,
            modelId = selectedModelId
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "API & Studio Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Universal third-party LLM & ElevenLabs proxy setup with automatic model & voice detection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. UNIVERSAL LLM STORY DIRECTOR & AUTO-DETECTOR
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioOutline),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.SmartToy,
                                contentDescription = null,
                                tint = ForgeCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Universal LLM Story Director",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = ForgeCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = selectedPreset.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = ForgeCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "Quick Provider Presets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LlmProviderPreset.entries.forEach { preset ->
                            val isSelected = selectedPreset == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedPreset = preset
                                    viewModel.clearLlmDetectionState()
                                    if (preset == LlmProviderPreset.CUSTOM) {
                                        if (UniversalModelDetector.isDefaultOrEmptyBaseUrl(llmBaseUrl)) {
                                            llmBaseUrl = ""
                                        }
                                    } else {
                                        llmBaseUrl = preset.baseUrl
                                        if (preset.defaultModelHint.isNotBlank() &&
                                            (selectedModelId.isBlank() ||
                                                LlmProviderPreset.entries.any { it.defaultModelHint == selectedModelId })
                                        ) {
                                            selectedModelId = preset.defaultModelHint
                                        }
                                    }
                                    viewModel.updateLlmConfigImmediate(
                                        baseUrl = llmBaseUrl,
                                        apiKey = llmApiKey,
                                        modelId = selectedModelId
                                    )
                                },
                                label = {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForgeCyan.copy(alpha = 0.20f),
                                    selectedLabelColor = ForgeCyan
                                ),
                                modifier = Modifier.testTag("preset_chip_${preset.name}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = llmBaseUrl,
                        onValueChange = { raw ->
                            val clean = cleanInput(raw)
                            llmBaseUrl = clean
                            selectedPreset = UniversalModelDetector.matchPresetForUrl(clean)
                            viewModel.updateLlmConfigImmediate(
                                baseUrl = clean,
                                apiKey = llmApiKey,
                                modelId = selectedModelId
                            )
                        },
                        label = { Text("LLM Base URL") },
                        placeholder = { Text("https://api.deepseek.com") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("llm_base_url_input"),
                        colors = studioTextFieldColors()
                    )

                    OutlinedTextField(
                        value = llmApiKey,
                        onValueChange = { applyLlmKeyChange(it) },
                        label = { Text("LLM API Key (auto-detects AIza / gsk_ / sk-or-)") },
                        placeholder = { Text("Paste your DeepSeek, Gemini, Groq, OpenRouter, or Proxy API key") },
                        singleLine = true,
                        visualTransformation = if (showLlmApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showLlmApiKey = !showLlmApiKey },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("toggle_llm_key_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (showLlmApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showLlmApiKey) "Hide API Key" else "Show API Key"
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val pasted = clipboardManager.getText()?.text.orEmpty().trim()
                                        if (pasted.isNotEmpty()) {
                                            applyLlmKeyChange(pasted)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("paste_llm_key_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = "Paste API Key"
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("llm_api_key_input"),
                        colors = studioTextFieldColors()
                    )

                    // Detect Available Models CTA + Raw Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                viewModel.detectAvailableModels(
                                    baseUrl = llmBaseUrl,
                                    apiKey = llmApiKey,
                                    showAllRaw = showAllRawModels,
                                    openSheetOnSuccess = true
                                )
                            },
                            enabled = !isDetectingModels,
                            colors = ButtonDefaults.buttonColors(containerColor = ForgeCyan),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("detect_models_button")
                        ) {
                            if (isDetectingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Detecting...",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Radar,
                                    contentDescription = "Detect Available Models",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Detect Available Models",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show all raw models",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Include whisper, tts, embedding & image models",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showAllRawModels,
                            onCheckedChange = { checked ->
                                viewModel.setShowAllRawModels(
                                    showAll = checked,
                                    baseUrl = llmBaseUrl,
                                    apiKey = llmApiKey
                                )
                            },
                            modifier = Modifier.testTag("show_raw_models_switch")
                        )
                    }

                    modelDetectionBanner?.let { banner ->
                        DetectionStatusBanner(
                            state = banner,
                            modifier = Modifier.testTag("model_detection_banner")
                        )
                    }

                    // Selected Model ID + Picker trigger + Ping Test button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = selectedModelId,
                            onValueChange = { raw ->
                                val clean = cleanInput(raw)
                                selectedModelId = clean
                                viewModel.updateLlmConfigImmediate(
                                    baseUrl = llmBaseUrl,
                                    apiKey = llmApiKey,
                                    modelId = clean
                                )
                            },
                            label = { Text("Selected Model ID") },
                            placeholder = { Text("e.g. deepseek-chat or gemini-2.5-flash") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (detectedModels.isNotEmpty()) {
                                            viewModel.openModelPickerSheet()
                                        } else {
                                            viewModel.detectAvailableModels(
                                                baseUrl = llmBaseUrl,
                                                apiKey = llmApiKey,
                                                showAllRaw = showAllRawModels,
                                                openSheetOnSuccess = true
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("open_model_picker_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = "Choose detected model"
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("selected_model_input"),
                            colors = studioTextFieldColors()
                        )

                        OutlinedButton(
                            onClick = {
                                viewModel.pingSelectedModel(
                                    baseUrl = llmBaseUrl,
                                    apiKey = llmApiKey,
                                    modelId = selectedModelId
                                )
                            },
                            enabled = !isPingingModel,
                            border = BorderStroke(1.dp, ForgeMint.copy(alpha = 0.7f)),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("test_model_ping_button")
                        ) {
                            if (isPingingModel) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ForgeMint
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = "Test Model Ping",
                                    tint = ForgeMint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Ping",
                                    color = ForgeMint,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    modelPingBanner?.let { ping ->
                        DetectionStatusBanner(
                            state = ping,
                            modifier = Modifier.testTag("model_ping_banner")
                        )
                    }

                    // Collapsible Optional AI Horde Backdrop Art Key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedArtKey = !showAdvancedArtKey }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Optional Backdrop Art Key (AI Horde)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = if (showAdvancedArtKey) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "Toggle backdrop art key",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showAdvancedArtKey) {
                        OutlinedTextField(
                            value = aiHordeKey,
                            onValueChange = { aiHordeKey = cleanInput(it) },
                            label = { Text("AI Horde Art Key (Optional • Default: 0000000000)") },
                            supportingText = { Text("Used only as image art backup after Pollinations Flux") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("aihorde_key_input"),
                            colors = studioTextFieldColors()
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.saveLlmSettings(
                                llmBaseUrl = llmBaseUrl,
                                llmApiKey = llmApiKey,
                                selectedModelId = selectedModelId,
                                ttsBaseUrl = ttsBaseUrl,
                                ttsModelId = ttsModelId,
                                selectedVoiceId = voiceIdInput,
                                aiHordeKey = aiHordeKey,
                                narrationSpeed = narrationSpeed
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_settings_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ForgeCoral)
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save Settings")
                        Spacer(Modifier.width(8.dp))
                        Text("Save API & Studio Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. ELEVENLABS / THIRD-PARTY TTS PROXY & VOICE AUTO-DETECTOR
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioOutline),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = ForgeAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "ElevenLabs / TTS Proxy",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.checkAllElevenLabsCredits(ttsBaseUrl) },
                            enabled = !isCheckingCredits,
                            modifier = Modifier.testTag("check_credits_button")
                        ) {
                            if (isCheckingCredits) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Check Credits",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("Check Credits", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Text(
                        text = "Supports official ElevenLabs keys and third-party proxy endpoints with automatic voice listing & word-level timing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = ttsBaseUrl,
                        onValueChange = { raw ->
                            val clean = cleanInput(raw)
                            ttsBaseUrl = clean
                            viewModel.setTtsBaseUrl(clean)
                        },
                        label = { Text("TTS Base URL") },
                        placeholder = { Text("https://api.elevenlabs.io") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tts_base_url_input"),
                        colors = studioTextFieldColors()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newElevenKey,
                            onValueChange = { newElevenKey = cleanInput(it) },
                            label = { Text("Add ElevenLabs xi-api-key") },
                            singleLine = true,
                            visualTransformation = if (showNewElevenKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showNewElevenKey = !showNewElevenKey },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showNewElevenKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showNewElevenKey) "Hide TTS key" else "Show TTS key"
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("elevenlabs_key_input"),
                            colors = studioTextFieldColors()
                        )
                        Button(
                            onClick = {
                                viewModel.addElevenLabsKey(newElevenKey, ttsBaseUrl)
                                newElevenKey = ""
                            },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("add_elevenlabs_key_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add key")
                        }
                    }

                    // Detect Voices & Credits Button + Voice & TTS Model Inputs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.detectVoicesAndCredits(
                                    ttsBaseUrl = ttsBaseUrl,
                                    draftApiKey = newElevenKey
                                )
                            },
                            enabled = !isDetectingVoices,
                            border = BorderStroke(1.dp, ForgeAmber.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("detect_voices_button")
                        ) {
                            if (isDetectingVoices) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ForgeAmber
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Detecting Voices & Credits...", color = ForgeAmber, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = "Detect Voices & Credits",
                                    tint = ForgeAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Detect Voices & Credits", color = ForgeAmber, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    voiceDetectionBanner?.let { banner ->
                        DetectionStatusBanner(
                            state = banner,
                            modifier = Modifier.testTag("voice_detection_banner")
                        )
                    }

                    // Voice Picker Dropdown (combines detected voices + built-in presets)
                    val voiceOptions: List<DetectedVoice> = remember(detectedVoices) {
                        if (detectedVoices.isNotEmpty()) {
                            detectedVoices
                        } else {
                            BuiltInPrompts.VOICE_PRESETS.map {
                                DetectedVoice(voiceId = it.id, name = it.name, category = it.tag)
                            }
                        }
                    }
                    val activeVoiceDisplay = remember(settings.selectedVoiceId, voiceOptions) {
                        val match = voiceOptions.firstOrNull { it.voiceId == settings.selectedVoiceId }
                        if (match != null) {
                            "${match.name} (${match.category}) • ${match.voiceId}"
                        } else {
                            settings.selectedVoiceId
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = voiceDropdownExpanded,
                        onExpandedChange = { voiceDropdownExpanded = !voiceDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = activeVoiceDisplay,
                            onValueChange = {},
                            readOnly = true,
                            label = {
                                Text(
                                    if (detectedVoices.isNotEmpty()) {
                                        "Voice Picker (${detectedVoices.size} detected)"
                                    } else {
                                        "Voice Picker (Built-in presets — tap Detect Voices)"
                                    }
                                )
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceDropdownExpanded)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .testTag("voice_picker_dropdown"),
                            colors = studioTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = voiceDropdownExpanded,
                            onDismissRequest = { voiceDropdownExpanded = false }
                        ) {
                            voiceOptions.forEach { voice ->
                                val isSelected = voice.voiceId == settings.selectedVoiceId
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = voice.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) ForgeAmber else MaterialTheme.colorScheme.onSurface
                                                )
                                                Surface(
                                                    color = ForgeAmber.copy(alpha = 0.14f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = voice.category,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = ForgeAmber,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = voice.voiceId,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        voiceIdInput = voice.voiceId
                                        viewModel.selectVoiceId(voice.voiceId)
                                        voiceDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = voiceIdInput,
                            onValueChange = { raw ->
                                val clean = cleanInput(raw)
                                voiceIdInput = clean
                                viewModel.selectVoiceId(clean)
                            },
                            label = { Text("Voice ID") },
                            placeholder = { Text("N2IVS1w4EtoT3dr4eOWO") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_voice_id_input"),
                            colors = studioTextFieldColors()
                        )
                        OutlinedTextField(
                            value = ttsModelId,
                            onValueChange = { raw ->
                                val clean = cleanInput(raw)
                                ttsModelId = clean
                                viewModel.setTtsModelId(clean)
                            },
                            label = { Text("TTS Model ID") },
                            placeholder = { Text("eleven_multilingual_v2") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tts_model_id_input"),
                            colors = studioTextFieldColors()
                        )
                    }

                    if (settings.elevenLabsKeys.isEmpty()) {
                        Surface(
                            color = StudioSurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No ElevenLabs keys added yet. Add a key above for studio-grade Tagalog voiceover with word-level alignment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        settings.elevenLabsKeys.forEach { entry ->
                            Surface(
                                color = StudioSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, StudioOutline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.maskedKey,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val statusText = when {
                                            entry.lastError != null -> "Status: ${entry.lastError}"
                                            entry.remainingChars != null ->
                                                "${entry.remainingChars} / ${entry.limitChars ?: "?"} chars left (${entry.tier ?: "free"})"
                                            else -> "Tap Check Credits to verify quota"
                                        }
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (entry.lastError != null) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                ForgeMint
                                            }
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeElevenLabsKey(entry.key) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Remove Key",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        color = StudioSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, StudioOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val speedLabel = String.format(Locale.US, "%.2fx", narrationSpeed)
                            val pacingHint = when {
                                narrationSpeed < 0.95f -> "Dramatic Slow"
                                narrationSpeed > 1.05f -> "Viral Fast"
                                else -> "Natural Balanced"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Narration Speed",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$pacingHint • Passed to ElevenLabs voice_settings",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = ForgeAmber.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .clickable {
                                            narrationSpeed = 1.0f
                                            viewModel.setNarrationSpeed(1.0f)
                                        }
                                        .minimumInteractiveComponentSize()
                                        .testTag("narration_speed_reset_badge")
                                ) {
                                    Text(
                                        text = speedLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ForgeAmber,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Slider(
                                value = narrationSpeed,
                                onValueChange = { raw ->
                                    narrationSpeed = ((raw * 100f).roundToInt() / 100f).coerceIn(0.85f, 1.15f)
                                },
                                onValueChangeFinished = {
                                    viewModel.setNarrationSpeed(narrationSpeed)
                                },
                                valueRange = 0.85f..1.15f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ForgeAmber,
                                    activeTrackColor = ForgeAmber,
                                    inactiveTrackColor = StudioOutline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("narration_speed_slider")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "0.85x Dramatic",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "1.00x Default",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "1.15x Viral",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. CUSTOM PNG CHARACTER SPRITE MANAGER
            CharacterSpriteManagerCard(
                customSpritePaths = settings.customSpritePaths,
                previewSprites = previewCustomSprites,
                stickerOutlineEnabled = settings.spriteStickerOutline,
                onImportSprite = { role, uri -> viewModel.importCharacterSprite(role, uri) },
                onRemoveSprite = { role -> viewModel.removeCharacterSprite(role) },
                onToggleStickerOutline = { viewModel.setSpriteStickerOutline(it) }
            )

            // 4. CUSTOM BGM AUDIO STUDIO
            CustomBgmCard(
                customBgmName = settings.customBgmName,
                onImportBgm = { uri -> viewModel.importCustomBgm(uri) },
                onClearBgm = { viewModel.clearCustomBgm() }
            )

            // 5. EDITABLE TAGALOG PRONUNCIATION DICTIONARY
            PronunciationDictionaryCard(
                entries = settings.pronunciationDictionary,
                onUpsertEntry = { orig, spoken -> viewModel.upsertPronunciation(orig, spoken) },
                onDeleteEntry = { orig -> viewModel.deletePronunciation(orig) },
                onResetDefaults = { viewModel.resetPronunciationDictionary() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (isModelSheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModelPickerBottomSheet(
            sheetState = sheetState,
            models = detectedModels,
            selectedModelId = selectedModelId,
            showAllRawModels = showAllRawModels,
            onToggleShowAllRaw = { checked ->
                viewModel.setShowAllRawModels(
                    showAll = checked,
                    baseUrl = llmBaseUrl,
                    apiKey = llmApiKey
                )
            },
            onSelectModel = { model ->
                selectedModelId = model.id
                viewModel.selectDetectedModel(
                    baseUrl = llmBaseUrl,
                    apiKey = llmApiKey,
                    modelId = model.id
                )
            },
            onDismiss = { viewModel.dismissModelPickerSheet() }
        )
    }
}

@Composable
private fun DetectionStatusBanner(
    state: DetectionBannerState,
    modifier: Modifier = Modifier
) {
    val accent = if (state.isSuccess) EmeraldSuccess else ForgeCrimson
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (state.isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = if (state.isSuccess) "Success" else "Error",
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = state.message,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerBottomSheet(
    sheetState: androidx.compose.material3.SheetState,
    models: List<DetectedModel>,
    selectedModelId: String,
    showAllRawModels: Boolean,
    onToggleShowAllRaw: (Boolean) -> Unit,
    onSelectModel: (DetectedModel) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(models, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) models
        else models.filter {
            it.id.contains(q, ignoreCase = true) ||
                it.displayName.contains(q, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        modifier = Modifier.testTag("model_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Detected Models (${models.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap any model to apply immediately",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onToggleShowAllRaw(!showAllRawModels) }) {
                    Text(
                        text = if (showAllRawModels) "Chat Only" else "Show All Raw",
                        color = ForgeCyan
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search models") },
                placeholder = { Text("gemini-2.5, qwen, deepseek, llama...") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = "Search models")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_search_input"),
                colors = studioTextFieldColors()
            )

            HorizontalDivider(color = StudioOutline)

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching models for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { model ->
                        val isSelected = model.id == selectedModelId
                        Surface(
                            color = if (isSelected) ForgeCyan.copy(alpha = 0.16f) else StudioSurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) ForgeCyan else StudioOutline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectModel(model) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected || model.isRecommended) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) ForgeCyan else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (model.isRecommended) {
                                        Spacer(Modifier.height(4.dp))
                                        Surface(
                                            color = EmeraldSuccess.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = EmeraldSuccess,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "Recommended for Tagalog JSON",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldSuccess
                                                )
                                            }
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected model",
                                        tint = ForgeCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
