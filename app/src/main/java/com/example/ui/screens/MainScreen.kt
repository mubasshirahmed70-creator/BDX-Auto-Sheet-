package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SheetState
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
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
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        AppHeader(isServiceRunning = isServiceRunning)

        Spacer(modifier = Modifier.height(18.dp))

        // Live Status & Metrics Card
        StatusMetricsCard(
            sheetState = sheetState,
            isServiceRunning = isServiceRunning
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Primary Action Buttons
        ActionButtonsSection(
            isServiceRunning = isServiceRunning,
            onStartBubble = { viewModel.checkAndStartFloatingBubble(context) },
            onStopService = { viewModel.stopFloatingBubble(context) },
            onOpenSheet = onNavigateToSheet,
            onOpenSetup = onNavigateToSetup
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Round Logic Info Card
        RoundLogicInfoCard(sheetState = sheetState)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AppHeader(isServiceRunning: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = "BDX Auto Sheet",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.service_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "BDX Auto Sheet",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatusMetricsCard(
    sheetState: SheetState,
    isServiceRunning: Boolean
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isServiceRunning) AccentGreen.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isServiceRunning) AccentGreen else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServiceRunning) "ACTIVE BUBBLE" else "IDLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isServiceRunning) AccentGreen else Color.Gray
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
                    label = "Configured Cols",
                    value = "${sheetState.config.columns.size} (${sheetState.config.columns.joinToString(",")})",
                    modifier = Modifier.weight(1.3f)
                )
                MetricItem(
                    label = "Total Cells",
                    value = "${sheetState.totalFilledCells}",
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ActionButtonsSection(
    isServiceRunning: Boolean,
    onStartBubble: () -> Unit,
    onStopService: () -> Unit,
    onOpenSheet: () -> Unit,
    onOpenSetup: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. OPEN SHEET BUTTON
        LargeActionButton(
            text = stringResource(R.string.open_sheet),
            icon = Icons.Default.TableChart,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            testTag = "open_sheet_button",
            onClick = onOpenSheet
        )

        // 2. SETUP / CONFIGURATION BUTTON
        LargeActionButton(
            text = stringResource(R.string.setup_configuration),
            icon = Icons.Default.Settings,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            testTag = "setup_config_button",
            onClick = onOpenSetup
        )

        // 3. START FLOATING BUBBLE BUTTON
        LargeActionButton(
            text = stringResource(R.string.start_floating_bubble),
            icon = Icons.Default.BubbleChart,
            containerColor = Color(0xFF0284C7), // Vibrant Cyan/Blue
            contentColor = Color.White,
            testTag = "start_floating_bubble_button",
            onClick = onStartBubble
        )

        // 4. STOP SERVICE BUTTON
        LargeActionButton(
            text = stringResource(R.string.stop_service),
            icon = Icons.Default.Stop,
            containerColor = if (isServiceRunning) AccentRed else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isServiceRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            testTag = "stop_service_button",
            onClick = onStopService
        )
    }
}

@Composable
private fun LargeActionButton(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
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
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
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
private fun RoundLogicInfoCard(sheetState: SheetState) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "How Round Logic Works",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            val firstCol = sheetState.config.columns.firstOrNull() ?: "A"
            Text(
                text = "• A round consists of columns: ${sheetState.config.columns.joinToString(" → ")}\n" +
                        "• Tapping '$firstCol' (the first column) advances to the next round/row position.\n" +
                        "• Skipped columns in any round remain empty.\n" +
                        "• Floating bubble overlays other apps so you can copy and tap on the fly!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}
