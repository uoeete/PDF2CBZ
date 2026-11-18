package com.pdf2cbz.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdf2cbz.app.data.model.ConversionProgress
import com.pdf2cbz.app.domain.util.FilenameUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionScreen(
    progress: ConversionProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onFinishedNavigate: () -> Unit
) {
    LaunchedEffect(progress.isRunning, progress.totalFiles) {
        if (!progress.isRunning && progress.totalFiles > 0) {
            onFinishedNavigate()
        }
    }

    val overallFraction = if (progress.totalFiles > 0) {
        val currentBookFraction = if (progress.totalPages > 0) {
            progress.currentPage.toFloat() / progress.totalPages.toFloat()
        } else 0f
        ((progress.completedCount + progress.errorCount + currentBookFraction) / progress.totalFiles.toFloat())
            .coerceIn(0f, 1f)
    } else 0f

    val pageFraction = if (progress.totalPages > 0) {
        (progress.currentPage.toFloat() / progress.totalPages.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedOverall by animateFloatAsState(targetValue = overallFraction, label = "overall")
    val animatedPage by animateFloatAsState(targetValue = pageFraction, label = "page")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (progress.isPaused) "変換を一時停止中" else "CBZへ変換中",
                        style = MaterialTheme.typography.titleLarge
                    )
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
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "現在処理中のPDF",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = progress.currentFileName.ifBlank { "準備中..." },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (progress.lastModeNote.isNotBlank()) {
                            val speedSuffix = if (progress.pagesPerSecond > 0.1f) {
                                String.format(Locale.US, " · %.1f ページ/秒", progress.pagesPerSecond)
                            } else {
                                ""
                            }
                            Text(
                                text = "${progress.lastModeNote}$speedSuffix",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ページ進捗", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${progress.currentPage} / ${progress.totalPages} ページ",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            LinearProgressIndicator(
                                progress = { animatedPage },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("全体の進捗", style = MaterialTheme.typography.bodySmall)
                                val elapsedText = if (progress.elapsedMillis > 0L) {
                                    " · ${FilenameUtils.formatDuration(progress.elapsedMillis)}"
                                } else ""
                                Text(
                                    text = "${(animatedOverall * 100).toInt()}% (${progress.currentFileIndex}/${progress.totalFiles}冊)$elapsedText",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            LinearProgressIndicator(
                                progress = { animatedOverall },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        label = "完了",
                        value = "${progress.completedCount}冊",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "残り",
                        value = "${progress.remainingCount}冊",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "エラー",
                        value = "${progress.errorCount}件",
                        isError = progress.errorCount > 0,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (progress.isPaused) {
                        Button(
                            onClick = onResume,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("resume_conversion_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("再開")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onPause,
                            enabled = progress.isRunning,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pause_conversion_button")
                        ) {
                            Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("一時停止")
                        }
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        enabled = progress.isRunning,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_conversion_button")
                    ) {
                        Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("キャンセル")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
