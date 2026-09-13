package com.example.aiza.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AssistantBubbleBg
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceBorder
import com.example.ui.theme.CosmicDarkBg
import com.example.ui.theme.DeepSpaceSurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UserBubbleBg

/**
 * Data model for messages rendered in the base conversation screen.
 */
data class ChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: String = "Now"
)

/**
 * Base Compose Screen for Aiza featuring:
 * 1. Futuristic header bar with assistant status
 * 2. Conversation area with a responsive chat message list
 * 3. Bottom input field with send affordance
 */
@Composable
fun AizaBaseConversationScreen(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    assistantStatusText: String = "ONLINE // STANDBY",
    userName: String = "Asik",
    isGenerating: Boolean = false
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = CosmicDarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Futuristic Header Bar
            BaseScreenHeader(
                assistantStatusText = assistantStatusText,
                userName = userName
            )

            // Conversation Area (Chat Message List)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("conversation_area")
            ) {
                if (messages.isEmpty()) {
                    BaseEmptyConversationView()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .testTag("chat_message_list"),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            BaseChatMessageItem(message = message)
                        }
                    }
                }
            }

            // Bottom Input Field Area
            BaseMessageInputBar(
                inputText = inputText,
                onInputTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() && !isGenerating) {
                        onSendMessage(inputText.trim())
                        inputText = ""
                    }
                },
                isSendEnabled = inputText.isNotBlank() && !isGenerating
            )
        }
    }
}

@Composable
private fun BaseScreenHeader(
    assistantStatusText: String,
    userName: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepSpaceSurface)
            .border(width = 1.dp, color = CardSurfaceBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Futuristic Nexus Icon
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0F2644), Color(0xFF0284C7))
                        )
                    )
                    .border(1.dp, NeonCyan, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    color = NeonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "AIZA",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "PERSONAL AI // $userName",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Status Indicator Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CardSurface)
                .border(1.dp, CardSurfaceBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(NeonCyan)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = assistantStatusText,
                color = NeonCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun BaseEmptyConversationView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Futuristic Core Insignia
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(NeonCyan, Color(0xFF0369A1), CosmicDarkBg)
                    )
                )
                .border(1.5.dp, NeonCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AIZA",
                color = CosmicDarkBg,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "AIZA CORE INITIALIZED",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Standby for commands. Type a message below to begin.",
            color = TextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun BaseChatMessageItem(message: ChatMessage) {
    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.82f else 0.88f)
                .clip(
                    RoundedCornerShape(
                        topStart = if (isUser) 14.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 14.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp
                    )
                )
                .background(if (isUser) UserBubbleBg else AssistantBubbleBg)
                .border(
                    width = 1.dp,
                    color = if (isUser) CardSurfaceBorder else NeonCyan.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(
                        topStart = if (isUser) 14.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 14.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp
                    )
                )
                .padding(12.dp)
                .testTag(if (isUser) "user_message_bubble" else "assistant_message_bubble")
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.senderName.uppercase(),
                        color = if (isUser) ElectricBlue else NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = message.timestamp,
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = message.text,
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun BaseMessageInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isSendEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepSpaceSurface)
            .border(1.dp, CardSurfaceBorder)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputTextChange,
            placeholder = {
                Text(
                    text = "Message Aiza...",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            maxLines = 4,
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
                .testTag("base_input_field")
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isSendEnabled) NeonCyan else CardSurface)
                .clickable(enabled = isSendEnabled) { onSend() }
                .testTag("base_send_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send Message",
                tint = if (isSendEnabled) CosmicDarkBg else TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AizaBaseConversationScreenPreview() {
    MyApplicationTheme {
        AizaBaseConversationScreen(
            messages = listOf(
                ChatMessage(
                    id = "1",
                    senderName = "Asik",
                    text = "What is our current mission status?",
                    isUser = true,
                    timestamp = "10:42 AM"
                ),
                ChatMessage(
                    id = "2",
                    senderName = "Aiza",
                    text = "All systems operational, Asik Sir. Gemini 2.5 neural provider is online and ready for your commands.",
                    isUser = false,
                    timestamp = "10:42 AM"
                )
            ),
            onSendMessage = {}
        )
    }
}
