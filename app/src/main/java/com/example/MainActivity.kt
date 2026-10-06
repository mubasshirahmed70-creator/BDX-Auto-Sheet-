package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.SheetScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.UiEvent
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreen {
    MAIN,
    SHEET,
    SETUP
}

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    // Storage Access Framework launcher to save XLSX file to user's desired location
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportXlsxToUri(uri)
        }
    }

    // Overlay permission launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (MainViewModel.hasOverlayPermission(this)) {
            viewModel.checkAndStartFloatingBubble(this)
        }
    }

    // Notification permission launcher for Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Continue even if notifications denied
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission if Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MyApplicationTheme {
                viewModel = viewModel<MainViewModel>()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }

                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            is UiEvent.ShowMessage -> {
                                scope.launch {
                                    snackbarHostState.showSnackbar(event.message)
                                }
                            }
                            is UiEvent.RequestOverlayPermission -> {
                                overlayPermissionLauncher.launch(event.intent)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Please allow 'Display over other apps' to use floating bubbles"
                                    )
                                }
                            }
                            is UiEvent.ShareXlsxFile -> {
                                shareExportedFile(event.file)
                            }
                        }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                slideInHorizontally { width -> width } togetherWith
                                        slideOutHorizontally { width -> -width }
                            } else {
                                slideInHorizontally { width -> -width } togetherWith
                                        slideOutHorizontally { width -> width }
                            }
                        },
                        label = "ScreenTransition",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) { screen ->
                        when (screen) {
                            AppScreen.MAIN -> {
                                MainScreen(
                                    viewModel = viewModel,
                                    onNavigateToSheet = { currentScreen = AppScreen.SHEET },
                                    onNavigateToSetup = { currentScreen = AppScreen.SETUP }
                                )
                            }
                            AppScreen.SHEET -> {
                                SheetScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = AppScreen.MAIN },
                                    onDownloadXlsx = { startXlsxExportFlow() }
                                )
                            }
                            AppScreen.SETUP -> {
                                SetupScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = AppScreen.MAIN }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startXlsxExportFlow() {
        val fileName = "BDX_AutoSheet_${System.currentTimeMillis()}.xlsx"
        createDocumentLauncher.launch(fileName)
    }

    private fun shareExportedFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share XLSX Spreadsheet"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
