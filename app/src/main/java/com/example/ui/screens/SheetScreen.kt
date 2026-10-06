package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.PrimaryLight
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate700
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onDownloadXlsx: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val sheetState by viewModel.sheetState.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    var editingCell by remember { mutableStateOf<Triple<Int, Int, String>?>(null) } // (row, colIndex, currentValue)
    var showResetDialog by remember { mutableStateOf(false) }

    // Automatic row calculation: dynamically expands as user inputs data, keeping 10 buffer rows
    val totalRowsToDisplay = remember(sheetState.maxRecordedRow) {
        maxOf(20, sheetState.maxRecordedRow + 10)
    }

    val horizontalScrollState = rememberScrollState()
    val columns = sheetState.config.columns

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Spreadsheet View",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${columns.size} Columns • ${sheetState.totalFilledCells} Filled Cells",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("sheet_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Undo Button
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo,
                        modifier = Modifier.testTag("sheet_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                        )
                    }

                    // Redo Button
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo,
                        modifier = Modifier.testTag("sheet_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // DOWNLOAD XLSX BUTTON
                    Button(
                        onClick = onDownloadXlsx,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sheet_download_xlsx_bottom_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DOWNLOAD XLSX",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // RESET ALL BUTTON
                    Button(
                        onClick = { showResetDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sheet_reset_all_bottom_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RESET ALL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Active Round Status Banner
            ActiveRoundBanner(
                currentRow = sheetState.currentRow,
                columns = columns,
                lastColIndex = sheetState.lastColIndex
            )

            // Spreadsheet Grid Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
            ) {
                Column {
                    // Header Row (Column Letters / Names)
                    TableHeaderRow(columns = columns)

                    // Data Rows (LazyColumn for smooth vertical scrolling)
                    val rowIndices = remember(totalRowsToDisplay) { (1..totalRowsToDisplay).toList() }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(rowIndices, key = { it }) { rowNum ->
                            TableDataRow(
                                row = rowNum,
                                isCurrentRound = (rowNum == sheetState.currentRow),
                                columns = columns,
                                cells = sheetState.cells,
                                onCellClick = { colIdx, currentValue ->
                                    editingCell = Triple(rowNum, colIdx, currentValue)
                                }
                            )
                        }

                        item {
                            // Extra space at bottom
                            Spacer(modifier = Modifier.height(48.dp))
                        }
                    }
                }
            }
        }
    }

    // Cell Edit Dialog
    editingCell?.let { (row, colIdx, currentValue) ->
        val colName = columns.getOrElse(colIdx) { "Col $colIdx" }
        var textInput by remember { mutableStateOf(currentValue) }

        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = {
                Text(
                    text = "Edit Cell: $colName$row",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter or edit content for cell $colName in Row $row:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cell_edit_text_field"),
                        placeholder = { Text("Cell text") },
                        singleLine = false,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCell(row, colIdx, textInput)
                        editingCell = null
                    },
                    modifier = Modifier.testTag("cell_edit_save_button")
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                Row {
                    if (currentValue.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.updateCell(row, colIdx, "")
                                editingCell = null
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.testTag("cell_edit_clear_button")
                        ) {
                            Text("CLEAR")
                        }
                    }
                    TextButton(
                        onClick = { editingCell = null }
                    ) {
                        Text("CANCEL")
                    }
                }
            }
        )
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Delete all sheet data?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("This will reset all cells and return to Row 1. Your column configuration will remain.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllData()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("dialog_reset_confirm_sheet")
                ) {
                    Text("RESET")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
private fun ActiveRoundBanner(
    currentRow: Int,
    columns: List<String>,
    lastColIndex: Int
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(PrimaryLight)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Active Round: Row $currentRow",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    val nextColName = if (lastColIndex < 0) {
                        columns.firstOrNull() ?: "A"
                    } else {
                        val nextIdx = (lastColIndex + 1) % columns.size
                        columns.getOrElse(nextIdx) { "A" }
                    }
                    Text(
                        text = "Next expected: $nextColName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ) {
                Text(
                    text = "Auto-Expanded",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TableHeaderRow(columns: List<String>) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, Slate200)
    ) {
        // Top-left corner row index header
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(44.dp)
                .border(0.5.dp, Slate200),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }

        // Column Headers
        columns.forEachIndexed { index, colName ->
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(44.dp)
                    .border(0.5.dp, Slate200)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = colName,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TableDataRow(
    row: Int,
    isCurrentRound: Boolean,
    columns: List<String>,
    cells: Map<Pair<Int, Int>, String>,
    onCellClick: (colIndex: Int, currentValue: String) -> Unit
) {
    val rowBgColor = when {
        isCurrentRound -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        row % 2 == 0 -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Row(
        modifier = Modifier
            .background(rowBgColor)
            .border(0.5.dp, Slate200)
    ) {
        // Row Number Cell
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(44.dp)
                .border(0.5.dp, Slate200)
                .background(if (isCurrentRound) PrimaryLight.copy(alpha = 0.15f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$row",
                fontWeight = if (isCurrentRound) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isCurrentRound) PrimaryLight else Slate700,
                fontSize = 12.sp
            )
        }

        // Data Cells
        columns.forEachIndexed { colIdx, _ ->
            val value = cells[row to colIdx].orEmpty()
            val hasData = value.isNotEmpty()

            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(44.dp)
                    .border(0.5.dp, Slate200)
                    .clickable { onCellClick(colIdx, value) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (hasData) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    // Empty cell placeholder indicator
                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
