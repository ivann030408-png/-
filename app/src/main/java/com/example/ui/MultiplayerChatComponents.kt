package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.RoomMessage
import com.example.ui.theme.ArenaSkyCyan
import com.example.ui.theme.BeakAmber
import com.example.ui.theme.CombCrimson
import com.example.ui.theme.FeatherWhite
import com.example.ui.theme.HudBorderGlow
import com.example.ui.theme.HudDarkGlass
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NitroEmerald
import com.example.ui.theme.TacticalNavy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_CHAT_LENGTH = 200

/**
 * Semi-transparent real-time chat panel for the Landscape Room Waiting Lobby.
 */
@Composable
fun LobbyChatPanel(
    messages: List<RoomMessage>,
    currentUserId: String,
    chatError: String?,
    onSendMessage: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quickPhrases = listOf(
        stringResource(R.string.chat_quick_ready),
        stringResource(R.string.chat_quick_go),
        stringResource(R.string.chat_quick_car),
        stringResource(R.string.chat_quick_gg)
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = TacticalNavy.copy(alpha = 0.66f),
        border = BorderStroke(1.2.dp, ArenaSkyCyan.copy(alpha = 0.42f)),
        modifier = modifier
            .imePadding()
            .testTag("lobby_chat_panel")
    ) {
        ChatPanelBody(
            messages = messages,
            currentUserId = currentUserId,
            chatError = chatError,
            quickPhrases = quickPhrases,
            showHeader = true,
            onClose = null,
            onSendMessage = onSendMessage,
            onDismissError = onDismissError,
            inputTestTag = "lobby_chat_input",
            sendTestTag = "lobby_chat_send_button"
        )
    }
}

/**
 * Compact 48dp semi-transparent HUD toggle button with live unread badge for in-match multiplayer chat.
 */
@Composable
fun InMatchChatToggleButton(
    chatOpen: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            color = if (chatOpen) ArenaSkyCyan.copy(alpha = 0.85f) else HudDarkGlass,
            shape = CircleShape,
            border = BorderStroke(
                width = 1.2.dp,
                color = if (chatOpen) FeatherWhite else ArenaSkyCyan.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("hud_chat_toggle_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = stringResource(R.string.cd_chat_toggle),
                    tint = if (chatOpen) TacticalNavy else ArenaSkyCyan,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        if (!chatOpen && unreadCount > 0) {
            val badgeText = if (unreadCount > 9) "9+" else unreadCount.toString()
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(CombCrimson)
                    .testTag("hud_chat_unread_badge"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = FeatherWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Non-blocking semi-transparent ticker showing the latest 3 messages during active gameplay.
 * Does not intercept touch events so virtual joystick and camera look remain responsive.
 */
@Composable
fun InMatchChatTicker(
    messages: List<RoomMessage>,
    currentUserId: String,
    modifier: Modifier = Modifier
) {
    val recent = remember(messages) { messages.takeLast(3) }
    if (recent.isEmpty()) return

    Column(
        modifier = modifier
            .widthIn(max = 265.dp)
            .testTag("hud_chat_ticker"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        recent.forEach { msg ->
            val isSelf = msg.senderId == currentUserId
            Surface(
                color = TacticalNavy.copy(alpha = 0.46f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    width = 0.6.dp,
                    color = if (isSelf) BeakAmber.copy(alpha = 0.35f) else ArenaSkyCyan.copy(alpha = 0.30f)
                )
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = if (isSelf) BeakAmber else ArenaSkyCyan,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(msg.senderName)
                            append(": ")
                        }
                        withStyle(SpanStyle(color = FeatherWhite.copy(alpha = 0.94f))) {
                            append(msg.text)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * Semi-transparent floating landscape chat overlay for active multiplayer matches.
 * Anchored left-of-center so the aiming reticle and right-hand fire/jump controls stay clear.
 */
@Composable
fun InMatchChatOverlay(
    messages: List<RoomMessage>,
    currentUserId: String,
    chatError: String?,
    onSendMessage: (String) -> Unit,
    onDismissError: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quickPhrases = listOf(
        stringResource(R.string.chat_quick_car),
        stringResource(R.string.chat_quick_cover),
        stringResource(R.string.chat_quick_go),
        stringResource(R.string.chat_quick_gg)
    )
    val consumeClicksSource = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TacticalNavy.copy(alpha = 0.72f),
        border = BorderStroke(1.2.dp, ArenaSkyCyan.copy(alpha = 0.55f)),
        modifier = modifier
            .widthIn(min = 270.dp, max = 350.dp)
            .fillMaxHeight(0.76f)
            .imePadding()
            .clickable(
                interactionSource = consumeClicksSource,
                indication = null,
                onClick = {}
            )
            .testTag("hud_chat_overlay")
    ) {
        ChatPanelBody(
            messages = messages,
            currentUserId = currentUserId,
            chatError = chatError,
            quickPhrases = quickPhrases,
            showHeader = true,
            onClose = onClose,
            onSendMessage = onSendMessage,
            onDismissError = onDismissError,
            inputTestTag = "hud_chat_input",
            sendTestTag = "hud_chat_send_button"
        )
    }
}

@Composable
private fun ChatPanelBody(
    messages: List<RoomMessage>,
    currentUserId: String,
    chatError: String?,
    quickPhrases: List<String>,
    showHeader: Boolean,
    onClose: (() -> Unit)?,
    onSendMessage: (String) -> Unit,
    onDismissError: () -> Unit,
    inputTestTag: String,
    sendTestTag: String
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun submitDraft() {
        val cleaned = draft.trim()
        if (cleaned.isNotEmpty()) {
            onSendMessage(cleaned)
            draft = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = ArenaSkyCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.chat_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = FeatherWhite,
                        fontWeight = FontWeight.Bold
                    )
                    if (messages.isNotEmpty()) {
                        Surface(
                            color = ArenaSkyCyan.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = messages.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = ArenaSkyCyan,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("hud_chat_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_chat_close),
                            tint = FeatherWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (chatError != null) {
            Surface(
                onClick = onDismissError,
                color = CombCrimson.copy(alpha = 0.28f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CombCrimson.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = chatError,
                    style = MaterialTheme.typography.labelSmall,
                    color = FeatherWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Scrollable message list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.22f))
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.chat_empty),
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedSteel
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(messages, key = { it.id.ifBlank { "${it.senderId}_${it.text.hashCode()}" } }) { msg ->
                        ChatMessageBubble(
                            message = msg,
                            isSelf = msg.senderId == currentUserId
                        )
                    }
                }
            }
        }

        // Quick tactical callout chips (1-tap send without needing the keyboard in landscape)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickPhrases.forEachIndexed { idx, phrase ->
                Surface(
                    onClick = { onSendMessage(phrase) },
                    color = Color.White.copy(alpha = 0.11f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.8.dp, HudBorderGlow),
                    modifier = Modifier.testTag("chat_quick_chip_$idx")
                ) {
                    Text(
                        text = phrase,
                        style = MaterialTheme.typography.labelSmall,
                        color = FeatherWhite,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Compact landscape input row + 48dp Send button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.36f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ArenaSkyCyan.copy(alpha = 0.45f)),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_placeholder),
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSteel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.length <= MAX_CHAT_LENGTH) draft = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = FeatherWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(BeakAmber),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitDraft() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(inputTestTag)
                    )
                }
            }

            val canSend = draft.isNotBlank()
            Surface(
                onClick = { submitDraft() },
                enabled = canSend,
                color = if (canSend) NitroEmerald else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .size(48.dp)
                    .testTag(sendTestTag)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_chat_send),
                        tint = if (canSend) TacticalNavy else MutedSteel,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: RoomMessage,
    isSelf: Boolean
) {
    val timeLabel = remember(message.createdAt) {
        val date: Date? = message.createdAt?.toDate()
        if (date != null) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        } else {
            ""
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelf) BeakAmber.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(
            width = 0.8.dp,
            color = if (isSelf) BeakAmber.copy(alpha = 0.42f) else ArenaSkyCyan.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSelf) "${message.senderName} (Вы)" else message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelf) BeakAmber else ArenaSkyCyan,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (timeLabel.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSteel,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                color = FeatherWhite,
                fontSize = 12.sp,
                lineHeight = 15.sp
            )
        }
    }
}
