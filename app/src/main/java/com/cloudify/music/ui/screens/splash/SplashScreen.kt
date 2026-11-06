package com.cloudify.music.ui.screens.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudify.music.R
import com.cloudify.music.core.theme.DeepBlack
import com.cloudify.music.core.theme.TextMuted
import com.cloudify.music.core.theme.TextPrimary
import com.cloudify.music.core.theme.VioletLight
import com.cloudify.music.core.theme.VioletPrimary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    themeColor: String,
    onFinish: () -> Unit
) {
    val isYellow = themeColor.equals("yellow", ignoreCase = true)
    val accentColor = if (isYellow) Color(0xFFFACC15) else Color(0xFF8B5CF6)
    val glowColor = if (isYellow) Color(0x40FACC15) else Color(0x408B5CF6)

    val scale = remember { Animatable(0.75f) }
    val alpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Animate icon scale and fade in
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f)
        )
    }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
        // Keep splash visible briefly for smooth app launch perception
        delay(850)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background aura
        Box(
            modifier = Modifier
                .size(260.dp)
                .scale(scale.value * 1.15f)
                .alpha(alpha.value * 0.45f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Icon in according with Theme
            val iconRes = if (isYellow) {
                R.drawable.ic_cloudify_logo_yellow
            } else {
                R.drawable.ic_cloudify_logo_violet
            }

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .clip(RoundedCornerShape(32.dp))
                    .background(if (isYellow) Color(0xFF1E1A0E) else Color(0xFF13101E)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = "Cloudify Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Brand Name
            Text(
                text = "Cloudify",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Музыка без границ",
                color = if (isYellow) Color(0xFFFDE047) else VioletLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}
