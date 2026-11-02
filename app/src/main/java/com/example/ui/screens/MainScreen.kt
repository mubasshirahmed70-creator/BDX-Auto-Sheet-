package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SheetState
import com.example.data.repository.HotmailStockState
import com.example.data.repository.VoltxSmsRepository
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.HackerCrimson
import com.example.ui.theme.HackerNeonRed
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

    val voltxRepo = remember { VoltxSmsRepository.getInstance(context) }
    val voltxState by voltxRepo.state.collectAsState()

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF09090B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Header (Sleek Matte Black with Red Accent)
            AppHeader()

            Spacer(modifier = Modifier.height(20.dp))

            // Live Status & Metrics Card
            StatusMetricsCard(
                sheetState = sheetState,
                hotmailStockState = hotmailStockState,
                hasVoltxKey = voltxState.apiKey.isNotBlank(),
                isServiceRunning = isServiceRunning
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Action Buttons (3 Clean Buttons: Single Toggle Start/Stop Bubble, Open Sheet, Setup/Configuration)
            ActionButtonsSection(
                isServiceRunning = isServiceRunning,
                onToggleBubble = {
                    if (isServiceRunning) {
                        viewModel.stopFloatingBubble(context)
                    } else {
                        viewModel.checkAndStartFloatingBubble(context)
                    }
                },
                onOpenSheet = onNavigateToSheet,
                onOpenSetup = onNavigateToSetup
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Concise Guide Card
            HowToUseAppCard(sheetState = sheetState)

            // Extra space so floating button never covers bottom content
            Spacer(modifier = Modifier.height(76.dp))
        }

        // Floating "Join Us" Telegram Channel Button
        ExtendedFloatingActionButton(
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/bdxtechnical")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open Telegram: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            containerColor = Color(0xFF18181D),
            contentColor = Color.White,
            shape = RoundedCornerShape(24.dp),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 18.dp)
                .border(1.dp, HackerNeonRed, RoundedCornerShape(24.dp))
                .testTag("join_us_telegram_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Join Us Telegram Channel",
                tint = HackerNeonRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Join Us",
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                letterSpacing = 0.3.sp
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 8.dp,
            border = BorderStroke(1.5.dp, HackerNeonRed),
            color = Color(0xFF121216),
            modifier = Modifier.size(76.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "BDX Auto Sheet Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "BDX AUTO SHEET",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "AUTOMATION & FLOATING TOOLS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color(0xFFA1A1AA)
        )
    }
}

@Composable
private fun StatusMetricsCard(
    sheetState: SheetState,
    hotmailStockState: HotmailStockState,
    hasVoltxKey: Boolean,
    isServiceRunning: Boolean
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121216)
        ),
        border = BorderStroke(1.dp, Color(0xFF27272A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM STATUS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = Color(0xFFA1A1AA)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isServiceRunning) AccentGreen.copy(alpha = 0.14f)
                            else Color(0xFF18181D)
                        )
                        .border(
                            1.dp,
                            if (isServiceRunning) AccentGreen else Color(0xFF27272A),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isServiceRunning) AccentGreen else Color(0xFF71717A))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServiceRunning) "ACTIVE" else "STANDBY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isServiceRunning) AccentGreen else Color(0xFFA1A1AA)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "Active Round",
                    value = "Row ${sheetState.currentRow}",
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "Mail Stock",
                    value = "${hotmailStockState.availableCount} Ready",
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "VOLTX API",
                    value = if (hasVoltxKey) "Ready" else "Not Set",
                    valueColor = if (hasVoltxKey) AccentGreen else Color(0xFFA1A1AA),
                    modifier = Modifier.weight(0.9f)
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFA1A1AA)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun ActionButtonsSection(
    isServiceRunning: Boolean,
    onToggleBubble: () -> Unit,
    onOpenSheet: () -> Unit,
    onOpenSetup: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. SINGLE TOGGLE BUTTON: START FLOATING BUBBLE <-> STOP FLOATING BUBBLE
        LargeActionButton(
            text = if (isServiceRunning) "STOP FLOATING BUBBLE" else stringResource(R.string.start_floating_bubble),
            icon = if (isServiceRunning) Icons.Default.Stop else Icons.Default.BubbleChart,
            containerColor = if (isServiceRunning) Color(0xFF1F0E12) else Color(0xFF141419),
            contentColor = Color.White,
            iconTint = HackerNeonRed,
            borderColor = HackerNeonRed,
            testTag = "start_floating_bubble_button",
            onClick = onToggleBubble
        )

        // 2. OPEN SHEET BUTTON
        LargeActionButton(
            text = stringResource(R.string.open_sheet),
            icon = Icons.Default.TableChart,
            containerColor = Color(0xFF141419),
            contentColor = Color.White,
            iconTint = HackerNeonRed,
            borderColor = Color(0xFFDC2626),
            testTag = "open_sheet_button",
            onClick = onOpenSheet
        )

        // 3. SETUP / CONFIGURATION BUTTON (Includes VOLTX API Key & Hotmail TXT Upload)
        LargeActionButton(
            text = stringResource(R.string.setup_configuration),
            icon = Icons.Default.Settings,
            containerColor = Color(0xFF141419),
            contentColor = Color.White,
            iconTint = HackerNeonRed,
            borderColor = Color(0xFFDC2626),
            testTag = "setup_config_button",
            onClick = onOpenSetup
        )
    }
}

@Composable
private fun LargeActionButton(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    iconTint: Color,
    borderColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(1.3.dp, borderColor),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun HowToUseAppCard(sheetState: SheetState) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121216)
        ),
        border = BorderStroke(1.dp, Color(0xFF27272A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E1E24),
                    border = BorderStroke(1.dp, HackerNeonRed),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Quick Guide",
                            tint = HackerNeonRed,
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
                text = "• Start / Stop Bubble: Tap the top button to start or stop the floating bubble anytime.\n\n" +
                        "• Setup / Configuration: Set your VOLTX SMS API Key (mauthapi), upload Hotmail .txt files, and customize sheet columns (${sheetState.config.columns.joinToString(", ")}) in Setup.\n\n" +
                        "• Compact VOLTX SMS Bubble: Tap ⚡ -> SMS icon to see live ranges (Facebook ranges always on top). Select a range to allocate 2, 3, or 4 numbers, tap any number to copy, and tap the incoming OTP badge to copy OTP.\n\n" +
                        "• Sheet & Export: Tapping '$firstCol' advances to the next row automatically. Download as Excel (.xlsx) or CSV anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA1A1AA),
                lineHeight = 18.sp
            )
        }
    }
}
