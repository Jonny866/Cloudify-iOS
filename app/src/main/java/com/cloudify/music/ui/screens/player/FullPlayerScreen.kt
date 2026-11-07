package com.cloudify.music.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cloudify.music.core.theme.*
import com.cloudify.music.domain.model.Track
import com.cloudify.music.service.PlaybackProgress
import com.cloudify.music.service.PlayerState
import com.cloudify.music.service.RepeatMode
import com.cloudify.music.ui.components.rememberConnectedAudioDevice
import com.cloudify.music.ui.components.ConnectedAudioDevice
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    playerState: PlayerState,
    progressFlow: StateFlow<PlaybackProgress>? = null,
    playbackProgress: PlaybackProgress = PlaybackProgress(playerState.positionMs, playerState.durationMs),
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onVolumeChange: (Float) -> Unit = {},
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onToggleVideoMode: () -> Unit = {},
    onDownloadTrack: (Track) -> Unit = {},
    onArtistClick: ((String) -> Unit)? = null
) {
    val track = playerState.currentTrack ?: return
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // 100% Solid Dark Theme with isolated swipe-down handlers
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffsetY.roundToInt().coerceAtLeast(0)) }
            .background(DeepBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar with pull-down gesture
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffsetY > 140f) {
                                    onDismiss()
                                }
                                dragOffsetY = 0f
                            },
                            onDragCancel = { dragOffsetY = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 0 || dragOffsetY > 0f) {
                                    dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                }
                            }
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Свернуть",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "СЕЙЧАС ИГРАЕТ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VioletLight,
                        letterSpacing = 1.2.sp
                    )
                    val sourceTitle = playerState.sourceName.ifBlank { "Любимое" }
                    Text(
                        text = sourceTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { showSleepTimerDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = "Таймер сна",
                        tint = if (playerState.sleepTimerMinutes != null) VioletPrimary else TextMuted
                    )
                }
            }

            // Cover Art or Video Visualizer (Crisp & Solid, No Blur)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (playerState.isVideoMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, VioletDark, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircle,
                                contentDescription = null,
                                tint = VioletPrimary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Музыкальный визуализатор активен",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Воспроизведение без сжатия",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(track.getDisplayArtwork(context))
                            .crossfade(true)
                            .build(),
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(18.dp))
                    )
                }
            }

            // Track Meta & Favorite Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artist,
                        color = VioletLight,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onArtistClick?.invoke(track.artist) }
                    )
                }

                IconButton(onClick = { onFavoriteToggle(track) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "В любимые",
                        tint = if (isFavorite) AccentPink else TextMuted,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Isolated Timeline Scrub Slider (immune to dropped frames & snap-back jumping)
            PlayerTimelineScrubber(
                progressFlow = progressFlow,
                fallbackProgress = playbackProgress,
                trackDurationMs = track.durationMs,
                onSeek = onSeek
            )

            // Main Playback Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = onShuffleToggle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Перемешать",
                        tint = if (playerState.isShuffle) VioletPrimary else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Предыдущий",
                        tint = TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Play / Pause or Buffering (Solid Purple Button)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(VioletPrimary)
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    if (playerState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = DeepBlack,
                            strokeWidth = 3.dp
                        )
                    } else {
                        AnimatedContent(
                            targetState = playerState.isPlaying,
                            transitionSpec = {
                                (scaleIn(animationSpec = tween(180)) + fadeIn(animationSpec = tween(180)))
                                    .togetherWith(scaleOut(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180)))
                            },
                            label = "play_pause_anim"
                        ) { isPlaying ->
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Воспроизведение",
                                tint = DeepBlack,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }

                // Next
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Следующий",
                        tint = TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Repeat
                IconButton(onClick = onRepeatToggle) {
                    Icon(
                        imageVector = when (playerState.repeatMode) {
                            RepeatMode.ONE -> Icons.Rounded.RepeatOne
                            RepeatMode.ALL -> Icons.Rounded.Repeat
                            RepeatMode.OFF -> Icons.Rounded.Repeat
                        },
                        contentDescription = "Повтор",
                        tint = if (playerState.repeatMode != RepeatMode.OFF) VioletPrimary else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Audio Device Indicator (Bluetooth Headphones, Speakers, etc.)
            val audioDevice = rememberConnectedAudioDevice()
            if (audioDevice != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when (audioDevice.type) {
                                    ConnectedAudioDevice.DeviceType.SPEAKER -> Icons.Rounded.Speaker
                                    ConnectedAudioDevice.DeviceType.HEADPHONES -> Icons.Rounded.Headphones
                                    ConnectedAudioDevice.DeviceType.PHONE_SPEAKER -> Icons.Rounded.Smartphone
                                    ConnectedAudioDevice.DeviceType.OTHER -> Icons.Rounded.BluetoothAudio
                                },
                                contentDescription = null,
                                tint = if (audioDevice.isBluetooth) VioletLight else TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = audioDevice.displayText,
                                color = if (audioDevice.isBluetooth) TextPrimary else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Bottom Extra Actions: Download & Queue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Download Button for Offline Playback
                TextButton(onClick = { onDownloadTrack(track) }) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Скачать",
                        tint = VioletPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Скачать", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // Queue Button with SVG QueueMusic icon
                TextButton(onClick = { showQueueSheet = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Очередь",
                        tint = VioletLight,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Очередь", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Lyrics Bottom Sheet (Solid DarkSurface)
    if (showLyricsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLyricsSheet = false },
            containerColor = DarkSurfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = VioletPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Текст трека",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${track.title} • ${track.artist}",
                    fontSize = 13.sp,
                    color = VioletLight
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = track.lyrics,
                    fontSize = 16.sp,
                    lineHeight = 28.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Queue Bottom Sheet (Solid DarkSurface)
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = DarkSurfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Очередь воспроизведения",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                playerState.queue.forEachIndexed { index, item ->
                    val isCurrent = index == playerState.queueIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCurrent) DarkSurface else Color.Transparent)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = if (isCurrent) VioletPrimary else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(28.dp)
                        )
                        AsyncImage(
                            model = item.artworkUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurface)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = if (isCurrent) VioletPrimary else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = item.artist,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            containerColor = DarkSurfaceVariant,
            title = {
                Text("Таймер сна", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        Button(
                            onClick = {
                                onSetSleepTimer(mins)
                                showSleepTimerDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$mins минут", color = TextPrimary)
                        }
                    }
                    if (playerState.sleepTimerMinutes != null) {
                        TextButton(
                            onClick = {
                                onCancelSleepTimer()
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Отключить таймер", color = AccentPink)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Закрыть", color = TextSecondary)
                }
            }
        )
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

@Composable
fun PlayerTimelineScrubber(
    progressFlow: StateFlow<PlaybackProgress>?,
    fallbackProgress: PlaybackProgress,
    trackDurationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (progressFlow != null) {
        val p by progressFlow.collectAsState()
        p
    } else {
        fallbackProgress
    }

    val totalDuration = if (progress.durationMs > 0) progress.durationMs else trackDurationMs.coerceAtLeast(1000L)

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderPosition by remember { mutableFloatStateOf(0f) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }

    val now = System.currentTimeMillis()
    val isSeekSettling = (now - lastSeekTime) < 450L

    val effectivePositionMs = when {
        isDraggingSlider -> dragSliderPosition.toLong()
        isSeekSettling -> dragSliderPosition.toLong()
        else -> progress.positionMs
    }.coerceIn(0L, totalDuration)

    val currentSec = effectivePositionMs / 1000
    val totalSec = totalDuration / 1000

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = effectivePositionMs.toFloat().coerceIn(0f, totalDuration.toFloat().coerceAtLeast(1f)),
            onValueChange = {
                isDraggingSlider = true
                dragSliderPosition = it
            },
            onValueChangeFinished = {
                isDraggingSlider = false
                lastSeekTime = System.currentTimeMillis()
                onSeek(dragSliderPosition.toLong())
            },
            valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = VioletPrimary,
                activeTrackColor = VioletPrimary,
                inactiveTrackColor = DarkSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentSec),
                color = TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = formatTime(totalSec),
                color = TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
