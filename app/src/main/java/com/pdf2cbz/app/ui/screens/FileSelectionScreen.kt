package com.pdf2cbz.app.ui.screens

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdf2cbz.app.data.model.AppSettings
import com.pdf2cbz.app.data.model.PdfQueueItem
import com.pdf2cbz.app.data.model.QueueItemStatus
import com.pdf2cbz.app.domain.util.FilenameUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSelectionScreen(
    queue: List<PdfQueueItem>,
    settings: AppSettings,
    onBack: () -> Unit,
    onAddMorePdfs: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearFinished: () -> Unit = {},
    onClearAll: () -> Unit,
    onStartConversion: () -> Unit
) {
    val pendingCount = queue.count { it.status == QueueItemStatus.PENDING }
    val finishedCount = queue.count {
        it.status == QueueItemStatus.COMPLETED ||
            it.status == QueueItemStatus.FAILED ||
            it.status == QueueItemStatus.SKIPPED ||
            it.status == QueueItemStatus.CANCELLED
    }
    val removableIds = remember(queue) {
        queue.filter { it.status != QueueItemStatus.PROCESSING }.map { it.id }.toSet()
    }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(removableIds) {
        selectedIds = selectedIds.intersect(removableIds)
    }

    val hasOutputFolder = !settings.outputTreeUri.isNullOrBlank()
    val totalSize = queue.sumOf { it.fileSizeBytes }
    val allRemovableSelected = removableIds.isNotEmpty() && selectedIds.size == removableIds.size

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("すべてのPDFを解除しますか？") },
            text = {
                Text("待機リストから${removableIds.size}件のファイルを解除します。元のPDFファイルは端末から削除されません。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        selectedIds = emptySet()
                        onClearAll()
                        scope.launch {
                            snackbarHostState.showSnackbar("選択リストをすべて解除しました")
                        }
                    },
                    modifier = Modifier.testTag("confirm_clear_all_button")
                ) {
                    Text("すべて解除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("選択済みPDF一覧", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${queue.size}ファイル · 合計 ${FilenameUtils.formatByteSize(totalSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("files_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "ホームに戻る"
                        )
                    }
                },
                actions = {
                    if (removableIds.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("clear_all_queue_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("全解除")
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onAddMorePdfs,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("add_more_pdfs_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("PDFを追加")
                    }
                    Button(
                        onClick = onStartConversion,
                        enabled = pendingCount > 0 && hasOutputFolder,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("files_start_conversion_button")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("変換開始 ($pendingCount)")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (queue.isEmpty()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(Modifier.size(14.dp))
                    Text(
                        text = "PDFが選択されていません",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "下の「PDFを追加」から複数の漫画PDFを一括選択できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!hasOutputFolder) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "保存先フォルダが未選択です。ホーム画面または設定画面から出力先フォルダを指定してください。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    if (removableIds.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(
                                        onClick = {
                                            selectedIds = if (allRemovableSelected) {
                                                emptySet()
                                            } else {
                                                removableIds
                                            }
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("toggle_select_all_button")
                                    ) {
                                        Icon(
                                            imageVector = if (allRemovableSelected) {
                                                Icons.Rounded.CheckBox
                                            } else {
                                                Icons.Rounded.CheckBoxOutlineBlank
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (allRemovableSelected) "全選択を解除" else "すべて選択")
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        AnimatedVisibility(
                                            visible = finishedCount > 0 && pendingCount > 0 && selectedIds.isEmpty(),
                                            enter = fadeIn(tween(150)),
                                            exit = fadeOut(tween(150))
                                        ) {
                                            TextButton(
                                                onClick = onClearFinished,
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("clear_finished_queue_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DoneAll,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("済を整理 ($finishedCount)")
                                            }
                                        }

                                        AnimatedVisibility(
                                            visible = selectedIds.isNotEmpty(),
                                            enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                                            exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                                        ) {
                                            FilledTonalButton(
                                                onClick = {
                                                    val targets = selectedIds.toList()
                                                    selectedIds = emptySet()
                                                    targets.forEach(onRemoveItem)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("${targets.size}件のファイルを一覧から削除しました")
                                                    }
                                                },
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("remove_selected_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("選択削除 (${selectedIds.size})")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(queue, key = { it.id }) { item ->
                        val isRemovable = item.status != QueueItemStatus.PROCESSING
                        val isChecked = item.id in selectedIds

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .then(
                                    if (isRemovable) {
                                        Modifier.clickable {
                                            selectedIds = if (isChecked) selectedIds - item.id else selectedIds + item.id
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .testTag("queue_item_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    enabled = isRemovable,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                                    }
                                )
                                Icon(
                                    imageVector = Icons.Rounded.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = item.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        StatusBadge(item.status)
                                        Text(
                                            text = "${FilenameUtils.formatByteSize(item.fileSizeBytes)} → ${FilenameUtils.toCbzFileName(item.fileName)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (!item.errorMessage.isNullOrBlank()) {
                                        Text(
                                            text = item.errorMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (isRemovable) {
                                    IconButton(
                                        onClick = {
                                            selectedIds = selectedIds - item.id
                                            onRemoveItem(item.id)
                                        },
                                        modifier = Modifier.testTag("remove_queue_item_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "${item.fileName} をリストから削除",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun StatusBadge(status: QueueItemStatus) {
    val (bg, fg) = when (status) {
        QueueItemStatus.PENDING -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        QueueItemStatus.PROCESSING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        QueueItemStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        QueueItemStatus.FAILED, QueueItemStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        QueueItemStatus.SKIPPED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun statusLabel(status: QueueItemStatus): String = when (status) {
    QueueItemStatus.PENDING -> "待機中"
    QueueItemStatus.PROCESSING -> "変換中"
    QueueItemStatus.COMPLETED -> "完了"
    QueueItemStatus.FAILED -> "失敗"
    QueueItemStatus.SKIPPED -> "スキップ"
    QueueItemStatus.CANCELLED -> "キャンセル"
}
