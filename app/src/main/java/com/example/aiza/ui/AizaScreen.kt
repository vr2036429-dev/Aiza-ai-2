package com.example.aiza.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiza.core.ConversationTurn
import com.example.aiza.core.model.AssistantStatus
import com.example.aiza.core.model.Language
import com.example.aiza.tools.ToolResult
import com.example.aiza.voice.VoiceError
import com.example.aiza.voice.VoiceState
import com.example.aiza.voice.VoiceStatusSnapshot
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.AssistantBubbleBg
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceBorder
import com.example.ui.theme.CosmicDarkBg
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSpaceSurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UserBubbleBg

@Composable
fun AizaScreen(
    viewModel: AizaViewModel,
    modifier: Modifier = Modifier
) {
    val status by viewModel.status.collectAsState()
    val history by viewModel.conversationHistory.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val showDiagnostics by viewModel.showDiagnosticsSheet.collectAsState()
    val showVoiceSettings by viewModel.showVoiceSettingsDialog.collectAsState()
    val voiceStatus by viewModel.voiceStatus.collectAsState()
    val pendingConfirmation by viewModel.latestPendingConfirmation.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceListening()
        }
    }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll when new message arrives
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(history.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CosmicDarkBg,
        topBar = {
            AizaTopBar(
                status = status,
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { viewModel.setLanguageFilter(it) },
                onOpenDiagnostics = { viewModel.openDiagnostics(true) },
                onOpenVoiceSettings = { viewModel.openVoiceSettings(true) },
                onClearHistory = { viewModel.clearHistory() },
                hasHistory = history.isNotEmpty()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Status Sub-Banner
            AizaStatusBanner(status = status)

            // Live Voice Interaction HUD (Listening / Speaking / Error)
            VoiceInteractionHUD(
                voiceStatus = voiceStatus,
                onStopListening = { viewModel.stopVoiceListening() },
                onStopSpeaking = { viewModel.stopSpeaking() },
                onDismissError = { viewModel.cancelVoice() }
            )

            // Conversation Messages Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (history.isEmpty()) {
                    AizaEmptyState(onSamplePromptClick = { prompt ->
                        inputText = prompt
                    })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(history, key = { it.response.id }) { turn ->
                            ConversationTurnItem(
                                turn = turn,
                                onAuthorize = { confirmationId, approved ->
                                    viewModel.authorizeAction(confirmationId, approved)
                                }
                            )
                        }
                    }
                }
            }

            // Quick Prompt Suggestions Row
            QuickSuggestionsRow(onSelectPrompt = { prompt ->
                viewModel.sendRequest(prompt)
            })

            // User Input Bar with Voice Input Button
            AizaInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendRequest(inputText)
                        inputText = ""
                    }
                },
                isBusy = status.isBusy,
                voiceStatus = voiceStatus,
                onVoiceInputClick = {
                    if (voiceStatus.isListening) {
                        viewModel.stopVoiceListening()
                    } else if (voiceStatus.isSpeaking) {
                        viewModel.stopSpeaking()
                    } else {
                        if (viewModel.hasMicrophonePermission()) {
                            viewModel.startVoiceListening()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            )
        }
    }

    if (showDiagnostics) {
        AizaDiagnosticsSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.openDiagnostics(false) }
        )
    }

    if (showVoiceSettings) {
        AizaVoiceSettingsDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.openVoiceSettings(false) }
        )
    }
}

@Composable
private fun AizaTopBar(
    status: AssistantStatus,
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onClearHistory: () -> Unit,
    hasHistory: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepSpaceSurface)
            .border(width = 1.dp, color = CardSurfaceBorder)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Branding and Nexus
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0F2644), Color(0xFF0284C7))
                            )
                        )
                        .border(1.dp, NeonCyan, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "A",
                        color = NeonCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AIZA",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "v2.0",
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "NATURAL VOICE AI // ASIK",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenVoiceSettings,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("voice_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Voice Engine Settings",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (hasHistory) {
                    IconButton(
                        onClick = onClearHistory,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Conversation",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("diagnostics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Diagnostics and Modules",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Language Mode Filter Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Text(
                    text = "LANG:",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            items(Language.values()) { lang ->
                val isSelected = selectedLanguage == lang
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) NeonCyan.copy(alpha = 0.2f) else CardSurface)
                        .border(
                            1.dp,
                            if (isSelected) NeonCyan else CardSurfaceBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onLanguageSelected(lang) }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = lang.displayName,
                        color = if (isSelected) NeonCyan else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun AizaStatusBanner(status: AssistantStatus) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val dotColor = when (status) {
        AssistantStatus.IDLE -> EmeraldSuccess
        AssistantStatus.UNDERSTANDING,
        AssistantStatus.ANALYZING_INTENT,
        AssistantStatus.THINKING -> ElectricBlue
        AssistantStatus.CHECKING_SECURITY,
        AssistantStatus.AWAITING_CONFIRMATION -> AmberAlert
        AssistantStatus.EXECUTING_TOOL -> NeonPurple
        AssistantStatus.SPEAKING -> NeonCyan
        AssistantStatus.ERROR -> CrimsonAlert
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0C1322))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = if (status.isBusy) pulseAlpha else 1f))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "STATUS: ${status.label.uppercase()}",
            color = dotColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            fontFamily = FontFamily.Monospace
        )
        if (status.isBusy) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = dotColor
            )
        }
    }
}

@Composable
private fun ConversationTurnItem(
    turn: ConversationTurn,
    onAuthorize: (confirmationId: String, approved: Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // User Message (Right Aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 4.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 14.dp
                        )
                    )
                    .background(UserBubbleBg)
                    .border(
                        1.dp,
                        CardSurfaceBorder,
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 4.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 14.dp
                        )
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = turn.request.userName.uppercase(),
                            color = ElectricBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = turn.request.text,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Assistant Response (Left Aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 14.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 14.dp
                        )
                    )
                    .background(AssistantBubbleBg)
                    .border(
                        1.dp,
                        NeonCyan.copy(alpha = 0.3f),
                        RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 14.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 14.dp
                        )
                    )
                    .padding(14.dp)
            ) {
                Column {
                    // Response Meta Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "AIZA",
                                color = NeonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Intent Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CardSurface)
                                    .border(0.5.dp, CardSurfaceBorder, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = turn.response.intent.name,
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Language & Duration
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = turn.response.language.displayName,
                                color = NeonPurple,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (turn.response.executionDurationMs > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${turn.response.executionDurationMs}ms",
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Text Body
                    Text(
                        text = turn.response.text,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    // Security Authorization Request Card if needed
                    if (turn.response.requiresConfirmation && turn.response.pendingActionId != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SecurityConfirmationCard(
                            confirmationId = turn.response.pendingActionId,
                            description = turn.response.pendingActionDescription ?: "Authorization required",
                            onAuthorize = onAuthorize
                        )
                    }

                    // Tool Result Info Card
                    if (turn.response.toolResult != null && !turn.response.requiresConfirmation) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ToolResultCard(turn.response.toolResult)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityConfirmationCard(
    confirmationId: String,
    description: String,
    onAuthorize: (confirmationId: String, approved: Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF26180E)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AmberAlert, RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = AmberAlert,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SECURITY CLEARANCE REQUIRED",
                    color = AmberAlert,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                color = TextPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { onAuthorize(confirmationId, false) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonAlert),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("deny_action_button")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("DENY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { onAuthorize(confirmationId, true) },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = CosmicDarkBg),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("authorize_action_button")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AUTHORIZE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ToolResultCard(toolResult: ToolResult) {
    val isSuccess = toolResult is ToolResult.Success
    val borderColor = if (isSuccess) EmeraldSuccess.copy(alpha = 0.5f) else CrimsonAlert.copy(alpha = 0.5f)
    val icon = if (isSuccess) Icons.Default.Check else Icons.Default.Warning

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CardSurface)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSuccess) EmeraldSuccess else CrimsonAlert,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isSuccess) "Module execution verified" else "Module execution error",
                color = if (isSuccess) EmeraldSuccess else CrimsonAlert,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun AizaEmptyState(
    onSamplePromptClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Core Orb
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(NeonCyan, Color(0xFF0369A1), CosmicDarkBg)
                    )
                )
                .border(2.dp, NeonCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AIZA",
                color = CosmicDarkBg,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "AIZA CORE ONLINE",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Autonomous Personal AI Assistant for Asik",
            color = ElectricBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Calm • Intelligent • Confident • Multi-Lingual Foundation",
            color = TextMuted,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Suggested prompts header
        Text(
            text = "QUICK PROMPTS ACROSS LANGUAGES",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(10.dp))

        val promptList = listOf(
            "Device battery status" to "English (Hardware Inspector)",
            "Kaisa chal raha hai Aiza?" to "Hinglish (Conversational)",
            "आज क्या महत्वपूर्ण काम बाकी है?" to "Hindi (Reminders & Tasks)",
            "কেমন আছো আইজা? সিস্টেম ঠিক আছে?" to "Bengali (Core State)",
            "Send message to Rahul: Meeting at 5pm" to "Sensitive Action (Requires Auth)"
        )

        promptList.forEach { (prompt, label) ->
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, CardSurfaceBorder, RoundedCornerShape(8.dp))
                    .clickable { onSamplePromptClick(prompt) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prompt,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = label,
                            color = ElectricBlue,
                            fontSize = 10.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSuggestionsRow(onSelectPrompt: (String) -> Unit) {
    val quickChips = listOf(
        "Battery status",
        "Set reminder: Call doctor",
        "Send message to Samir: I'm on my way",
        "Delete file old_logs.txt",
        "তুমি কেমন আছো?",
        "Subah ka routine shuru karo"
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepSpaceSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(quickChips) { chip ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, CardSurfaceBorder, RoundedCornerShape(16.dp))
                    .clickable { onSelectPrompt(chip) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = chip,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun VoiceInteractionHUD(
    voiceStatus: VoiceStatusSnapshot,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit,
    onDismissError: () -> Unit
) {
    AnimatedVisibility(visible = voiceStatus.state != VoiceState.IDLE) {
        val containerBg = when (voiceStatus.state) {
            VoiceState.LISTENING -> CrimsonAlert.copy(alpha = 0.15f)
            VoiceState.PROCESSING -> ElectricBlue.copy(alpha = 0.15f)
            VoiceState.SPEAKING -> NeonCyan.copy(alpha = 0.15f)
            VoiceState.ERROR -> CrimsonAlert.copy(alpha = 0.25f)
            VoiceState.IDLE -> CardSurface
        }

        val borderColor = when (voiceStatus.state) {
            VoiceState.LISTENING -> CrimsonAlert
            VoiceState.PROCESSING -> ElectricBlue
            VoiceState.SPEAKING -> NeonCyan
            VoiceState.ERROR -> CrimsonAlert
            VoiceState.IDLE -> CardSurfaceBorder
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DeepSpaceSurface)
                .border(1.dp, borderColor)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("voice_interaction_hud")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(containerBg)
                            .border(1.dp, borderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (voiceStatus.state) {
                                VoiceState.LISTENING -> Icons.Default.Mic
                                VoiceState.SPEAKING -> Icons.Default.VolumeUp
                                VoiceState.PROCESSING -> Icons.Default.Refresh
                                VoiceState.ERROR -> Icons.Default.Warning
                                VoiceState.IDLE -> Icons.Default.MicOff
                            },
                            contentDescription = null,
                            tint = borderColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (voiceStatus.state) {
                                VoiceState.LISTENING -> "LISTENING TO ASIK..."
                                VoiceState.PROCESSING -> "PROCESSING AUDIO INPUT..."
                                VoiceState.SPEAKING -> "AIZA IS SPEAKING..."
                                VoiceState.ERROR -> "VOICE SYSTEM NOTICE"
                                VoiceState.IDLE -> "IDLE"
                            },
                            color = borderColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp
                        )

                        val subText = when (voiceStatus.state) {
                            VoiceState.LISTENING -> if (voiceStatus.partialText.isNotBlank()) "\"${voiceStatus.partialText}\"" else "Speak naturally to Aiza..."
                            VoiceState.PROCESSING -> if (voiceStatus.partialText.isNotBlank()) "\"${voiceStatus.partialText}\"" else "Analyzing intent..."
                            VoiceState.SPEAKING -> "Tap Stop to interrupt speech immediately"
                            VoiceState.ERROR -> voiceStatus.error?.userMessage ?: "An error occurred in the voice pipeline."
                            VoiceState.IDLE -> ""
                        }

                        if (subText.isNotBlank()) {
                            Text(
                                text = subText,
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                maxLines = 2
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action controls depending on voice state
                when (voiceStatus.state) {
                    VoiceState.LISTENING -> {
                        Button(
                            onClick = onStopListening,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("stop_listening_button")
                        ) {
                            Text("DONE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    VoiceState.SPEAKING -> {
                        Button(
                            onClick = onStopSpeaking,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("stop_speaking_button")
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop Speaking", tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("STOP", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    VoiceState.ERROR -> {
                        IconButton(
                            onClick = onDismissError,
                            modifier = Modifier.size(28.dp).testTag("dismiss_voice_error_button")
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun AizaInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isBusy: Boolean,
    voiceStatus: VoiceStatusSnapshot,
    onVoiceInputClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepSpaceSurface)
            .border(1.dp, CardSurfaceBorder)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Futuristic Microphone Voice Button
        val isListening = voiceStatus.isListening
        val isSpeaking = voiceStatus.isSpeaking

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (isListening) CrimsonAlert.copy(alpha = 0.25f)
                    else if (isSpeaking) NeonCyan.copy(alpha = 0.2f)
                    else CardSurface
                )
                .border(
                    1.dp,
                    if (isListening) CrimsonAlert
                    else if (isSpeaking) NeonCyan
                    else CardSurfaceBorder,
                    CircleShape
                )
                .clickable { onVoiceInputClick() }
                .testTag("voice_input_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Mic else if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop Listening" else if (isSpeaking) "Stop Speaking" else "Speak to Aiza",
                tint = if (isListening) CrimsonAlert else if (isSpeaking) NeonCyan else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    text = "Ask Aiza or tap mic to speak...",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = CardSurface,
                unfocusedContainerColor = CardSurface,
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = CardSurfaceBorder,
                cursorColor = NeonCyan
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("user_input_field")
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (text.isNotBlank() && !isBusy) NeonCyan else CardSurface
                )
                .clickable(enabled = text.isNotBlank() && !isBusy) { onSend() }
                .testTag("send_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send Command",
                tint = if (text.isNotBlank() && !isBusy) CosmicDarkBg else TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
