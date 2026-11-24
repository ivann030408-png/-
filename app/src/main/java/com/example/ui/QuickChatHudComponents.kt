package com.example.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.RoomMessage
import com.example.ui.theme.ArenaSkyCyan
import com.example.ui.theme.BeakAmber
import com.example.ui.theme.CombCrimson
import com.example.ui.theme.ExplosiveOrange
import com.example.ui.theme.FeatherWhite
import com.example.ui.theme.HudBorderGlow
import com.example.ui.theme.HudDarkGlass
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NitroEmerald
import com.example.ui.theme.TacticalNavy

/**
 * Pre-defined tactical and sandbox Quick Chat presets for fast 1-tap coordination in matches.
 */
enum class QuickChatPreset(
    val id: String,
    @StringRes val textRes: Int,
    val fallbackText: String,
    val icon: ImageVector,
    val accent: Color
) {
    HELP(
        id = "help",
        textRes = R.string.quick_chat_msg_help,
        fallbackText = "Помогите!",
        icon = Icons.Filled.Warning,
        accent = CombCrimson
    ),
    BUILD(
        id = "build",
        textRes = R.string.quick_chat_msg_build,
        fallbackText = "Давайте строить!",
        icon = Icons.Filled.Construction,
        accent = BeakAmber
    ),
    FOLLOW(
        id = "follow",
        textRes = R.string.quick_chat_msg_follow,
        fallbackText = "За мной!",
        icon = Icons.Filled.NearMe,
        accent = ArenaSkyCyan
    ),
    CAR(
        id = "car",
        textRes = R.string.quick_chat_msg_car,
        fallbackText = "В машину!",
        icon = Icons.Filled.DirectionsCar,
        accent = NitroEmerald
    ),
    COVER(
        id = "cover",
        textRes = R.string.quick_chat_msg_cover,
        fallbackText = "Прикрой меня!",
        icon = Icons.Filled.Shield,
        accent = ExplosiveOrange
    ),
    ATTACK(
        id = "attack",
        textRes = R.string.quick_chat_msg_attack,
        fallbackText = "В атаку!",
        icon = Icons.Filled.GpsFixed,
        accent = CombCrimson
    ),
    REGROUP(
        id = "regroup",
        textRes = R.string.quick_chat_msg_regroup,
        fallbackText = "Сбор в центре!",
        icon = Icons.Filled.Flag,
        accent = ArenaSkyCyan
    ),
    GG(
        id = "gg",
        textRes = R.string.quick_chat_msg_gg,
        fallbackText = "Отличная игра!",
        icon = Icons.Filled.ThumbUp,
        accent = NitroEmerald
    );

    companion object {
        fun matchByText(text: String): QuickChatPreset? {
            val clean = text.trim()
            return entries.firstOrNull { it.fallbackText.equals(clean, ignoreCase = true) }
        }
    }
}

/**
 * Compact 48dp HUD Quick Chat button matching top-left HudMiniIconButton geometry,
 * with active highlight and live unread badge for incoming online messages.
 */
@Composable
fun QuickChatHudButton(
    isOpen: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isOpen) ArenaSkyCyan else HudDarkGlass)
                .border(
                    width = if (isOpen) 1.5.dp else 1.dp,
                    color = if (isOpen) FeatherWhite else ArenaSkyCyan.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onClick)
                .testTag("hud_quick_chat_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isOpen) Icons.Filled.Close else Icons.AutoMirrored.Filled.Chat,
                contentDescription = stringResource(
                    if (isOpen) R.string.cd_quick_chat_close else R.string.cd_quick_chat_button
                ),
                tint = if (isOpen) TacticalNavy else ArenaSkyCyan,
                modifier = Modifier.size(21.dp)
            )
        }

        if (!isOpen && unreadCount > 0) {
            val badgeText = if (unreadCount > 9) "9+" else unreadCount.toString()
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(CombCrimson)
                    .border(1.dp, FeatherWhite.copy(alpha = 0.85f), CircleShape)
                    .testTag("hud_quick_chat_unread_badge"),
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
 * Compact floating Quick Chat panel anchored under the top-left HUD bar.
 * Presents a 2-column grid of 1-tap 48dp tactical callouts without obstructing crosshair or combat controls.
 */
@Composable
fun QuickChatPanelOverlay(
    visible: Boolean,
    recentMessages: List<RoomMessage>,
    currentUserId: String,
    isOnlineMatch: Boolean,
    cooldownRemainingMs: Long,
    onSelectMessage: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
            scaleIn(
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(0f, 0f),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
            scaleOut(
                targetScale = 0.94f,
                transformOrigin = TransformOrigin(0f, 0f)
            ),
        modifier = modifier
    ) {
        val consumeClicks = remember { MutableInteractionSource() }
        val inCooldown = cooldownRemainingMs > 0L
        val presetPairs = remember { QuickChatPreset.entries.chunked(2) }
        val latestHistory = remember(recentMessages) { recentMessages.takeLast(4) }
        var customChatText by remember { mutableStateOf("") }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = BorderStroke(1.2.dp, ArenaSkyCyan.copy(alpha = 0.58f)),
            modifier = Modifier
                .widthIn(min = 286.dp, max = 360.dp)
                .clickable(
                    interactionSource = consumeClicks,
                    indication = null,
                    onClick = {}
                )
                .testTag("hud_quick_chat_panel")
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                TacticalNavy.copy(alpha = 0.90f),
                                Color(0xE60B1528)
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header: Title + Online/Local status chip + Close button
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
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.quick_chat_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = FeatherWhite,
                            letterSpacing = 0.6.sp
                        )
                        Surface(
                            color = if (isOnlineMatch) NitroEmerald.copy(alpha = 0.22f) else MutedSteel.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(
                                0.8.dp,
                                if (isOnlineMatch) NitroEmerald.copy(alpha = 0.7f) else MutedSteel.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = stringResource(
                                    if (isOnlineMatch) R.string.quick_chat_mode_online else R.string.quick_chat_mode_local
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOnlineMatch) NitroEmerald else MutedSteel,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onClose)
                            .testTag("quick_chat_close_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_quick_chat_close),
                            tint = FeatherWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Free-Text Chat Input Row + Send Button (syncs in real time via Firestore messages)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val canSendCustom = !inCooldown && customChatText.trim().isNotEmpty()
                    OutlinedTextField(
                        value = customChatText,
                        onValueChange = { if (it.length <= 160) customChatText = it },
                        placeholder = {
                            Text(
                                text = "Написать в чат…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSteel
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val trimmed = customChatText.trim()
                                if (!inCooldown && trimmed.isNotEmpty()) {
                                    onSelectMessage(trimmed)
                                    customChatText = ""
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArenaSkyCyan,
                            unfocusedBorderColor = MutedSteel.copy(alpha = 0.45f),
                            focusedTextColor = FeatherWhite,
                            unfocusedTextColor = FeatherWhite,
                            cursorColor = ArenaSkyCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hud_chat_text_input")
                    )
                    IconButton(
                        onClick = {
                            val trimmed = customChatText.trim()
                            if (canSendCustom) {
                                onSelectMessage(trimmed)
                                customChatText = ""
                            }
                        },
                        enabled = canSendCustom,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canSendCustom) NitroEmerald else MutedSteel.copy(alpha = 0.25f))
                            .testTag("hud_chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить сообщение",
                            tint = if (canSendCustom) TacticalNavy else MutedSteel
                        )
                    }
                }

                // 2-Column Grid of 1-Tap 48dp Tactical Quick Chat Buttons
                presetPairs.forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            val label = stringResource(preset.textRes)
                            Surface(
                                onClick = {
                                    if (!inCooldown) onSelectMessage(label)
                                },
                                enabled = !inCooldown,
                                color = preset.accent.copy(alpha = if (inCooldown) 0.08f else 0.16f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = preset.accent.copy(alpha = if (inCooldown) 0.28f else 0.68f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("quick_chat_item_${preset.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(preset.accent.copy(alpha = 0.24f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = preset.icon,
                                            contentDescription = null,
                                            tint = preset.accent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (inCooldown) FeatherWhite.copy(alpha = 0.55f) else FeatherWhite,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (inCooldown) {
                    Text(
                        text = stringResource(R.string.quick_chat_cooldown_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = BeakAmber,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                // Compact recent room messages preview (last 3) for quick match context
                if (latestHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.quick_chat_recent_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSteel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.26f))
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                    ) {
                        latestHistory.forEach { msg ->
                            val isSelf = msg.senderId == currentUserId
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
                                    withStyle(SpanStyle(color = FeatherWhite.copy(alpha = 0.92f))) {
                                        append(msg.text)
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Non-blocking auto-fading live feed showing recent incoming/outgoing Quick Chat callouts
 * beneath the top-left HUD bar. Never intercepts touch events.
 */
@Composable
fun QuickChatLiveFeed(
    messages: List<RoomMessage>,
    currentUserId: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = messages.isNotEmpty(),
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 3 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 3 }),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 264.dp)
                .testTag("hud_quick_chat_feed"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            messages.takeLast(3).forEach { msg ->
                val isSelf = msg.senderId == currentUserId
                val matchedPreset = remember(msg.text) { QuickChatPreset.matchByText(msg.text) }
                val accent = matchedPreset?.accent ?: if (isSelf) BeakAmber else ArenaSkyCyan
                Surface(
                    color = TacticalNavy.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.9.dp, accent.copy(alpha = 0.52f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = matchedPreset?.icon ?: Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = if (isSelf) BeakAmber else ArenaSkyCyan,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                ) {
                                    append(msg.senderName)
                                    append(": ")
                                }
                                withStyle(
                                    SpanStyle(
                                        color = FeatherWhite,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                ) {
                                    append(msg.text)
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
