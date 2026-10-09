package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val hotmailStockState by viewModel.hotmailStockState.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    var showPasteTextDialog by remember { mutableStateOf(false) }
    var pasteTextInput by remember { mutableStateOf("") }

    // Android Storage Access Framework picker for .txt files
    val openTxtFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importHotmailsFromUri(uri)
        }
    }

    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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

            Spacer(modifier = Modifier.height(20.dp))

            // Hotmail Stock Management & TXT File Upload Card
            HotmailStockCard(
                stockState = hotmailStockState,
                onUploadTxtFile = {
                    openTxtFileLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                },
                onOpenPasteDialog = {
                    pasteTextInput = ""
                    showPasteTextDialog = true
                },
                onClearUsed = { viewModel.clearUsedHotmailStock() },
                onResetAll = { viewModel.resetHotmailStock() },
                onClearAll = { viewModel.clearHotmailStock() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Action Buttons
            ActionButtonsSection(
                isServiceRunning = isServiceRunning,
                onStartBubble = { viewModel.checkAndStartFloatingBubble(context) },
                onStopService = { viewModel.stopFloatingBubble(context) },
                onOpenSheet = onNavigateToSheet,
                onOpenSetup = onNavigateToSetup
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Unified Guide Card
            HowToUseAppCard(sheetState = sheetState)

            // Extra space so floating button never covers bottom content
            Spacer(modifier = Modifier.height(76.dp))
        }

        // Paste Text Dialog
        if (showPasteTextDialog) {
            AlertDialog(
                onDismissRequest = { showPasteTextDialog = false },
                title = {
                    Text(
                        text = "Paste Hotmail Accounts",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter or paste hotmail lines (one per line, e.g. email:password or email|pass):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pasteTextInput,
                            onValueChange = { pasteTextInput = it },
                            placeholder = { Text("user1@hotmail.com:pass1\nuser2@hotmail.com:pass2") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            maxLines = 8
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (pasteTextInput.isNotBlank()) {
                                viewModel.importHotmailsFromText(pasteTextInput)
                            }
                            showPasteTextDialog = false
                        }
                    ) {
                        Text("Add to Stock")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasteTextDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
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
            containerColor = Color(0xFF0088CC), // Official Telegram Blue
            contentColor = Color.White,
            shape = RoundedCornerShape(24.dp),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 18.dp)
                .testTag("join_us_telegram_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Join Us Telegram Channel",
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
private fun AppHeader(isServiceRunning: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 4.dp,
            color = Color.Transparent,
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
            text = "BDX Auto Sheet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = stringResource(R.string.service_subtitle),
            style = MaterialTheme.typography.bodySmall,
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
private fun HowToUseAppCard(sheetState: SheetState) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Quick Guide",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "How to Use This App",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val firstCol = sheetState.config.columns.firstOrNull() ?: "A"
            Text(
                text = "• Floating Bubbles: Tap 'START FLOATING BUBBLE' to show bubbles on your screen. Tap any column bubble (${sheetState.config.columns.joinToString(", ")}) over any app to insert copied text.\n\n" +
                        "• 📦 In-App Hotmail Pool & Queue: Upload your .txt file with hotmail accounts (one per line). In the floating MailGen window:\n" +
                        "   - Hotmails are loaded one by one automatically with 'Next ⏭️' and 'Done/Skip ⏩' buttons!\n" +
                        "   - 📋 COPY FULL DATA button copies the entire line (email:password:recovery).\n" +
                        "   - 📧 COPY MAIL ONLY button copies only the clean email address.\n" +
                        "   - Automatic Discard: When OTP arrives, that mail is automatically retired/বাতিল so it is never reused!\n\n" +
                        "• ⚡ Special Tools Hub: Tap the ⚡/🛠️ button to expand quick tools:\n" +
                        "   - ✉️ MailGen Inbox: Read temporary mail, copy email & fetch OTPs.\n" +
                        "   - 🔵 Facebook Web: Floating mobile Facebook window! Log in freely with automatic typing, and 1-tap 'COPY UID' or 'COPY COOKIE' to grab account ID or full cookies straight to clipboard!\n\n" +
                        "• Round Progression: Data is recorded row by row. Tapping '$firstCol' or finishing a round advances to the next row automatically.\n\n" +
                        "• Sheet & Export: Tap 'OPEN SHEET' to review, edit cells, or export everything to Excel (.xlsx) anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun HotmailStockCard(
    stockState: HotmailStockState,
    onUploadTxtFile: () -> Unit,
    onOpenPasteDialog: () -> Unit,
    onClearUsed: () -> Unit,
    onResetAll: () -> Unit,
    onClearAll: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hotmail_stock_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Icon + Title + Stock Counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF6366F1),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "HOTMAIL STOCK & POOL",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Available pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${stockState.availableCount} Available",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metrics Row: Available, Used, Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "Unused / বাকি",
                    value = "${stockState.availableCount}",
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "Used / বাতিল",
                    value = "${stockState.usedCount}",
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "Total / মোট",
                    value = "${stockState.totalCount}",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Next Hotmail In Queue Preview
            val activeItem = stockState.currentLoadedItem ?: stockState.availableItems.firstOrNull()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Next In Queue: ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = activeItem?.email ?: if (stockState.totalCount == 0) "Stock empty — Upload .txt file below" else "All used/বাতিল",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (activeItem != null) MaterialTheme.colorScheme.primary else Color.Gray,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Upload TXT File Button (Requirement 9 & 10)
            Button(
                onClick = onUploadTxtFile,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4F46E5), // Indigo
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("upload_txt_file_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "UPLOAD .TXT FILE (টিএক্সটি ফাইল আপলোড)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Action Row: Paste Lines, Clear Used, Reset All
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenPasteDialog,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Paste", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onClearUsed,
                    shape = RoundedCornerShape(10.dp),
                    enabled = stockState.usedCount > 0,
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Clear Used", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onResetAll,
                    shape = RoundedCornerShape(10.dp),
                    enabled = stockState.usedCount > 0,
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Reset All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
