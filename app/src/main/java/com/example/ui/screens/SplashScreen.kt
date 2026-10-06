package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premium Opening / Splash Animation Screen.
 * Shows the official BDX Auto Sheet logo with smooth zoom, fade-in,
 * followed by "BDX Auto Sheet" and "Powered by BDX".
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logoScale = remember { Animatable(0.6f) }
    val logoAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val progressAlpha = remember { Animatable(0f) }

    // Subtle pulsing ambient glow
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    LaunchedEffect(Unit) {
        // Step 1: Logo scales up and fades in
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        delay(350)
        // Step 2: Main title "BDX Auto Sheet" smoothly fades in
        launch {
            titleAlpha.animateTo(1f, animationSpec = tween(450, easing = FastOutSlowInEasing))
        }

        delay(250)
        // Step 3: Subtitle "Powered by BDX" fades in
        launch {
            subtitleAlpha.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        }
        launch {
            progressAlpha.animateTo(1f, animationSpec = tween(300))
        }

        // Wait to let user enjoy the sleek animation (~1.8 seconds total)
        delay(950)

        // Complete splash and transition smoothly
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Deep slate navy
                        Color(0xFF1E293B),
                        Color(0xFF0A0F1D)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background glow behind logo
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(glowScale)
                .alpha(logoAlpha.value * 0.25f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF38BDF8),
                            Color(0xFF1D4ED8),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Animated App Logo
            Surface(
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 10.dp,
                color = Color.Transparent,
                modifier = Modifier
                    .size(108.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            ) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = "BDX Auto Sheet Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Title: "BDX Auto Sheet"
            Text(
                text = "BDX Auto Sheet",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color.White,
                modifier = Modifier.alpha(titleAlpha.value)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle: "Powered by BDX"
            Text(
                text = "Powered by BDX",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
                color = Color(0xFF38BDF8), // Vibrant cyan
                modifier = Modifier.alpha(subtitleAlpha.value)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Subtle loading indicator at bottom
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(3.dp)
                    .alpha(progressAlpha.value)
                    .clip(RoundedCornerShape(2.dp))
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF38BDF8),
                    trackColor = Color(0xFF1E293B)
                )
            }
        }
    }
}
