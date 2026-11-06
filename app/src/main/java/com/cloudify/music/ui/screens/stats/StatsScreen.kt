package com.cloudify.music.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudify.music.core.theme.AccentCyan
import com.cloudify.music.core.theme.AccentPink
import com.cloudify.music.core.theme.DarkSurface
import com.cloudify.music.core.theme.DarkSurfaceVariant
import com.cloudify.music.core.theme.DeepBlack
import com.cloudify.music.core.theme.TextMuted
import com.cloudify.music.core.theme.TextPrimary
import com.cloudify.music.core.theme.TextSecondary
import com.cloudify.music.core.theme.VioletDark
import com.cloudify.music.core.theme.VioletLight
import com.cloudify.music.core.theme.VioletPrimary

@Composable
fun StatsScreen(
    viewModel: StatsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val stats = state.stats

    Scaffold(
        containerColor = DeepBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp, start = 16.dp, end = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column {
                Text(
                    text = "Итоги месяца",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = stats.monthName,
                    fontSize = 14.sp,
                    color = VioletLight,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Hero Card: Hours Listened
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(VioletDark, VioletPrimary)
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "ВРЕМЯ С МУЗЫКОЙ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDDD6FE),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${stats.hoursListened} ч",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "музыки прослушано в этом месяце",
                            fontSize = 12.sp,
                            color = Color(0xFFDDD6FE)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // Top Artist Card
            StatDetailCard(
                title = "ЛЮБИМЫЙ ИСПОЛНИТЕЛЬ",
                value = stats.topArtist,
                subtitle = "Твой артист №1 в этом месяце",
                icon = Icons.Rounded.Person,
                iconTint = AccentCyan,
                imageUrl = stats.topArtistArtwork
            )

            // Top Track Card
            StatDetailCard(
                title = "ЛЮБИМАЯ ПЕСНЯ",
                value = stats.topTrack,
                subtitle = "Слушали ${stats.topTrackPlays} раз на повторе",
                icon = Icons.Rounded.MusicNote,
                iconTint = VioletLight,
                imageUrl = stats.topTrackArtwork.ifBlank { null }
            )
        }
    }
}

@Composable
fun StatDetailCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    imageUrl: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = value,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}
