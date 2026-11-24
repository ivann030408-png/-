package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.LeaderboardEntry
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

enum class LeaderboardSort {
    KILLS,
    WINS
}

private val SilverRankColor = Color(0xFFD5DDE8)
private val BronzeRankColor = Color(0xFFD98B4C)

private fun sortLeaderboard(
    entries: List<LeaderboardEntry>,
    sortMode: LeaderboardSort
): List<LeaderboardEntry> {
    val comparator = when (sortMode) {
        LeaderboardSort.KILLS -> compareByDescending<LeaderboardEntry> { it.kills }
            .thenByDescending { it.wins }
            .thenByDescending { it.matchesPlayed }
            .thenBy { it.nickname.lowercase() }

        LeaderboardSort.WINS -> compareByDescending<LeaderboardEntry> { it.wins }
            .thenByDescending { it.kills }
            .thenByDescending { it.matchesPlayed }
            .thenBy { it.nickname.lowercase() }
    }
    return entries.sortedWith(comparator)
}

@Composable
fun GlobalLeaderboardCard(
    state: UiState<List<LeaderboardEntry>>,
    currentUserId: String,
    sortMode: LeaderboardSort,
    onSortModeChange: (LeaderboardSort) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = GlassPanelFill,
        border = BorderStroke(1.dp, GlassBorderLight),
        modifier = modifier
            .fillMaxWidth()
            .testTag("main_menu_leaderboard_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Header row: Trophy + Title + Sort Toggle + Expand Button
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
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = stringResource(R.string.cd_leaderboard_trophy),
                        tint = BeakAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.leaderboard_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = FeatherWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                LeaderboardSortPills(
                    sortMode = sortMode,
                    onSortModeChange = onSortModeChange
                )

                IconButton(
                    onClick = onExpand,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("leaderboard_expand_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = stringResource(R.string.cd_leaderboard_expand),
                        tint = ArenaSkyCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Column Header Strip
            LeaderboardColumnsHeader(showStreak = false)
            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LeaderboardListBody(
                    state = state,
                    currentUserId = currentUserId,
                    sortMode = sortMode,
                    maxEntries = 10,
                    showStreak = false
                )
            }
        }
    }
}

@Composable
fun GlobalLeaderboardDialog(
    state: UiState<List<LeaderboardEntry>>,
    currentUserId: String,
    sortMode: LeaderboardSort,
    onSortModeChange: (LeaderboardSort) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = BunkerSlate.copy(alpha = 0.97f),
            border = BorderStroke(1.8.dp, BeakAmber.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .fillMaxHeight(0.86f)
                .testTag("leaderboard_full_dialog")
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
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = stringResource(R.string.cd_leaderboard_trophy),
                            tint = BeakAmber,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.leaderboard_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = FeatherWhite
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LeaderboardSortPills(
                            sortMode = sortMode,
                            onSortModeChange = onSortModeChange
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("leaderboard_dialog_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_leaderboard_close),
                                tint = FeatherWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                LeaderboardColumnsHeader(showStreak = true)
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LeaderboardListBody(
                        state = state,
                        currentUserId = currentUserId,
                        sortMode = sortMode,
                        maxEntries = 25,
                        showStreak = true
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardSortPills(
    sortMode: LeaderboardSort,
    onSortModeChange: (LeaderboardSort) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = GlassPanelSoftFill,
        border = BorderStroke(1.dp, GlassBorderLight)
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SortPillOption(
                label = stringResource(R.string.leaderboard_sort_kills),
                selected = sortMode == LeaderboardSort.KILLS,
                accent = ExplosiveOrange,
                testTag = "leaderboard_sort_kills",
                onClick = { onSortModeChange(LeaderboardSort.KILLS) }
            )
            SortPillOption(
                label = stringResource(R.string.leaderboard_sort_wins),
                selected = sortMode == LeaderboardSort.WINS,
                accent = BeakAmber,
                testTag = "leaderboard_sort_wins",
                onClick = { onSortModeChange(LeaderboardSort.WINS) }
            )
        }
    }
}

@Composable
private fun SortPillOption(
    label: String,
    selected: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) TacticalNavy else MutedSteel
        )
    }
}

@Composable
private fun LeaderboardColumnsHeader(showStreak: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GlassPanelSoftFill)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#",
            style = MaterialTheme.typography.labelSmall,
            color = MutedSteel,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(26.dp)
        )
        Text(
            text = stringResource(R.string.leaderboard_col_player),
            style = MaterialTheme.typography.labelSmall,
            color = MutedSteel,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (showStreak) stringResource(R.string.stat_kills) else stringResource(R.string.leaderboard_col_kills),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = ExplosiveOrange,
            textAlign = TextAlign.End,
            modifier = Modifier.width(if (showStreak) 58.dp else 36.dp)
        )
        Text(
            text = if (showStreak) stringResource(R.string.stat_wins) else stringResource(R.string.leaderboard_col_wins),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = BeakAmber,
            textAlign = TextAlign.End,
            modifier = Modifier.width(if (showStreak) 64.dp else 36.dp)
        )
        Text(
            text = if (showStreak) stringResource(R.string.stat_matches) else stringResource(R.string.leaderboard_col_matches),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = ArenaSkyCyan,
            textAlign = TextAlign.End,
            modifier = Modifier.width(if (showStreak) 58.dp else 36.dp)
        )
        if (showStreak) {
            Text(
                text = "СЕРИЯ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = NitroEmerald,
                textAlign = TextAlign.End,
                modifier = Modifier.width(54.dp)
            )
        }
    }
}

@Composable
private fun LeaderboardListBody(
    state: UiState<List<LeaderboardEntry>>,
    currentUserId: String,
    sortMode: LeaderboardSort,
    maxEntries: Int,
    showStreak: Boolean
) {
    when (state) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = BeakAmber,
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
                        text = state.message.ifBlank { stringResource(R.string.leaderboard_error) },
                        style = MaterialTheme.typography.bodySmall,
                        color = CombCrimson,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        is UiState.Success -> {
            val sortedEntries = remember(state.data, sortMode, maxEntries) {
                sortLeaderboard(state.data, sortMode).take(maxEntries)
            }
            if (sortedEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.leaderboard_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSteel,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("leaderboard_list")
                ) {
                    itemsIndexed(
                        items = sortedEntries,
                        key = { _, item -> item.id.ifBlank { item.userId } }
                    ) { index, entry ->
                        LeaderboardRowItem(
                            rank = index + 1,
                            entry = entry,
                            isCurrentUser = entry.userId == currentUserId,
                            showStreak = showStreak
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRowItem(
    rank: Int,
    entry: LeaderboardEntry,
    isCurrentUser: Boolean,
    showStreak: Boolean
) {
    val rankColor = when (rank) {
        1 -> BeakAmber
        2 -> SilverRankColor
        3 -> BronzeRankColor
        else -> MutedSteel
    }
    val rowBg = if (isCurrentUser) {
        NitroEmerald.copy(alpha = 0.16f)
    } else if (rank <= 3) {
        rankColor.copy(alpha = 0.08f)
    } else {
        TacticalNavy.copy(alpha = 0.45f)
    }
    val rowBorder = if (isCurrentUser) {
        BorderStroke(1.dp, NitroEmerald.copy(alpha = 0.55f))
    } else if (rank == 1) {
        BorderStroke(1.dp, BeakAmber.copy(alpha = 0.4f))
    } else {
        null
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = rowBg,
        border = rowBorder,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_row_$rank")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = if (showStreak) 8.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (rank <= 3) rankColor.copy(alpha = 0.25f) else SteelSurface.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = rankColor
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = entry.nickname.ifBlank { "Боец" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isCurrentUser || rank <= 3) FontWeight.Bold else FontWeight.Medium,
                    color = FeatherWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isCurrentUser) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = NitroEmerald.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = stringResource(R.string.leaderboard_you_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = NitroEmerald,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Text(
                text = entry.kills.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ExplosiveOrange,
                textAlign = TextAlign.End,
                modifier = Modifier.width(if (showStreak) 58.dp else 36.dp)
            )
            Text(
                text = entry.wins.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = BeakAmber,
                textAlign = TextAlign.End,
                modifier = Modifier.width(if (showStreak) 64.dp else 36.dp)
            )
            Text(
                text = entry.matchesPlayed.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = ArenaSkyCyan,
                textAlign = TextAlign.End,
                modifier = Modifier.width(if (showStreak) 58.dp else 36.dp)
            )
            if (showStreak) {
                Text(
                    text = entry.bestStreak.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = NitroEmerald,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(54.dp)
                )
            }
        }
    }
}
