package com.cloudify.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudify.music.core.theme.DeepBlack
import com.cloudify.music.core.theme.TextPrimary
import com.cloudify.music.core.theme.VioletDark
import com.cloudify.music.core.theme.VioletLight
import com.cloudify.music.core.theme.VioletPrimary

@Composable
fun WaveCard(
    isWaveActive: Boolean = false,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isWaveActive) 1.05f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            VioletDark,
            VioletPrimary,
            Color(0xFF4C1D95),
            Color(0xFF2E1065)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .scale(if (isWaveActive) pulseScale else 1f)
            .clip(RoundedCornerShape(20.dp))
            .background(gradientBrush)
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = VioletLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "МОЯ ВОЛНА",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = VioletLight,
                        letterSpacing = 1.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isWaveActive) "Волна активна • Играет" else "Бесконечный поток музыки",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Подстраивается под твой вкус и лайки",
                    fontSize = 12.sp,
                    color = Color(0xFFDDD6FE)
                )
            }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(TextPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Запустить волну",
                    tint = DeepBlack,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
