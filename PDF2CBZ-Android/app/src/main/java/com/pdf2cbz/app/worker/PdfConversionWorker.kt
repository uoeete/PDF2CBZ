package com.pdf2cbz.app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.pdf2cbz.app.MainActivity
import com.pdf2cbz.app.Pdf2CbzApplication
import com.pdf2cbz.app.R
import com.pdf2cbz.app.data.model.ConversionHistoryEntry
import com.pdf2cbz.app.data.model.ConversionProgress
import com.pdf2cbz.app.data.model.QueueItemStatus
import com.pdf2cbz.app.data.model.SourcePdfAction
import com.pdf2cbz.app.domain.converter.AndroidSafOutputTarget
import com.pdf2cbz.app.domain.converter.PageMode
import com.pdf2cbz.app.domain.converter.PdfConversionConfig
import com.pdf2cbz.app.domain.converter.PdfConversionException
import com.pdf2cbz.app.domain.converter.PdfToCbzPipeline
import com.pdf2cbz.app.domain.util.AndroidStorageSpaceChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class PdfConversionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val CHANNEL_ID = "pdf2cbz_conversion_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as Pdf2CbzApplication
        val controller = app.conversionController
        val settings = controller.getCurrentSettings()

        val outputTreeUriString = settings.outputTreeUri
        if (outputTreeUriString.isNullOrBlank()) {
            val pending = controller.queue.value.filter { it.status == QueueItemStatus.PENDING }
            pending.forEach { item ->
                controller.updateItem(
                    item.copy(
                        status = QueueItemStatus.FAILED,
                        errorMessage = "保存先フォルダが設定されていません。設定画面から保存先を選択してください。"
                    )
                )
            }
            controller.updateProgress {
                it.copy(isRunning = false, errorCount = pending.size)
            }
            return@withContext Result.failure()
        }

        val outputTreeUri = Uri.parse(outputTreeUriString)
        val pipeline = PdfToCbzPipeline(
            context = applicationContext,
            storageSpaceChecker = AndroidStorageSpaceChecker()
        )
        val config = PdfConversionConfig(
            imageFormat = settings.imageFormat,
            dpi = settings.dpi,
            jpegQuality = settings.jpegQuality,
            preferDirectImageExtraction = settings.preferDirectImageExtraction,
            includeComicInfoXml = settings.includeComicInfoXml,
            duplicatePolicy = settings.duplicatePolicy,
            autoDeleteTempFiles = settings.autoDeleteTempFiles
        )

        ensureNotificationChannel()
        setForeground(createForegroundInfo("変換を開始しています...", 0, 0))

        val pendingItems = controller.queue.value.filter { it.status == QueueItemStatus.PENDING }
        val totalFiles = pendingItems.size
        var completedCount = 0
        var errorCount = 0

        controller.updateProgress {
            ConversionProgress(
                isRunning = true,
                isPaused = false,
                isCancelled = false,
                currentFileIndex = 0,
                totalFiles = totalFiles,
                completedCount = 0,
                remainingCount = totalFiles,
                errorCount = 0
            )
        }

        for ((index, item) in pendingItems.withIndex()) {
            if (controller.isCancelled() || isStopped) {
                markRemainingCancelled(controller, pendingItems.drop(index))
                break
            }

            controller.awaitIfPaused()
            if (controller.isCancelled() || isStopped) {
                markRemainingCancelled(controller, pendingItems.drop(index))
                break
            }

            controller.updateItem(item.copy(status = QueueItemStatus.PROCESSING, errorMessage = null))
            controller.updateProgress {
                it.copy(
                    currentFileIndex = index + 1,
                    totalFiles = totalFiles,
                    currentFileName = item.fileName,
                    currentPage = 0,
                    totalPages = 0,
                    completedCount = completedCount,
                    remainingCount = totalFiles - index,
                    errorCount = errorCount
                )
            }

            setForeground(
                createForegroundInfo(
                    contentText = "${index + 1}/$totalFiles: ${item.fileName}",
                    progress = index,
                    max = totalFiles
                )
            )

            try {
                val outputTarget = AndroidSafOutputTarget(applicationContext, outputTreeUri)
                val result = pipeline.convertSinglePdf(
                    sourceUri = Uri.parse(item.uriString),
                    sourceFileName = item.fileName,
                    sourceSizeBytes = item.fileSizeBytes,
                    outputTarget = outputTarget,
                    config = config,
                    isCancelled = { controller.isCancelled() || isStopped },
                    awaitIfPaused = { controller.awaitIfPaused() },
                    onPageProgress = { currentPage, totalPages, mode ->
                        val note = when (mode) {
                            PageMode.DIRECT_EXTRACTED -> "PDF内画像を直接抽出中"
                            PageMode.PDF_RENDERED -> "PDFページをレンダリング中 (${settings.dpi} DPI)"
                        }
                        controller.updateProgress {
                            it.copy(
                                currentPage = currentPage,
                                totalPages = totalPages,
                                lastModeNote = note
                            )
                        }
                    }
                )

                if (result.skipped) {
                    controller.updateItem(
                        item.copy(
                            status = QueueItemStatus.SKIPPED,
                            outputFileName = result.outputFileName
                        )
                    )
                } else {
                    completedCount++
                    controller.updateItem(
                        item.copy(
                            status = QueueItemStatus.COMPLETED,
                            outputFileName = result.outputFileName,
                            outputUriString = result.outputUriString,
                            outputSizeBytes = result.outputSizeBytes,
                            pageCount = result.pageCount,
                            extractedDirectPages = result.directExtractedPages,
                            renderedPages = result.renderedPages
                        )
                    )
                    controller.appendHistory(
                        ConversionHistoryEntry(
                            id = UUID.randomUUID().toString(),
                            sourceFileName = item.fileName,
                            outputFileName = result.outputFileName,
                            outputUriString = result.outputUriString,
                            outputFolderName = settings.outputTreeDisplayName,
                            pageCount = result.pageCount,
                            outputSizeBytes = result.outputSizeBytes,
                            imageFormat = settings.imageFormat,
                            dpi = settings.dpi,
                            timestampMillis = System.currentTimeMillis(),
                            success = true
                        )
                    )

                    if (settings.sourcePdfAction == SourcePdfAction.DELETE_AFTER_SUCCESS) {
                        runCatching {
                            DocumentFile.fromSingleUri(applicationContext, Uri.parse(item.uriString))?.delete()
                        }
                    }
                }
            } catch (cancelled: PdfConversionException.Cancelled) {
                controller.updateItem(
                    item.copy(
                        status = QueueItemStatus.CANCELLED,
                        errorMessage = cancelled.userMessageJa
                    )
                )
                markRemainingCancelled(controller, pendingItems.drop(index + 1))
                break
            } catch (e: PdfConversionException) {
                errorCount++
                controller.updateItem(
                    item.copy(
                        status = QueueItemStatus.FAILED,
                        errorMessage = e.userMessageJa
                    )
                )
                controller.appendHistory(
                    ConversionHistoryEntry(
                        id = UUID.randomUUID().toString(),
                        sourceFileName = item.fileName,
                        outputFileName = "-",
                        outputFolderName = settings.outputTreeDisplayName,
                        pageCount = 0,
                        outputSizeBytes = 0L,
                        imageFormat = settings.imageFormat,
                        dpi = settings.dpi,
                        timestampMillis = System.currentTimeMillis(),
                        success = false,
                        errorMessage = e.userMessageJa
                    )
                )
            } catch (t: Throwable) {
                errorCount++
                val msg = "変換中に予期しないエラーが発生しました: ${t.localizedMessage ?: "不明なエラー"}"
                controller.updateItem(
                    item.copy(
                        status = QueueItemStatus.FAILED,
                        errorMessage = msg
                    )
                )
                controller.appendHistory(
                    ConversionHistoryEntry(
                        id = UUID.randomUUID().toString(),
                        sourceFileName = item.fileName,
                        outputFileName = "-",
                        outputFolderName = settings.outputTreeDisplayName,
                        pageCount = 0,
                        outputSizeBytes = 0L,
                        imageFormat = settings.imageFormat,
                        dpi = settings.dpi,
                        timestampMillis = System.currentTimeMillis(),
                        success = false,
                        errorMessage = msg
                    )
                )
            }
        }

        val finalRemaining = controller.queue.value.count { it.status == QueueItemStatus.PENDING }
        controller.updateProgress {
            it.copy(
                isRunning = false,
                isPaused = false,
                completedCount = completedCount,
                remainingCount = finalRemaining,
                errorCount = errorCount
            )
        }
        controller.resetCancellationForNextRun()
        Result.success()
    }

    private fun markRemainingCancelled(
        controller: ConversionController,
        remaining: List<PdfQueueItemCompat>
    ) {
        remaining.forEach { item ->
            controller.updateItem(
                item.copy(
                    status = QueueItemStatus.CANCELLED,
                    errorMessage = "変換がキャンセルされました。"
                )
            )
        }
    }

    private fun ensureNotificationChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_conversion),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.notification_channel_conversion_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(contentText: String, progress: Int, max: Int): ForegroundInfo {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(applicationContext.getString(R.string.notification_converting_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(max, progress, max <= 0)
            .setContentIntent(pendingIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}

private typealias PdfQueueItemCompat = com.pdf2cbz.app.data.model.PdfQueueItem
