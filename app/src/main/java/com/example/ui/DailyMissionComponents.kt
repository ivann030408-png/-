package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.DailyMission
import com.example.data.DailyMissionType
import com.example.data.UiState
import com.example.ui.theme.ArenaSkyCyan
import com.example.ui.theme.BeakAmber
import com.example.ui.theme.BunkerSlate
import com.example.ui.theme.CombCrimson
import com.example.ui.theme.ExplosiveOrange
import com.example.ui.theme.FeatherWhite
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NitroEmerald
import com.example.ui.theme.SteelSurface
import com.example.ui.theme.TacticalNavy
import kotlinx.coroutines.delay

@Composable
fun CoinsBalanceBadge(
    coins: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlassPanelFill,
        border = BorderStroke(1.dp, BeakAmber.copy(alpha = 0.50f)),
        modifier = modifier.testTag("coins_balance_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MonetizationOn,
                contentDescription = stringResource(R.string.cd_coins_balance),
                tint = BeakAmber,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = coins.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = BeakAmber
            )
        }
    }
}

@Composable
fun DailyMissionsCard(
    state: UiState<List<DailyMission>>,
    claimingMissionId: String?,
    feedbackMessage: String?,
    onClearFeedback: () -> Unit,
    onClaimMission: (String) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(2600L)
            onClearFeedback()
        }
    }

    val missions = (state as? UiState.Success)?.data.orEmpty()
    val doneCount = missions.count { it.status == "completed" || it.status == "claimed" }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = GlassPanelFill,
        border = BorderStroke(1.dp, GlassBorderLight),
        modifier = modifier
            .fillMaxWidth()
            .testTag("main_menu_missions_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TaskAlt,
                        contentDescription = stringResource(R.string.cd_missions_icon),
                        tint = NitroEmerald,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.missions_panel_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = FeatherWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NitroEmerald.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, NitroEmerald.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "$doneCount/3",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = NitroEmerald,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                IconButton(
                    onClick = onExpand,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("missions_expand_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = stringResource(R.string.cd_missions_expand),
                        tint = ArenaSkyCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = feedbackMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (feedbackMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BeakAmber.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, BeakAmber.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = feedbackMessage,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BeakAmber,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                DailyMissionsBody(
                    state = state,
                    claimingMissionId = claimingMissionId,
                    compact = true,
                    onClaimMission = onClaimMission
                )
            }
        }
    }
}

@Composable
fun DailyMissionsDialog(
    state: UiState<List<DailyMission>>,
    coins: Long,
    claimingMissionId: String?,
    feedbackMessage: String?,
    onClaimMission: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = BunkerSlate.copy(alpha = 0.97f),
            border = BorderStroke(1.8.dp, NitroEmerald.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .fillMaxHeight(0.84f)
                .testTag("missions_full_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.TaskAlt,
                            contentDescription = stringResource(R.string.cd_missions_icon),
                            tint = NitroEmerald,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.missions_panel_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = FeatherWhite
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoinsBalanceBadge(coins = coins)
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("missions_dialog_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_missions_close),
                                tint = FeatherWhite
                            )
                        }
                    }
                }

                if (feedbackMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BeakAmber.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, BeakAmber.copy(alpha = 0.75f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = feedbackMessage,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = BeakAmber,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    DailyMissionsBody(
                        state = state,
                        claimingMissionId = claimingMissionId,
                        compact = false,
                        onClaimMission = onClaimMission
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyMissionsBody(
    state: UiState<List<DailyMission>>,
    claimingMissionId: String?,
    compact: Boolean,
    onClaimMission: (String) -> Unit
) {
    when (state) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = NitroEmerald,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = CombCrimson,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = state.message.ifBlank { stringResource(R.string.missions_error) },
                        style = MaterialTheme.typography.bodySmall,
                        color = CombCrimson,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        is UiState.Success -> {
            if (state.data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.missions_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSteel,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("daily_missions_list")
                ) {
                    items(
                        items = state.data,
                        key = { it.id.ifBlank { "${it.dayKey}_${it.slotIndex}" } }
                    ) { mission ->
                        DailyMissionItemCard(
                            mission = mission,
                            claiming = claimingMissionId == mission.id,
                            compact = compact,
                            onClaim = { onClaimMission(mission.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyMissionItemCard(
    mission: DailyMission,
    claiming: Boolean,
    compact: Boolean,
    onClaim: () -> Unit
) {
    val type = mission.resolvedType()
    val accent = missionAccentColor(type)
    val isClaimed = mission.status == "claimed"
    val isCompleted = mission.status == "completed"
    val rawFraction = if (mission.target > 0L) {
        (mission.progress.toFloat() / mission.target.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
        label = "mission_progress"
    )

    val containerColor = when {
        isCompleted -> NitroEmerald.copy(alpha = 0.16f)
        isClaimed -> Color(0xFF0A1220).copy(alpha = 0.30f)
        else -> Color(0xFF0A1220).copy(alpha = 0.44f)
    }
    val borderColor = when {
        isCompleted -> NitroEmerald.copy(alpha = 0.65f)
        isClaimed -> FeatherWhite.copy(alpha = 0.10f)
        else -> FeatherWhite.copy(alpha = 0.14f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_mission_item_${mission.slotIndex}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 8.dp else 14.dp, vertical = if (compact) 6.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 32.dp else 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = if (isClaimed) 0.12f else 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = missionIcon(type),
                    contentDescription = null,
                    tint = if (isClaimed) MutedSteel else accent,
                    modifier = Modifier.size(if (compact) 18.dp else 24.dp)
                )
            }

            Spacer(modifier = Modifier.width(if (compact) 8.dp else 12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mission.title.ifBlank { type.title },
                        style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isClaimed) MutedSteel else FeatherWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${mission.progress}/${mission.target}${type.unitSuffix}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted || isClaimed) NitroEmerald else accent
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = if (isCompleted || isClaimed) NitroEmerald else accent,
                    trackColor = SteelSurface.copy(alpha = 0.55f),
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 5.dp else 7.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.width(if (compact) 8.dp else 12.dp))

            when {
                isCompleted -> {
                    Button(
                        onClick = onClaim,
                        enabled = !claiming,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NitroEmerald,
                            contentColor = TacticalNavy
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(if (compact) 36.dp else 44.dp)
                            .testTag("claim_mission_${mission.slotIndex}")
                    ) {
                        if (claiming) {
                            CircularProgressIndicator(
                                color = TacticalNavy,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.MonetizationOn,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+${mission.rewardCoins}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                isClaimed -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NitroEmerald.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = NitroEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.missions_claimed_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NitroEmerald
                            )
                        }
                    }
                }

                else -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BeakAmber.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, BeakAmber.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MonetizationOn,
                                contentDescription = null,
                                tint = BeakAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "+${mission.rewardCoins}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = BeakAmber
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun missionIcon(type: DailyMissionType): ImageVector = when (type) {
    DailyMissionType.KILLS -> Icons.Filled.MilitaryTech
    DailyMissionType.DRIVE_METERS -> Icons.Filled.DirectionsCar
    DailyMissionType.BUILD_PROPS -> Icons.Filled.Build
    DailyMissionType.DESTROY_PROPS -> Icons.Filled.Whatshot
    DailyMissionType.ROADKILL -> Icons.Filled.DirectionsCar
    DailyMissionType.PLAY_MATCHES -> Icons.Filled.SportsEsports
    DailyMissionType.WIN_MATCH -> Icons.Filled.EmojiEvents
    DailyMissionType.EXPLOSIONS -> Icons.Filled.LocalFireDepartment
}

private fun missionAccentColor(type: DailyMissionType): Color = when (type) {
    DailyMissionType.KILLS -> ExplosiveOrange
    DailyMissionType.DRIVE_METERS -> ArenaSkyCyan
    DailyMissionType.BUILD_PROPS -> NitroEmerald
    DailyMissionType.DESTROY_PROPS -> BeakAmber
    DailyMissionType.ROADKILL -> ExplosiveOrange
    DailyMissionType.PLAY_MATCHES -> ArenaSkyCyan
    DailyMissionType.WIN_MATCH -> BeakAmber
    DailyMissionType.EXPLOSIONS -> CombCrimson
}
