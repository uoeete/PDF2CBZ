package com.pdf2cbz.app.ui.screens

import android.os.Build
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdf2cbz.app.data.model.AppSettings
import com.pdf2cbz.app.data.model.DpiOption
import com.pdf2cbz.app.data.model.DuplicatePolicy
import com.pdf2cbz.app.data.model.ImageFormat
import com.pdf2cbz.app.data.model.SourcePdfAction
import com.pdf2cbz.app.data.model.ThemeMode
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
    Scaffold(
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
                            contentDescription = "戻る"
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
                    SettingsCard(title = "画像出力設定") {
                        Text("画像形式", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ImageFormat.entries.forEach { format ->
                                FilterChip(
                                    selected = settings.imageFormat == format,
                                    onClick = { onFormatChange(format) },
                                    label = { Text(format.displayName) },
                                    modifier = Modifier.testTag("format_chip_${format.name}")
                                )
                            }
                        }

                        Text("解像度 (DPI)", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                        if (settings.imageFormat != ImageFormat.PNG) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (settings.imageFormat == ImageFormat.JPEG) "JPEG品質" else "WebP品質",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "${settings.jpegQuality}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = settings.jpegQuality.toFloat(),
                                onValueChange = { onQualityChange(it.roundToInt().coerceIn(1, 100)) },
                                valueRange = 1f..100f,
                                modifier = Modifier.testTag("quality_slider")
                            )
                        }

                        SwitchRow(
                            title = "PDF内画像の直接抽出を優先",
                            subtitle = "再圧縮なしで高速抽出します（回転・注釈・テキスト・複合レイヤー等は自動で並列レンダリング）",
                            checked = settings.preferDirectImageExtraction,
                            onCheckedChange = onPreferDirectChange,
                            testTag = "prefer_direct_switch"
                        )

                        SwitchRow(
                            title = "ComicInfo.xmlを同梱",
                            subtitle = "Mihon等のリーダー向けにページ数・タイトルメタデータを出力します",
                            checked = settings.includeComicInfoXml,
                            onCheckedChange = onComicInfoChange,
                            testTag = "comic_info_switch"
                        )
                    }
                }

                item {
                    SettingsCard(title = "保存先とファイル管理") {
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = onPickOutputFolder,
                                modifier = Modifier.testTag("settings_pick_folder_button")
                            ) {
                                Text("選択")
                            }
                        }

                        Text("同名CBZが存在する場合", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DuplicatePolicy.entries.forEach { policy ->
                                FilterChip(
                                    selected = settings.duplicatePolicy == policy,
                                    onClick = { onDuplicatePolicyChange(policy) },
                                    label = { Text(policy.label) }
                                )
                            }
                        }

                        Text("変換後の元PDFの扱い", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SourcePdfAction.entries.forEach { action ->
                                FilterChip(
                                    selected = settings.sourcePdfAction == action,
                                    onClick = { onSourcePdfActionChange(action) },
                                    label = { Text(action.label) }
                                )
                            }
                        }

                        SwitchRow(
                            title = "一時ファイルを自動削除",
                            subtitle = "1冊ごとに変換用キャッシュを即座に解放します",
                            checked = settings.autoDeleteTempFiles,
                            onCheckedChange = onAutoDeleteTempChange,
                            testTag = "auto_delete_temp_switch"
                        )
                    }
                }

                item {
                    SettingsCard(title = "外観と履歴") {
                        Text("ダークモード", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { onThemeModeChange(mode) },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("theme_chip_${mode.name}")
                                )
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SwitchRow(
                                title = "Material You ダイナミックカラー",
                                subtitle = "壁紙の色調に合わせた配色を適用します",
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
                                onClick = onClearHistory,
                                enabled = historyCount > 0,
                                modifier = Modifier.testTag("clear_history_button")
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
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
        modifier = Modifier.fillMaxWidth(),
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
