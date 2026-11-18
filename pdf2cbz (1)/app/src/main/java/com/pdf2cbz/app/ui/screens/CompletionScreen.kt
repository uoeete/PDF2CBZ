package com.pdf2cbz.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdf2cbz.app.data.model.AppSettings
import com.pdf2cbz.app.data.model.PdfQueueItem
import com.pdf2cbz.app.data.model.QueueItemStatus
import com.pdf2cbz.app.domain.util.FilenameUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletionScreen(
    queue: List<PdfQueueItem>,
    settings: AppSettings,
    onOpenOutputFolder: () -> Unit,
    onRetryFailed: () -> Unit,
    onBackHome: () -> Unit
) {
    val succeeded = queue.filter { it.status == QueueItemStatus.COMPLETED }
    val failed = queue.filter { it.status == QueueItemStatus.FAILED || it.status == QueueItemStatus.CANCELLED }

    val totalPages = succeeded.sumOf { it.pageCount }
    val totalDurationMs = succeeded.sumOf { it.durationMillis }
    val totalSizeBytes = succeeded.sumOf { it.outputSizeBytes }
    val avgPagesPerSec = if (totalDurationMs > 0L && totalPages > 0) {
        totalPages.toFloat() / (totalDurationMs / 1000f)
    } else {
        0f
    }

    var badgeVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        badgeVisible = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("変換完了", style = MaterialTheme.typography.titleLarge) }
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
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedVisibility(
                                visible = badgeVisible,
                                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = tween(220))
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (failed.isEmpty()) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    },
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (failed.isEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                                            contentDescription = if (failed.isEmpty()) "変換完了" else "一部エラーあり",
                                            tint = if (failed.isEmpty()) {
                                                MaterialTheme.colorScheme.onTertiaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            },
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (failed.isEmpty()) {
                                    "${succeeded.size}冊のCBZを作成しました"
                                } else {
                                    "変換処理が終了しました（一部未完了）"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("生成CBZ", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        text = "${succeeded.size}冊",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("エラー", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        text = "${failed.size}冊",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (failed.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (succeeded.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        SummaryStat("総ページ", "${totalPages}P")
                                        SummaryStat(
                                            "処理時間",
                                            if (totalDurationMs > 0L) FilenameUtils.formatDuration(totalDurationMs) else "-"
                                        )
                                        SummaryStat(
                                            "処理速度",
                                            if (avgPagesPerSec > 0.1f) String.format(Locale.US, "%.1f P/秒", avgPagesPerSec) else "-"
                                        )
                                        SummaryStat("合計サイズ", FilenameUtils.formatByteSize(totalSizeBytes))
                                    }
                                }
                            }

                            Text(
                                text = "保存先: ${settings.outputTreeDisplayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (failed.isNotEmpty()) {
                                    FilledTonalButton(
                                        onClick = onOpenOutputFolder,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("open_output_folder_button")
                                    ) {
                                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("保存先を開く")
                                    }
                                    Button(
                                        onClick = onRetryFailed,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("retry_failed_button")
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("再試行 (${failed.size})")
                                    }
                                } else {
                                    Button(
                                        onClick = onOpenOutputFolder,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("open_output_folder_button")
                                    ) {
                                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("保存先フォルダを開く")
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = onBackHome,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("back_home_button")
                            ) {
                                Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("ホームへ戻る")
                            }
                        }
                    }
                }

                if (failed.isNotEmpty()) {
                    item {
                        Text(
                            text = "エラーが発生したファイル",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    items(failed, key = { "fail_${it.id}" }) { item ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = "エラー",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Column {
                                    Text(
                                        text = item.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.errorMessage ?: "変換に失敗しました。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (succeeded.isNotEmpty()) {
                    item {
                        Text(
                            text = "生成されたCBZ一覧",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    items(succeeded, key = { "ok_${it.id}" }) { item ->
                        val timeSuffix = if (item.durationMillis > 0L) {
                            " · ${FilenameUtils.formatDuration(item.durationMillis)}"
                        } else ""
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "完了",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.outputFileName ?: FilenameUtils.toCbzFileName(item.fileName),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.pageCount}ページ · ${FilenameUtils.formatByteSize(item.outputSizeBytes)}$timeSuffix · 直接抽出 ${item.extractedDirectPages}P / 描画 ${item.renderedPages}P",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
