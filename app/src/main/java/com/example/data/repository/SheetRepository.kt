package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.CellHistoryAction
import com.example.data.model.ClipboardInsertResult
import com.example.data.model.SheetConfig
import com.example.data.model.SheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.ArrayDeque

class SheetRepository(private val context: Context) {

    private val _sheetState = MutableStateFlow(SheetState())
    val sheetState: StateFlow<SheetState> = _sheetState.asStateFlow()

    private val undoStack = ArrayDeque<CellHistoryAction>()
    private val redoStack = ArrayDeque<CellHistoryAction>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val dataFile: File by lazy { File(context.filesDir, "sheet_data.json") }
    private val configFile: File by lazy { File(context.filesDir, "sheet_config.json") }

    init {
        loadFromDisk()
    }

    private fun updateHistoryFlows() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    /**
     * Inserts text from clipboard into the sheet following the strict round/row logic.
     */
    @Synchronized
    fun insertFromClipboard(columnName: String, text: String): ClipboardInsertResult {
        if (text.isEmpty()) {
            return ClipboardInsertResult.EmptyClipboard
        }

        val currentState = _sheetState.value
        val columns = currentState.config.columns
        val colIndex = columns.indexOf(columnName)
        if (colIndex < 0) {
            return ClipboardInsertResult.Error("Column '$columnName' not found")
        }

        val prevRow = currentState.currentRow
        val prevLastCol = currentState.lastColIndex

        val targetRow: Int
        val newLastCol: Int

        if (colIndex == 0) {
            // First column in the configured round
            // If row 1 is completely empty and no column was pressed yet, use row 1.
            val isFirstRoundUnused = (prevRow == 1 && prevLastCol == -1 &&
                    columns.indices.none { c -> currentState.cells.containsKey(1 to c) })

            if (isFirstRoundUnused) {
                targetRow = 1
            } else {
                // Starts the next round/row position
                targetRow = prevRow + 1
            }
            newLastCol = 0
        } else {
            // Non-first column: uses the active round's row
            targetRow = prevRow
            newLastCol = colIndex
        }

        val cellKey = targetRow to colIndex
        val oldValue = currentState.cells[cellKey]

        val newCells = currentState.cells.toMutableMap()
        newCells[cellKey] = text

        val newState = currentState.copy(
            cells = newCells,
            currentRow = targetRow,
            lastColIndex = newLastCol
        )

        // Record history for Undo
        val action = CellHistoryAction(
            row = targetRow,
            colIndex = colIndex,
            previousValue = oldValue,
            newValue = text,
            prevCurrentRow = prevRow,
            newCurrentRow = targetRow,
            prevLastColIndex = prevLastCol,
            newLastColIndex = newLastCol
        )
        undoStack.push(action)
        redoStack.clear()
        updateHistoryFlows()

        _sheetState.value = newState
        saveToDiskAsync()

        return ClipboardInsertResult.Success(
            columnName = columnName,
            row = targetRow,
            content = text
        )
    }

    /**
     * Updates a cell manually (e.g. from the Spreadsheet UI).
     */
    @Synchronized
    fun updateCellManually(row: Int, colIndex: Int, newValue: String) {
        val currentState = _sheetState.value
        val cellKey = row to colIndex
        val oldValue = currentState.cells[cellKey]

        if (oldValue == newValue) return

        val newCells = currentState.cells.toMutableMap()
        if (newValue.isEmpty()) {
            newCells.remove(cellKey)
        } else {
            newCells[cellKey] = newValue
        }

        val action = CellHistoryAction(
            row = row,
            colIndex = colIndex,
            previousValue = oldValue,
            newValue = newValue,
            prevCurrentRow = currentState.currentRow,
            newCurrentRow = currentState.currentRow,
            prevLastColIndex = currentState.lastColIndex,
            newLastColIndex = currentState.lastColIndex
        )
        undoStack.push(action)
        redoStack.clear()
        updateHistoryFlows()

        _sheetState.value = currentState.copy(cells = newCells)
        saveToDiskAsync()
    }

    @Synchronized
    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val action = undoStack.pop()
        redoStack.push(action)
        updateHistoryFlows()

        val currentState = _sheetState.value
        val newCells = currentState.cells.toMutableMap()
        val cellKey = action.row to action.colIndex

        if (action.previousValue == null) {
            newCells.remove(cellKey)
        } else {
            newCells[cellKey] = action.previousValue
        }

        _sheetState.value = currentState.copy(
            cells = newCells,
            currentRow = action.prevCurrentRow,
            lastColIndex = action.prevLastColIndex
        )
        saveToDiskAsync()
        return true
    }

    @Synchronized
    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val action = redoStack.pop()
        undoStack.push(action)
        updateHistoryFlows()

        val currentState = _sheetState.value
        val newCells = currentState.cells.toMutableMap()
        val cellKey = action.row to action.colIndex

        if (action.newValue.isEmpty()) {
            newCells.remove(cellKey)
        } else {
            newCells[cellKey] = action.newValue
        }

        _sheetState.value = currentState.copy(
            cells = newCells,
            currentRow = action.newCurrentRow,
            lastColIndex = action.newLastColIndex
        )
        saveToDiskAsync()
        return true
    }

    /**
     * Resets all cell data and round position. Does NOT delete column configuration.
     */
    @Synchronized
    fun resetAllData() {
        val currentState = _sheetState.value
        undoStack.clear()
        redoStack.clear()
        updateHistoryFlows()

        _sheetState.value = currentState.copy(
            cells = emptyMap(),
            currentRow = 1,
            lastColIndex = -1
        )
        saveToDiskAsync()
    }

    /**
     * Saves configuration changes (columns, button size, save mode).
     */
    @Synchronized
    fun updateConfig(newConfig: SheetConfig) {
        val currentState = _sheetState.value
        _sheetState.value = currentState.copy(config = newConfig)
        saveConfigToDiskAsync(newConfig)
    }

    private fun saveToDiskAsync() {
        val state = _sheetState.value
        scope.launch {
            try {
                val json = JSONObject()
                json.put("currentRow", state.currentRow)
                json.put("lastColIndex", state.lastColIndex)

                val cellsArray = JSONArray()
                for ((key, value) in state.cells) {
                    val cellObj = JSONObject()
                    cellObj.put("r", key.first)
                    cellObj.put("c", key.second)
                    cellObj.put("v", value)
                    cellsArray.put(cellObj)
                }
                json.put("cells", cellsArray)

                dataFile.writeText(json.toString())
            } catch (e: Exception) {
                Log.e("SheetRepository", "Failed to save sheet data", e)
            }
        }
    }

    private fun saveConfigToDiskAsync(config: SheetConfig) {
        scope.launch {
            try {
                val json = JSONObject()
                val colArray = JSONArray()
                config.columns.forEach { colArray.put(it) }
                json.put("columns", colArray)
                json.put("buttonSizeDp", config.buttonSizeDp)
                json.put("isLocalSaveMode", config.isLocalSaveMode)
                json.put("isHorizontalBubbleLayout", config.isHorizontalBubbleLayout)

                configFile.writeText(json.toString())
            } catch (e: Exception) {
                Log.e("SheetRepository", "Failed to save sheet config", e)
            }
        }
    }

    private fun loadFromDisk() {
        var loadedConfig = SheetConfig()
        if (configFile.exists()) {
            try {
                val text = configFile.readText()
                val json = JSONObject(text)
                val colArray = json.optJSONArray("columns")
                val cols = mutableListOf<String>()
                if (colArray != null) {
                    for (i in 0 until colArray.length()) {
                        cols.add(colArray.getString(i))
                    }
                }
                val buttonSize = json.optInt("buttonSizeDp", SheetConfig.DEFAULT_BUTTON_SIZE)
                val isLocal = json.optBoolean("isLocalSaveMode", true)
                val isHorizontal = json.optBoolean("isHorizontalBubbleLayout", false)

                loadedConfig = SheetConfig(
                    columns = if (cols.isNotEmpty()) cols else listOf("A", "B", "C"),
                    buttonSizeDp = buttonSize,
                    isLocalSaveMode = isLocal,
                    isHorizontalBubbleLayout = isHorizontal
                )
            } catch (e: Exception) {
                Log.e("SheetRepository", "Failed to read sheet config", e)
            }
        }

        var loadedRow = 1
        var loadedLastCol = -1
        val loadedCells = mutableMapOf<Pair<Int, Int>, String>()

        if (dataFile.exists()) {
            try {
                val text = dataFile.readText()
                val json = JSONObject(text)
                loadedRow = json.optInt("currentRow", 1)
                loadedLastCol = json.optInt("lastColIndex", -1)

                val cellsArray = json.optJSONArray("cells")
                if (cellsArray != null) {
                    for (i in 0 until cellsArray.length()) {
                        val obj = cellsArray.getJSONObject(i)
                        val r = obj.getInt("r")
                        val c = obj.getInt("c")
                        val v = obj.getString("v")
                        loadedCells[r to c] = v
                    }
                }
            } catch (e: Exception) {
                Log.e("SheetRepository", "Failed to read sheet data", e)
            }
        }

        _sheetState.value = SheetState(
            config = loadedConfig,
            cells = loadedCells,
            currentRow = loadedRow,
            lastColIndex = loadedLastCol
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: SheetRepository? = null

        fun getInstance(context: Context): SheetRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SheetRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
