package com.cloudify.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cloudify.music.core.theme.*
import com.cloudify.music.domain.model.Track

@Composable
fun TrackListItem(
    track: Track,
    isFavorite: Boolean = track.isFavorite,
    isDownloaded: Boolean = track.isDownloaded,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit = {},
    onMoreClick: () -> Unit = {}
) {
    val activeColor = VioletLight
    val itemBgColor by animateColorAsState(
        targetValue = if (isCurrentTrack) VioletPrimary.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(220),
        label = "track_bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(itemBgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork with overlay playing indicator
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.artworkUrl)
                    .crossfade(true)
                    .size(160)
                    .build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkSurface)
            )

            if (isCurrentTrack) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = activeColor,
                            strokeWidth = 2.dp
                        )
                    } else if (isPlaying) {
                        PlayingEqualizerBars(isPlaying = true, color = activeColor)
                    } else {
                        PlayingEqualizerBars(isPlaying = false, color = activeColor)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                color = if (isCurrentTrack) activeColor else TextPrimary,
                fontSize = 15.sp,
                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowCircleDown,
                        contentDescription = "Скачано на устройство",
                        tint = VioletLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = track.artist,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Like Button (Heart: filled pink/accent when favorite, outlined when not)
        IconButton(onClick = onFavoriteToggle) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "В любимые",
                tint = if (isFavorite) AccentPink else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        // 3-dots Menu Button
        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Опции",
                tint = TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun PlayingEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = VioletPrimary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq_bars")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(360, easing = LinearEasing), RepeatMode.Reverse),
        label = "h3"
    )

    Row(
        modifier = modifier
            .height(18.dp)
            .width(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight((if (isPlaying) h1 else 0.4f).coerceIn(0.05f, 1f))
                .background(color, RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight((if (isPlaying) h2 else 0.7f).coerceIn(0.05f, 1f))
                .background(color, RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight((if (isPlaying) h3 else 0.5f).coerceIn(0.05f, 1f))
                .background(color, RoundedCornerShape(2.dp))
        )
    }
}
