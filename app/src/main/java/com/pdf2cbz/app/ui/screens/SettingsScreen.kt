package com.pdf2cbz.app.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdf2cbz.app.data.model.AppSettings
import com.pdf2cbz.app.data.model.DpiOption
import com.pdf2cbz.app.data.model.DuplicatePolicy
import com.pdf2cbz.app.data.model.ImageFormat
import com.pdf2cbz.app.data.model.SourcePdfAction
import com.pdf2cbz.app.data.model.ThemeMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    historyCount: Int,
    onBack: () -> Unit,
    onPickOutputFolder: () -> Unit,
    onFormatChange: (ImageFormat) -> Unit,
    onDpiChange: (Int) -> Unit,
    onQualityChange: (Int) -> Unit,
    onPreferDirectChange: (Boolean) -> Unit,
    onComicInfoChange: (Boolean) -> Unit,
    onDuplicatePolicyChange: (DuplicatePolicy) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onSourcePdfActionChange: (SourcePdfAction) -> Unit,
    onAutoDeleteTempChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit
) {
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showEngineDetails by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("変換履歴を削除しますか？") },
            text = {
                Text("保存されている${historyCount}件の変換履歴を削除します。出力済みのCBZファイル自体は削除されません。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearHistoryDialog = false
                        onClearHistory()
                        scope.launch {
                            snackbarHostState.showSnackbar("変換履歴を削除しました")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_clear_history_button")
                ) {
                    Text("削除する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("設定", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "ホームに戻る"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .testTag("settings_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SettingsCard(
                        title = "画像出力設定",
                        icon = Icons.Rounded.Image
                    ) {
                        Text("画像形式", style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ImageFormat.entries.forEachIndexed { index, format ->
                                SegmentedButton(
                                    selected = settings.imageFormat == format,
                                    onClick = { onFormatChange(format) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ImageFormat.entries.size
                                    ),
                                    modifier = Modifier.testTag("format_chip_${format.name}")
                                ) {
                                    Text(format.displayName, maxLines = 1)
                                }
                            }
                        }
                        Text(
                            text = when (settings.imageFormat) {
                                ImageFormat.JPEG -> "漫画向けの標準形式。直接抽出に対応し最速・軽量です。"
                                ImageFormat.PNG -> "可逆圧縮（劣化なし）。ファイル容量は大きくなります。"
                                ImageFormat.WEBP -> "高圧縮率で軽量ですが、対応ビューアをご確認ください。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text("解像度 (DPI)", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "ページを画像としてレンダリングする際の解像度です（直接抽出時は元の画質を維持）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DpiOption.entries.forEach { option ->
                                FilterChip(
                                    selected = settings.dpi == option.dpi,
                                    onClick = { onDpiChange(option.dpi) },
                                    label = { Text("${option.dpi} DPI") },
                                    modifier = Modifier.testTag("dpi_chip_${option.dpi}")
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = settings.imageFormat != ImageFormat.PNG,
                            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                            exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (settings.imageFormat == ImageFormat.JPEG) "JPEG品質" else "WebP品質",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${settings.jpegQuality}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Slider(
                                    value = settings.jpegQuality.toFloat(),
                                    onValueChange = { onQualityChange(it.roundToInt().coerceIn(1, 100)) },
                                    valueRange = 1f..100f,
                                    modifier = Modifier.testTag("quality_slider")
                                )
                                Text(
                                    text = "85〜92前後が画質とCBZ容量のバランスに優れています。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        SwitchRow(
                            title = "PDF内画像の直接抽出を優先",
                            subtitle = "JPEG形式時に再圧縮なしで高速抽出します（回転・注釈・テキスト・複合レイヤー等は自動で高精度レンダリングへ切り替え）",
                            checked = settings.preferDirectImageExtraction,
                            onCheckedChange = onPreferDirectChange,
                            testTag = "prefer_direct_switch"
                        )

                        SwitchRow(
                            title = "ComicInfo.xmlを同梱",
                            subtitle = "Mihon等のコミックビューア向けにページ数・タイトル情報をCBZ内に含めます",
                            checked = settings.includeComicInfoXml,
                            onCheckedChange = onComicInfoChange,
                            testTag = "comic_info_switch"
                        )
                    }
                }

                item {
                    SettingsCard(
                        title = "保存先とファイル管理",
                        icon = Icons.Rounded.FolderOpen
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("保存先フォルダ", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = settings.outputTreeDisplayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = onPickOutputFolder,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_pick_folder_button")
                            ) {
                                Text("選択")
                            }
                        }

                        Text("同名CBZが存在する場合", style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            DuplicatePolicy.entries.forEachIndexed { index, policy ->
                                val shortLabel = when (policy) {
                                    DuplicatePolicy.RENAME -> "別名保存"
                                    DuplicatePolicy.OVERWRITE -> "上書き"
                                    DuplicatePolicy.SKIP -> "スキップ"
                                }
                                SegmentedButton(
                                    selected = settings.duplicatePolicy == policy,
                                    onClick = { onDuplicatePolicyChange(policy) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = DuplicatePolicy.entries.size
                                    )
                                ) {
                                    Text(shortLabel, maxLines = 1)
                                }
                            }
                        }
                        Text(
                            text = settings.duplicatePolicy.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text("変換後の元PDFの扱い", style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SourcePdfAction.entries.forEachIndexed { index, action ->
                                val shortLabel = when (action) {
                                    SourcePdfAction.KEEP -> "削除しない"
                                    SourcePdfAction.DELETE_AFTER_SUCCESS -> "成功後削除"
                                }
                                SegmentedButton(
                                    selected = settings.sourcePdfAction == action,
                                    onClick = { onSourcePdfActionChange(action) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = SourcePdfAction.entries.size
                                    )
                                ) {
                                    Text(shortLabel, maxLines = 1)
                                }
                            }
                        }
                        Text(
                            text = settings.sourcePdfAction.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (settings.sourcePdfAction == SourcePdfAction.DELETE_AFTER_SUCCESS) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        SwitchRow(
                            title = "一時ファイルを自動削除",
                            subtitle = "1冊の変換が完了するたびに一時キャッシュを即座に解放します",
                            checked = settings.autoDeleteTempFiles,
                            onCheckedChange = onAutoDeleteTempChange,
                            testTag = "auto_delete_temp_switch"
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showEngineDetails = !showEngineDetails }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Speed,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "高速変換エンジンの仕組み",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showEngineDetails) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = if (showEngineDetails) "詳細を閉じる" else "詳細を開く"
                                    )
                                }
                                AnimatedVisibility(
                                    visible = showEngineDetails,
                                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                                    exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
                                ) {
                                    Text(
                                        text = "・ゼロコピーJPEG抽出で対応ページの再圧縮をスキップ\n" +
                                            "・並列PdfRendererワーカー + BitmapPool再利用でGC負荷を削減\n" +
                                            "・無圧縮ZipEntry.STOREDで単一ストリームへ直接CBZ書き出し",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SettingsCard(
                        title = "外観と履歴",
                        icon = Icons.Rounded.Palette
                    ) {
                        Text("テーマ設定", style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                val shortLabel = when (mode) {
                                    ThemeMode.SYSTEM -> "システム"
                                    ThemeMode.LIGHT -> "ライト"
                                    ThemeMode.DARK -> "ダーク"
                                }
                                SegmentedButton(
                                    selected = settings.themeMode == mode,
                                    onClick = { onThemeModeChange(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size
                                    ),
                                    modifier = Modifier.testTag("theme_chip_${mode.name}")
                                ) {
                                    Text(shortLabel, maxLines = 1)
                                }
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SwitchRow(
                                title = "Material You ダイナミックカラー",
                                subtitle = "Android端末の壁紙に合わせた配色を適用します",
                                checked = settings.dynamicColor,
                                onCheckedChange = onDynamicColorChange,
                                testTag = "dynamic_color_switch"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("変換履歴の管理", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "保存されている履歴: ${historyCount}件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { showClearHistoryDialog = true },
                                enabled = historyCount > 0,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("clear_history_button")
                            ) {
                                Text("履歴を削除")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
