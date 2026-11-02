package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SheetConfig
import com.example.data.repository.HotmailStockState
import com.example.data.repository.VoltxSmsRepository
import com.example.data.repository.VoltxSmsState
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.HackerCrimson
import com.example.ui.theme.HackerNeonRed
import com.example.ui.theme.PrimaryLight
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.syncSetupWithCurrentConfig()
    }

    val columnsInput by viewModel.setupColumnsInput.collectAsState()
    val buttonSize by viewModel.setupButtonSize.collectAsState()
    val isLocalSaveMode by viewModel.setupIsLocalSaveMode.collectAsState()
    val hotmailStockState by viewModel.hotmailStockState.collectAsState()

    val voltxRepo = remember { VoltxSmsRepository.getInstance(context) }
    val voltxState by voltxRepo.state.collectAsState()

    var voltxApiKeyInput by remember(voltxState.apiKey) { mutableStateOf(voltxState.apiKey) }
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

    val parsedPreviewColumns = remember(columnsInput) {
        SheetConfig.parseColumns(columnsInput)
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Setup & Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("setup_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121216)
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF09090B))
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. VOLTX SMS API Key (mauthapi) Configuration Card
            VoltxSmsSetupCard(
                voltxState = voltxState,
                apiKeyInput = voltxApiKeyInput,
                onApiKeyChange = { voltxApiKeyInput = it },
                onSaveApiKey = {
                    voltxRepo.updateApiKey(voltxApiKeyInput)
                    Toast.makeText(context, "✅ VOLTX API Key Saved!", Toast.LENGTH_SHORT).show()
                },
                onCycleCopyFormat = {
                    val next = voltxRepo.cycleCopyFormat()
                    Toast.makeText(context, "Copy Format: ${next.label}", Toast.LENGTH_SHORT).show()
                },
                onClearAllocatedNumbers = {
                    voltxRepo.clearAllocatedNumbers()
                    Toast.makeText(context, "Cleared allocated VOLTX numbers", Toast.LENGTH_SHORT).show()
                }
            )

            // 2. Hotmail Stock & TXT File Upload Card
            HotmailStockSetupCard(
                stockState = hotmailStockState,
                onUploadTxtFile = {
                    openTxtFileLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                },
                onOpenPasteDialog = {
                    pasteTextInput = ""
                    showPasteTextDialog = true
                },
                onClearUsed = { viewModel.clearUsedHotmailStock() },
                onResetAll = { viewModel.resetHotmailStock() }
            )

            // 3. Column Buttons Input Card
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
                    Text(
                        text = stringResource(R.string.column_buttons_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Define column names in order (e.g. A,B,C or Email,Password,Name,Phone).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA1A1AA)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = columnsInput,
                        onValueChange = { viewModel.onSetupColumnsChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_columns_input"),
                        placeholder = { Text("A,B,C") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Configured Columns Preview (${parsedPreviewColumns.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFA1A1AA)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        parsedPreviewColumns.forEachIndexed { idx, col ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF18181D),
                                border = BorderStroke(1.dp, Color(0xFF27272A)),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}. ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HackerNeonRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = col,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Button Size Slider Card
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
                            text = stringResource(R.string.button_size_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${buttonSize.toInt()} dp",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = HackerNeonRed
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = buttonSize,
                        onValueChange = { viewModel.onSetupButtonSizeChanged(it) },
                        valueRange = SheetConfig.MIN_BUTTON_SIZE.toFloat()..SheetConfig.MAX_BUTTON_SIZE.toFloat(),
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = HackerNeonRed,
                            activeTrackColor = HackerNeonRed,
                            inactiveTrackColor = Color(0xFF27272A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_button_size_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Live Visual Preview of Floating Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val sizeDp = buttonSize.dp
                        Box(
                            modifier = Modifier
                                .size(sizeDp)
                                .clip(CircleShape)
                                .background(PrimaryLight)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = parsedPreviewColumns.firstOrNull() ?: "A",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = (buttonSize * 0.32f).sp
                            )
                        }
                    }
                }
            }

            // 5. Save Mode Card
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
                    Text(
                        text = stringResource(R.string.save_mode),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA1A1AA)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.local_app_space_mode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Persist sheet data locally in internal private storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA1A1AA)
                            )
                        }

                        Switch(
                            checked = isLocalSaveMode,
                            onCheckedChange = { viewModel.onSetupLocalSaveModeToggled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = HackerNeonRed
                            ),
                            modifier = Modifier.testTag("setup_save_mode_switch")
                        )
                    }
                }
            }

            // 6. Save & Configure Button
            Button(
                onClick = {
                    if (voltxApiKeyInput.trim() != voltxState.apiKey) {
                        voltxRepo.updateApiKey(voltxApiKeyInput)
                    }
                    viewModel.saveConfiguration()
                    onNavigateBack()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HackerCrimson,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("setup_save_configure_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.save_and_configure),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Paste Hotmail Text Dialog
    if (showPasteTextDialog) {
        AlertDialog(
            onDismissRequest = { showPasteTextDialog = false },
            containerColor = Color(0xFF141418),
            title = {
                Text(
                    text = "Paste Hotmail Accounts",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter or paste hotmail lines (one per line, e.g. email:password or email|pass):",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA1A1AA)
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
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HackerCrimson,
                        contentColor = Color.White
                    )
                ) {
                    Text("Add to Stock", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteTextDialog = false }) {
                    Text("Cancel", color = Color(0xFFA1A1AA))
                }
            }
        )
    }
}

@Composable
private fun VoltxSmsSetupCard(
    voltxState: VoltxSmsState,
    apiKeyInput: String,
    onApiKeyChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onCycleCopyFormat: () -> Unit,
    onClearAllocatedNumbers: () -> Unit
) {
    val hasKey = voltxState.apiKey.isNotBlank()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121216)
        ),
        border = BorderStroke(1.dp, Color(0xFF27272A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voltx_sms_setup_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                        shape = CircleShape,
                        color = Color(0xFF1F1F26),
                        border = BorderStroke(1.dp, HackerNeonRed),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = HackerNeonRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "VOLTX SMS API Setup",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Set your mauthapi key for floating SMS & OTP",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = Color(0xFFA1A1AA)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (hasKey) AccentGreen.copy(alpha = 0.14f) else Color(0xFF271518),
                    border = BorderStroke(1.dp, if (hasKey) AccentGreen else Color(0xFF522228))
                ) {
                    Text(
                        text = if (hasKey) "KEY SAVED" else "NO KEY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasKey) AccentGreen else Color(0xFFF87171),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = onApiKeyChange,
                label = { Text("VOLTX API Key (mauthapi)") },
                placeholder = { Text("Paste mauthapi token here...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voltx_api_key_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSaveApiKey,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HackerCrimson,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp)
                        .testTag("save_voltx_key_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Save API Key",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }

                OutlinedButton(
                    onClick = onCycleCopyFormat,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        text = "Copy: ${voltxState.copyFormat.label}",
                        color = Color(0xFFE4E4E7),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }

                if (voltxState.allocatedNumbers.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearAllocatedNumbers,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = "Clear (${voltxState.allocatedNumbers.size})",
                            color = Color(0xFFA1A1AA),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HotmailStockSetupCard(
    stockState: HotmailStockState,
    onUploadTxtFile: () -> Unit,
    onOpenPasteDialog: () -> Unit,
    onClearUsed: () -> Unit,
    onResetAll: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121216)
        ),
        border = BorderStroke(1.dp, Color(0xFF27272A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hotmail_stock_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                        shape = CircleShape,
                        color = Color(0xFF1F1F26),
                        border = BorderStroke(1.dp, HackerNeonRed),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = HackerNeonRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Hotmail Stock & TXT Upload",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Unused: ${stockState.availableCount} • Used: ${stockState.usedCount} • Total: ${stockState.totalCount}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = Color(0xFFA1A1AA)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentGreen.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, AccentGreen)
                ) {
                    Text(
                        text = "${stockState.availableCount} Ready",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val activeItem = stockState.currentLoadedItem ?: stockState.availableItems.firstOrNull()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF18181D),
                border = BorderStroke(1.dp, Color(0xFF27272A)),
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
                        color = Color(0xFFA1A1AA)
                    )
                    Text(
                        text = activeItem?.email ?: if (stockState.totalCount == 0) "Stock empty — Upload .txt file below" else "All used/বাতিল",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (activeItem != null) Color.White else Color.Gray,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onUploadTxtFile,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1F1F26),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, HackerNeonRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("upload_txt_file_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = HackerNeonRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "UPLOAD .TXT FILE (টিএক্সটি ফাইল আপলোড)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenPasteDialog,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = Color(0xFFE4E4E7),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Paste", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE4E4E7))
                }

                OutlinedButton(
                    onClick = onClearUsed,
                    shape = RoundedCornerShape(10.dp),
                    enabled = stockState.usedCount > 0,
                    border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Clear Used", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onResetAll,
                    shape = RoundedCornerShape(10.dp),
                    enabled = stockState.usedCount > 0,
                    border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Reset All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
