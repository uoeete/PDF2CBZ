package com.pdf2cbz.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pdf2cbz.app.ui.screens.CompletionScreen
import com.pdf2cbz.app.ui.screens.ConversionScreen
import com.pdf2cbz.app.ui.screens.FileSelectionScreen
import com.pdf2cbz.app.ui.screens.HomeScreen
import com.pdf2cbz.app.ui.screens.SettingsScreen
import com.pdf2cbz.app.ui.theme.Pdf2CbzTheme
import com.pdf2cbz.app.ui.viewmodel.MainViewModel

private object Routes {
    const val HOME = "home"
    const val FILES = "files"
    const val CONVERSION = "conversion"
    const val COMPLETION = "completion"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val queue by viewModel.queue.collectAsStateWithLifecycle()
            val progress by viewModel.progress.collectAsStateWithLifecycle()
            val history by viewModel.history.collectAsStateWithLifecycle()

            val navController = rememberNavController()
            val context = LocalContext.current

            val pdfPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    viewModel.onPdfFilesPicked(uris)
                    if (navController.currentDestination?.route == Routes.HOME) {
                        navController.navigate(Routes.FILES)
                    }
                }
            }

            val folderPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    viewModel.onOutputFolderPicked(uri)
                }
            }

            val openOutputFolder: () -> Unit = {
                val treeUriStr = settings.outputTreeUri
                if (treeUriStr.isNullOrBlank()) {
                    Toast.makeText(context, "保存先フォルダが設定されていません。", Toast.LENGTH_SHORT).show()
                } else {
                    val treeUri = Uri.parse(treeUriStr)
                    val docUri = runCatching {
                        DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            DocumentsContract.getTreeDocumentId(treeUri)
                        )
                    }.getOrDefault(treeUri)

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching {
                        context.startActivity(intent)
                    }.onFailure {
                        Toast.makeText(
                            context,
                            "保存先: ${settings.outputTreeDisplayName}（ファイルアプリからご確認ください）",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            Pdf2CbzTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            settings = settings,
                            queue = queue,
                            progress = progress,
                            history = history,
                            onPickPdfs = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            onPickOutputFolder = { folderPickerLauncher.launch(null) },
                            onOpenFileSelection = { navController.navigate(Routes.FILES) },
                            onStartConversion = {
                                viewModel.startConversion()
                                navController.navigate(Routes.CONVERSION)
                            },
                            onOpenConversionProgress = { navController.navigate(Routes.CONVERSION) },
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                        )
                    }

                    composable(Routes.FILES) {
                        FileSelectionScreen(
                            queue = queue,
                            settings = settings,
                            onBack = { navController.popBackStack() },
                            onAddMorePdfs = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            onRemoveItem = { viewModel.removeQueueItem(it) },
                            onClearAll = { viewModel.clearAllQueue() },
                            onStartConversion = {
                                viewModel.startConversion()
                                navController.navigate(Routes.CONVERSION) {
                                    popUpTo(Routes.HOME)
                                }
                            }
                        )
                    }

                    composable(Routes.CONVERSION) {
                        ConversionScreen(
                            progress = progress,
                            onPause = { viewModel.pauseConversion() },
                            onResume = { viewModel.resumeConversion() },
                            onCancel = { viewModel.cancelConversion() },
                            onFinishedNavigate = {
                                navController.navigate(Routes.COMPLETION) {
                                    popUpTo(Routes.HOME)
                                }
                            }
                        )
                    }

                    composable(Routes.COMPLETION) {
                        CompletionScreen(
                            queue = queue,
                            settings = settings,
                            onOpenOutputFolder = openOutputFolder,
                            onRetryFailed = {
                                viewModel.retryFailed()
                                navController.navigate(Routes.CONVERSION) {
                                    popUpTo(Routes.HOME)
                                }
                            },
                            onBackHome = {
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            }
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            settings = settings,
                            historyCount = history.size,
                            onBack = { navController.popBackStack() },
                            onPickOutputFolder = { folderPickerLauncher.launch(null) },
                            onFormatChange = viewModel::setImageFormat,
                            onDpiChange = viewModel::setDpi,
                            onQualityChange = viewModel::setJpegQuality,
                            onPreferDirectChange = viewModel::setPreferDirectExtraction,
                            onComicInfoChange = viewModel::setIncludeComicInfo,
                            onDuplicatePolicyChange = viewModel::setDuplicatePolicy,
                            onThemeModeChange = viewModel::setThemeMode,
                            onDynamicColorChange = viewModel::setDynamicColor,
                            onSourcePdfActionChange = viewModel::setSourcePdfAction,
                            onAutoDeleteTempChange = viewModel::setAutoDeleteTemp,
                            onClearHistory = viewModel::clearHistory
                        )
                    }
                }
            }
        }
    }
}
