package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.clipboard.ClipboardHelper
import com.example.clipboard.ClipboardReadResult
import com.example.data.model.ClipboardInsertResult
import com.example.data.model.SheetConfig
import com.example.data.model.SheetState
import com.example.data.repository.SheetRepository
import com.example.export.XlsxExporter
import com.example.service.FloatingBubbleService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class UiEvent {
    data class ShowMessage(val message: String) : UiEvent()
    data class RequestOverlayPermission(val intent: Intent) : UiEvent()
    data class ShareXlsxFile(val file: File) : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SheetRepository.getInstance(application)

    val sheetState: StateFlow<SheetState> = repository.sheetState
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.sheetState.value)

    val isServiceRunning: StateFlow<Boolean> = FloatingBubbleService.isRunning

    val canUndo: StateFlow<Boolean> = repository.canUndo
    val canRedo: StateFlow<Boolean> = repository.canRedo

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // Temporary config state for Setup screen
    private val _setupColumnsInput = MutableStateFlow(SheetConfig.DEFAULT_COLUMNS_STR)
    val setupColumnsInput: StateFlow<String> = _setupColumnsInput.asStateFlow()

    private val _setupButtonSize = MutableStateFlow(SheetConfig.DEFAULT_BUTTON_SIZE.toFloat())
    val setupButtonSize: StateFlow<Float> = _setupButtonSize.asStateFlow()

    private val _setupIsLocalSaveMode = MutableStateFlow(true)
    val setupIsLocalSaveMode: StateFlow<Boolean> = _setupIsLocalSaveMode.asStateFlow()

    private val _setupIsHorizontal = MutableStateFlow(false)
    val setupIsHorizontal: StateFlow<Boolean> = _setupIsHorizontal.asStateFlow()

    init {
        val currentConfig = repository.sheetState.value.config
        _setupColumnsInput.value = currentConfig.columns.joinToString(",")
        _setupButtonSize.value = currentConfig.buttonSizeDp.toFloat()
        _setupIsLocalSaveMode.value = currentConfig.isLocalSaveMode
        _setupIsHorizontal.value = currentConfig.isHorizontalBubbleLayout
    }

    fun syncSetupWithCurrentConfig() {
        val currentConfig = sheetState.value.config
        _setupColumnsInput.value = currentConfig.columns.joinToString(",")
        _setupButtonSize.value = currentConfig.buttonSizeDp.toFloat()
        _setupIsLocalSaveMode.value = currentConfig.isLocalSaveMode
        _setupIsHorizontal.value = currentConfig.isHorizontalBubbleLayout
    }

    fun onSetupColumnsChanged(input: String) {
        _setupColumnsInput.value = input
    }

    fun onSetupButtonSizeChanged(size: Float) {
        _setupButtonSize.value = size
    }

    fun onSetupLocalSaveModeToggled(enabled: Boolean) {
        _setupIsLocalSaveMode.value = enabled
    }

    fun onSetupOrientationToggled(isHorizontal: Boolean) {
        _setupIsHorizontal.value = isHorizontal
    }

    fun saveConfiguration() {
        val parsedCols = SheetConfig.parseColumns(_setupColumnsInput.value)
        val newConfig = SheetConfig(
            columns = parsedCols,
            buttonSizeDp = _setupButtonSize.value.toInt(),
            isLocalSaveMode = _setupIsLocalSaveMode.value,
            isHorizontalBubbleLayout = _setupIsHorizontal.value
        )
        repository.updateConfig(newConfig)
        _setupColumnsInput.value = parsedCols.joinToString(",")
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowMessage("Configuration saved successfully!"))
        }
    }

    fun checkAndStartFloatingBubble(context: Context) {
        if (!hasOverlayPermission(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.RequestOverlayPermission(intent))
            }
            return
        }

        FloatingBubbleService.start(context)
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowMessage("Floating bubble service started"))
        }
    }

    fun stopFloatingBubble(context: Context) {
        FloatingBubbleService.stop(context)
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowMessage("Floating bubble service stopped"))
        }
    }

    fun undo() {
        if (repository.undo()) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowMessage("Action undone"))
            }
        }
    }

    fun redo() {
        if (repository.redo()) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowMessage("Action redone"))
            }
        }
    }

    fun resetAllData() {
        repository.resetAllData()
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowMessage("All sheet data has been reset"))
        }
    }

    fun updateCell(row: Int, colIndex: Int, value: String) {
        repository.updateCellManually(row, colIndex, value)
    }

    fun insertCurrentClipboardIntoColumn(columnName: String, context: Context = getApplication()) {
        when (val clip = ClipboardHelper.readCurrentText(context)) {
            is ClipboardReadResult.Text -> {
                when (val result = repository.insertFromClipboard(columnName, clip.content)) {
                    is ClipboardInsertResult.Success -> {
                        viewModelScope.launch {
                            _uiEvents.emit(UiEvent.ShowMessage("${result.columnName}${result.row} saved"))
                        }
                    }
                    is ClipboardInsertResult.EmptyClipboard -> {
                        viewModelScope.launch {
                            _uiEvents.emit(UiEvent.ShowMessage("Clipboard is empty."))
                        }
                    }
                    is ClipboardInsertResult.NoUsableText -> {
                        viewModelScope.launch {
                            _uiEvents.emit(UiEvent.ShowMessage("Clipboard does not contain usable text."))
                        }
                    }
                    is ClipboardInsertResult.Error -> {
                        viewModelScope.launch {
                            _uiEvents.emit(UiEvent.ShowMessage("Error: ${result.message}"))
                        }
                    }
                }
            }
            is ClipboardReadResult.Empty -> {
                viewModelScope.launch {
                    _uiEvents.emit(UiEvent.ShowMessage("Clipboard is empty."))
                }
            }
            is ClipboardReadResult.NonTextContent -> {
                viewModelScope.launch {
                    _uiEvents.emit(UiEvent.ShowMessage("Clipboard does not contain usable text."))
                }
            }
            is ClipboardReadResult.Error -> {
                viewModelScope.launch {
                    _uiEvents.emit(UiEvent.ShowMessage("Clipboard error: ${clip.error}"))
                }
            }
        }
    }

    fun exportXlsxToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val outputStream = app.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    XlsxExporter.exportToStream(sheetState.value, outputStream)
                    outputStream.close()
                    _uiEvents.emit(UiEvent.ShowMessage("XLSX exported successfully!"))
                } else {
                    _uiEvents.emit(UiEvent.ShowMessage("Failed to open file output stream"))
                }
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowMessage("Export error: ${e.message}"))
            }
        }
    }

    fun prepareAndShareXlsx() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val cacheDir = app.cacheDir
                val file = File(cacheDir, "BDX_AutoSheet_${System.currentTimeMillis()}.xlsx")
                val fos = FileOutputStream(file)
                XlsxExporter.exportToStream(sheetState.value, fos)
                fos.close()
                _uiEvents.emit(UiEvent.ShareXlsxFile(file))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowMessage("Export error: ${e.message}"))
            }
        }
    }

    companion object {
        var hasShownSplashThisSession: Boolean = false

        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }

    fun markSplashCompleted() {
        hasShownSplashThisSession = true
    }
}
