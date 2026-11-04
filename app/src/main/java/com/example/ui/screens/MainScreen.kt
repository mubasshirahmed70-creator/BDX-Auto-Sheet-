package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SheetState
import com.example.data.repository.HotmailStockState
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AppThemeManager
import com.example.ui.theme.AppThemePreset
import com.example.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSheet: () -> Unit,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState by viewModel.sheetState.collectAsState()
    val hotmailStockState by viewModel.hotmailStockState.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val activeTheme by AppThemeManager.currentTheme.collectAsState()

    val scrollState = rememberScrollState()

    val onToggleBubble = {
        if (isServiceRunning) {
            viewModel.stopFloatingBubble(context)
        } else {
            viewModel.checkAndStartFloatingBubble(context)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        activeTheme.bgCompose,
                        activeTheme.headerStartCompose.copy(alpha = 0.65f),
                        activeTheme.bgCompose
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            // 1. Compact Top HUD Header Bar
            BentoTopHudHeader(
                theme = activeTheme,
                isServiceRunning = isServiceRunning,
                onJoinTelegram = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/bdxtechnical")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open Telegram: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Center Power Hero Hub (Asymmetric Bento Hero Card with Glowing Power Ring)
            BentoPowerHeroCard(
                theme = activeTheme,
                sheetState = sheetState,
                isServiceRunning = isServiceRunning,
                onToggleBubble = onToggleBubble
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Hotmail Access Status Card (Non-clickable display of Available & Used Hotmails)
            HotmailAccessStatusBentoCard(
                theme = activeTheme,
                hotmailStockState = hotmailStockState
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Side-by-Side Bento Action Modules (Live Sheet & Setup/Config)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BentoModuleTile(
                    theme = activeTheme,
                    icon = Icons.Default.TableChart,
                    badgeText = "ROW ${sheetState.currentRow}",
                    badgeAccent = activeTheme.accentBrightCompose,
                    title = "Live Sheet",
                    subtitle = "${sheetState.cells.size} cells • ${sheetState.config.columns.joinToString(",")}",
                    footerActionText = "Open Sheet →",
                    testTag = "open_sheet_button",
                    onClick = onNavigateToSheet,
                    modifier = Modifier.weight(1f)
                )

                BentoModuleTile(
                    theme = activeTheme,
                    icon = Icons.Default.Settings,
                    badgeText = "${sheetState.config.buttonSizeDp}DP DOCK",
                    badgeAccent = activeTheme.accentLightTextCompose,
                    title = "Setup & Config",
                    subtitle = "BDX SMS • Hotmail • Cols",
                    footerActionText = "Configure →",
                    testTag = "setup_config_button",
                    onClick = onNavigateToSetup,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Concise Quick Guide Card
            HowToUseAppCard(
                theme = activeTheme,
                sheetState = sheetState
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BentoTopHudHeader(
    theme: AppThemePreset,
    isServiceRunning: Boolean,
    onJoinTelegram: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    1.2.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF7DD3FC),
                            Color(0xFF38BDF8),
                            Color(0xFF2563EB)
                        )
                    )
                ),
                shadowElevation = 8.dp,
                modifier = Modifier.size(48.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_bdx_brand_logo),
                    contentDescription = "BDX Auto Sheet Logo",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "BDX AUTO SHEET",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.0.sp,
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0xFF38BDF8).copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = "PRO",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.7.sp,
                            color = Color(0xFF7DD3FC),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }
                Text(
                    text = "PRO AUTOMATION SUITE",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = theme.accentLightTextCompose
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live Status Capsule
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isServiceRunning) AccentGreen.copy(alpha = 0.15f) else theme.cardCompose,
                border = BorderStroke(
                    1.dp,
                    if (isServiceRunning) AccentGreen else theme.cardBorderCompose
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isServiceRunning) AccentGreen else Color(0xFF71717A))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isServiceRunning) "LIVE" else "STANDBY",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isServiceRunning) AccentGreen else theme.textSecondaryCompose
                    )
                }
            }

            // Compact Telegram Pill Button
            Surface(
                shape = CircleShape,
                color = theme.elevatedCompose,
                border = BorderStroke(1.dp, theme.accentBrightCompose),
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable { onJoinTelegram() }
                    .testTag("join_us_telegram_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Join Us Telegram",
                        tint = theme.accentBrightCompose,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BentoPowerHeroCard(
    theme: AppThemePreset,
    sheetState: SheetState,
    isServiceRunning: Boolean,
    onToggleBubble: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PowerPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isServiceRunning) 1.07f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PowerRingScale"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.4.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    theme.accentBrightCompose,
                    theme.cardBorderCompose,
                    theme.accentCompose
                )
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            theme.headerStartCompose,
                            theme.cardCompose,
                            theme.elevatedCompose
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = theme.accentSoftBgCompose,
                        border = BorderStroke(0.8.dp, theme.accentBrightCompose.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = if (isServiceRunning) "⚡ GLASS DOCK OVERLAY ACTIVE" else "⚡ FLOATING COMMAND ENGINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            color = theme.accentLightTextCompose,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isServiceRunning) "Overlay Running" else "Launch Overlay",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isServiceRunning) {
                            "Glass Dock is active on screen. Tap power ring to stop."
                        } else {
                            "Tap the power ring to launch the floating Glass Dock."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textSecondaryCompose,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = theme.bgCompose.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, theme.cardBorderCompose)
                        ) {
                            Text(
                                text = "Round #${sheetState.currentRow}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.accentLightTextCompose,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = theme.bgCompose.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, theme.cardBorderCompose)
                        ) {
                            Text(
                                text = "${sheetState.config.columns.size} Columns",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Glowing Power Ring Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(88.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isServiceRunning) AccentGreen.copy(alpha = 0.35f)
                                    else theme.accentBrightCompose.copy(alpha = 0.32f),
                                    Color.Transparent
                                )
                            )
                        )
                        .clickable { onToggleBubble() }
                        .testTag("start_floating_bubble_button")
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isServiceRunning) Color(0xFF063E2E) else theme.elevatedCompose,
                        border = BorderStroke(
                            width = 2.5.dp,
                            color = if (isServiceRunning) AccentGreen else theme.accentBrightCompose
                        ),
                        shadowElevation = 10.dp,
                        modifier = Modifier.size(70.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PowerSettingsNew,
                                contentDescription = if (isServiceRunning) "Stop Floating Bubble" else "Start Floating Bubble",
                                tint = if (isServiceRunning) AccentGreen else theme.accentBrightCompose,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = if (isServiceRunning) "STOP" else "START",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Non-clickable Bento Status Card for Hotmail Access.
 * Displays Available and Used counts clearly without triggering navigation on tap.
 */
@Composable
private fun HotmailAccessStatusBentoCard(
    theme: AppThemePreset,
    hotmailStockState: HotmailStockState
) {
    val total = (hotmailStockState.availableCount + hotmailStockState.usedCount).coerceAtLeast(1)
    val ratio = (hotmailStockState.availableCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardCompose),
        border = BorderStroke(1.dp, theme.cardBorderCompose),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hotmail_access_status_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = theme.accentSoftBgCompose,
                        border = BorderStroke(1.dp, theme.accentBrightCompose.copy(alpha = 0.65f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Hotmail Access",
                                tint = theme.accentBrightCompose,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Hotmail Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Live Account Stock Overview",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = theme.textSecondaryCompose
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.elevatedCompose,
                    border = BorderStroke(
                        0.9.dp,
                        if (hotmailStockState.availableCount > 0) AccentGreen else theme.cardBorderCompose
                    )
                ) {
                    Text(
                        text = "${hotmailStockState.availableCount} AVAILABLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (hotmailStockState.availableCount > 0) AccentGreen else theme.textSecondaryCompose,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Side-by-side Available & Used Metric Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = theme.elevatedCompose,
                    border = BorderStroke(1.dp, theme.cardBorderCompose),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Available",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = theme.textSecondaryCompose
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${hotmailStockState.availableCount}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (hotmailStockState.availableCount > 0) AccentGreen else Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = theme.elevatedCompose,
                    border = BorderStroke(1.dp, theme.cardBorderCompose),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Used",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = theme.textSecondaryCompose
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${hotmailStockState.usedCount}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = theme.accentLightTextCompose
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { ratio },
                color = theme.accentBrightCompose,
                trackColor = theme.elevatedCompose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun BentoModuleTile(
    theme: AppThemePreset,
    icon: ImageVector,
    badgeText: String,
    badgeAccent: Color,
    title: String,
    subtitle: String,
    footerActionText: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardCompose),
        border = BorderStroke(1.dp, theme.cardBorderCompose),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = theme.accentSoftBgCompose,
                    border = BorderStroke(1.dp, theme.accentBrightCompose.copy(alpha = 0.6f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = theme.accentBrightCompose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = theme.elevatedCompose,
                    border = BorderStroke(0.8.dp, badgeAccent.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeAccent,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = theme.textSecondaryCompose,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = footerActionText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = theme.accentLightTextCompose
            )
        }
    }
}

@Composable
private fun HowToUseAppCard(
    theme: AppThemePreset,
    sheetState: SheetState
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = theme.cardCompose
        ),
        border = BorderStroke(1.dp, theme.cardBorderCompose),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = theme.accentSoftBgCompose,
                    border = BorderStroke(1.dp, theme.accentBrightCompose),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Quick Guide",
                            tint = theme.accentBrightCompose,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "Quick Guide",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val firstCol = sheetState.config.columns.firstOrNull() ?: "A"
            Text(
                text = "• Launch Overlay: Tap the glowing START power ring above to open the floating Glass Dock.\n\n" +
                        "• BDX SMS & Hotmail Access: Open BDX SMS (auto +Full copy & live OTP) or Hotmail Access directly from the floating Tools Hub.\n\n" +
                        "• Minimize & Maximize: All floating windows minimize into a floating mini-bubble and restore with 1 tap.\n\n" +
                        "• Sheet & Export: Tapping '$firstCol' advances to the next row automatically. Export as Excel (.xlsx) or CSV anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = theme.textSecondaryCompose,
                lineHeight = 18.sp
            )
        }
    }
}
