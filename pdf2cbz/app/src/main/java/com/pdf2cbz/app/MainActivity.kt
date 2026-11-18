package com.pdf2cbz.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
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
import kotlinx.serialization.Serializable

@Serializable
internal object HomeRoute

@Serializable
internal object FilesRoute

@Serializable
internal object ConversionRoute

@Serializable
internal object CompletionRoute

@Serializable
internal object SettingsRoute

private const val TRANSITION_DURATION_MS = 200

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

            val startConversionAndNavigate = {
                viewModel.startConversion()
                navController.navigate(ConversionRoute) {
                    popUpTo(HomeRoute)
                }
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ ->
                // Proceed with WorkManager conversion regardless of notification grant/deny
                startConversionAndNavigate()
            }

            val launchConversionWithPermissionCheck = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startConversionAndNavigate()
                }
            }

            val pdfPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    viewModel.onPdfFilesPicked(uris)
                    if (navController.currentDestination?.route?.contains("HomeRoute") == true) {
                        navController.navigate(FilesRoute)
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
                val slideSpec = tween<IntOffset>(
                    durationMillis = TRANSITION_DURATION_MS,
                    easing = FastOutSlowInEasing
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        enterTransition = {
                            slideInHorizontally(
                                animationSpec = slideSpec,
                                initialOffsetX = { fullWidth -> fullWidth }
                            )
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                animationSpec = slideSpec,
                                targetOffsetX = { fullWidth -> -fullWidth }
                            )
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                animationSpec = slideSpec,
                                initialOffsetX = { fullWidth -> -fullWidth }
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                animationSpec = slideSpec,
                                targetOffsetX = { fullWidth -> fullWidth }
                            )
                        }
                    ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            settings = settings,
                            queue = queue,
                            progress = progress,
                            history = history,
                            onPickPdfs = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            onPickOutputFolder = { folderPickerLauncher.launch(null) },
                            onOpenFileSelection = { navController.navigate(FilesRoute) },
                            onStartConversion = launchConversionWithPermissionCheck,
                            onOpenConversionProgress = { navController.navigate(ConversionRoute) },
                            onOpenSettings = { navController.navigate(SettingsRoute) }
                        )
                    }

                    composable<FilesRoute> {
                        FileSelectionScreen(
                            queue = queue,
                            settings = settings,
                            onBack = { navController.popBackStack() },
                            onAddMorePdfs = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            onRemoveItem = { viewModel.removeQueueItem(it) },
                            onClearAll = { viewModel.clearAllQueue() },
                            onStartConversion = launchConversionWithPermissionCheck
                        )
                    }

                    composable<ConversionRoute> {
                        ConversionScreen(
                            progress = progress,
                            onPause = { viewModel.pauseConversion() },
                            onResume = { viewModel.resumeConversion() },
                            onCancel = { viewModel.cancelConversion() },
                            onFinishedNavigate = {
                                navController.navigate(CompletionRoute) {
                                    popUpTo(HomeRoute)
                                }
                            }
                        )
                    }

                    composable<CompletionRoute> {
                        CompletionScreen(
                            queue = queue,
                            settings = settings,
                            onOpenOutputFolder = openOutputFolder,
                            onRetryFailed = {
                                viewModel.retryFailed()
                                navController.navigate(ConversionRoute) {
                                    popUpTo(HomeRoute)
                                }
                            },
                            onBackHome = {
                                navController.popBackStack(HomeRoute, inclusive = false)
                            }
                        )
                    }

                    composable<SettingsRoute> {
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
}
