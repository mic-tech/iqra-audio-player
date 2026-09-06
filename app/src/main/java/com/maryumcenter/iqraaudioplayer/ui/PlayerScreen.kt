package com.maryumcenter.iqraaudioplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maryumcenter.iqraaudioplayer.PlayerUiState
import com.maryumcenter.iqraaudioplayer.R
import com.maryumcenter.iqraaudioplayer.Progress
import com.maryumcenter.iqraaudioplayer.Track
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    progress: StateFlow<Progress>,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val subtitle = state.rootLabel
                        if (subtitle != null) {
                            Text(
                                text = "$subtitle · ${state.tracks.size} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (state.hasRoot) {
                        // A toggle rather than a plain action: off it looks like
                        // its neighbours, on it gets a filled container, so the
                        // state is readable at a glance across the room.
                        FilledIconToggleButton(
                            checked = state.repeatAll,
                            onCheckedChange = { onToggleRepeat() },
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = LocalContentColor.current,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Repeat,
                                contentDescription = if (state.repeatAll) {
                                    "Repeat all, on. Tap to stop at the last track."
                                } else {
                                    "Repeat all, off. Tap to loop the folder."
                                },
                            )
                        }
                        IconButton(onClick = onRescan, enabled = !state.scanning) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Rescan folder")
                        }
                    }
                    IconButton(onClick = onPickFolder) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Choose root folder")
                    }
                },
            )
        },
        bottomBar = {
            if (state.tracks.isNotEmpty()) {
                NowPlayingBar(
                    state = state,
                    progress = progress,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeekBack = onSeekBack,
                    onSeekForward = onSeekForward,
                    onSeekTo = onSeekTo,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.hasRoot -> EmptyState(
                    icon = Icons.Filled.Folder,
                    title = "No folder chosen yet",
                    message = "Pick a folder and every audio file inside it — including " +
                        "sub-folders — is queued up in alphabetical order.",
                    actionLabel = "Choose folder",
                    onAction = onPickFolder,
                )

                state.scanning && state.tracks.isEmpty() -> ScanningState()

                state.tracks.isEmpty() -> EmptyState(
                    icon = Icons.Filled.MusicNote,
                    title = "No audio files found",
                    message = "Nothing playable turned up under this folder. " +
                        "Try a different one, or rescan if you have just added files.",
                    actionLabel = "Choose another folder",
                    onAction = onPickFolder,
                )

                else -> TrackList(
                    tracks = state.tracks,
                    currentIndex = state.currentIndex,
                    isPlaying = state.isPlaying,
                    onPlayTrack = onPlayTrack,
                )
            }

            if (state.scanning && state.tracks.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    onPlayTrack: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    // Follow the player as it moves through the queue.
    LaunchedEffect(currentIndex) {
        if (currentIndex in tracks.indices) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val alreadyVisible = visible.any { it.index == currentIndex }
            if (!alreadyVisible) listState.animateScrollToItem(currentIndex)
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, track -> track.uri }) { index, track ->
            val previousFolder = tracks.getOrNull(index - 1)?.folder
            if (index == 0 || previousFolder != track.folder) {
                FolderHeader(track.folder)
            }
            TrackRow(
                track = track,
                position = index + 1,
                isCurrent = index == currentIndex,
                isPlaying = isPlaying,
                onClick = { onPlayTrack(index) },
            )
        }
    }
}

@Composable
private fun FolderHeader(folder: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = folder.ifEmpty { "Root folder" },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun TrackRow(
    track: Track,
    position: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val background =
        if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor =
        if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            if (isCurrent && isPlaying) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = "Now playing",
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun NowPlayingBar(
    state: PlayerUiState,
    progress: StateFlow<Progress>,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    // While the user drags the slider we show their position, not the player's.
    var scrubPosition by remember { mutableStateOf<Float?>(null) }

    // Collected here rather than in PlayerScreen: this updates several times a
    // second and only these few lines should redraw with it.
    val current by progress.collectAsStateWithLifecycle()

    val track = state.currentTrack
    val duration = current.durationMs
    val position = scrubPosition?.toLong() ?: current.positionMs

    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = track?.title ?: "Nothing playing",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track?.folder?.ifEmpty { state.rootLabel ?: "Root folder" } ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Slider(
                value = position.coerceIn(0L, maxOf(duration, 0L)).toFloat(),
                onValueChange = { scrubPosition = it },
                onValueChangeFinished = {
                    scrubPosition?.let { onSeekTo(it.toLong()) }
                    scrubPosition = null
                },
                valueRange = 0f..maxOf(duration, 1L).toFloat(),
                enabled = duration > 0L,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatTime(position), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(4.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track")
                }
                IconButton(onClick = onSeekBack) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds")
                }
                FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(60.dp)) {
                    Icon(
                        imageVector =
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onSeekForward) {
                    Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds")
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next track")
                }
            }
        }
    }
}

@Composable
private fun ScanningState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Scanning folder…", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
