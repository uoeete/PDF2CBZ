package com.pdf2cbz.app.worker

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pdf2cbz.app.data.model.AppSettings
import com.pdf2cbz.app.data.model.ConversionHistoryEntry
import com.pdf2cbz.app.data.model.ConversionProgress
import com.pdf2cbz.app.data.model.PdfQueueItem
import com.pdf2cbz.app.data.model.QueueItemStatus
import com.pdf2cbz.app.data.queue.QueueStateStore
import com.pdf2cbz.app.data.settings.SettingsRepository
import com.pdf2cbz.app.domain.converter.SafMetadataHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ConversionController(private val appContext: Context) {

    companion object {
        const val UNIQUE_WORK_NAME = "pdf2cbz_batch_conversion"
    }

    val settingsRepository = SettingsRepository(appContext)
    private val queueStateStore = QueueStateStore(appContext)
    private val workManager = WorkManager.getInstance(appContext)

    private val _queue = MutableStateFlow(queueStateStore.loadQueue())
    val queue: StateFlow<List<PdfQueueItem>> = _queue.asStateFlow()

    private val _history = MutableStateFlow(queueStateStore.loadHistory())
    val history: StateFlow<List<ConversionHistoryEntry>> = _history.asStateFlow()

    private val _progress = MutableStateFlow(ConversionProgress())
    val progress: StateFlow<ConversionProgress> = _progress.asStateFlow()

    private val pausedFlag = AtomicBoolean(false)
    private val cancelledFlag = AtomicBoolean(false)

    fun addPdfUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val existingUris = _queue.value.map { it.uriString }.toHashSet()
        val newItems = uris.mapNotNull { uri ->
            val uriStr = uri.toString()
            if (uriStr in existingUris) return@mapNotNull null

            // Attempt persistable read permission for SAF URIs across WorkManager restarts
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val meta = SafMetadataHelper.queryPdfMetadata(appContext, uri)
            PdfQueueItem(
                id = UUID.randomUUID().toString(),
                uriString = uriStr,
                fileName = meta.fileName,
                fileSizeBytes = meta.sizeBytes,
                status = QueueItemStatus.PENDING
            )
        }

        if (newItems.isNotEmpty()) {
            _queue.update { current ->
                val updated = current + newItems
                queueStateStore.saveQueue(updated)
                updated
            }
        }
    }

    fun removeItem(itemId: String) {
        _queue.update { current ->
            val updated = current.filterNot {
                it.id == itemId && it.status != QueueItemStatus.PROCESSING
            }
            queueStateStore.saveQueue(updated)
            updated
        }
    }

    fun clearPendingOrAll(onlyCompletedAndFailed: Boolean = false) {
        _queue.update { current ->
            val updated = if (onlyCompletedAndFailed) {
                current.filter {
                    it.status == QueueItemStatus.PENDING || it.status == QueueItemStatus.PROCESSING
                }
            } else {
                current.filter { it.status == QueueItemStatus.PROCESSING }
            }
            queueStateStore.saveQueue(updated)
            updated
        }
    }

    fun startBatchConversion() {
        val hasPending = _queue.value.any { it.status == QueueItemStatus.PENDING }
        if (!hasPending) return

        pausedFlag.set(false)
        cancelledFlag.set(false)

        val totalPending = _queue.value.count { it.status == QueueItemStatus.PENDING }
        _progress.value = ConversionProgress(
            isRunning = true,
            isPaused = false,
            isCancelled = false,
            currentFileIndex = 0,
            totalFiles = totalPending,
            remainingCount = totalPending
        )

        val request = OneTimeWorkRequestBuilder<PdfConversionWorker>().build()
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun retryFailedItems() {
        val hasFailedOrCancelled = _queue.value.any {
            it.status == QueueItemStatus.FAILED || it.status == QueueItemStatus.CANCELLED
        }
        if (!hasFailedOrCancelled) return

        _queue.update { current ->
            val updated = current.map { item ->
                if (item.status == QueueItemStatus.FAILED || item.status == QueueItemStatus.CANCELLED) {
                    item.copy(status = QueueItemStatus.PENDING, errorMessage = null)
                } else {
                    item
                }
            }
            queueStateStore.saveQueue(updated)
            updated
        }
        startBatchConversion()
    }

    fun pauseConversion() {
        pausedFlag.set(true)
        _progress.update { it.copy(isPaused = true) }
    }

    fun resumeConversion() {
        pausedFlag.set(false)
        _progress.update { it.copy(isPaused = false) }
    }

    fun cancelConversion() {
        cancelledFlag.set(true)
        pausedFlag.set(false)
        _progress.update { it.copy(isCancelled = true, isPaused = false) }
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun isCancelled(): Boolean = cancelledFlag.get()

    suspend fun awaitIfPaused() {
        while (pausedFlag.get() && !cancelledFlag.get()) {
            delay(200)
        }
    }

    internal suspend fun getCurrentSettings(): AppSettings {
        return settingsRepository.settingsFlow.first()
    }

    internal fun updateItem(updatedItem: PdfQueueItem) {
        _queue.update { current ->
            val updated = current.map { if (it.id == updatedItem.id) updatedItem else it }
            queueStateStore.saveQueue(updated)
            updated
        }
    }

    internal fun updateProgress(transform: (ConversionProgress) -> ConversionProgress) {
        _progress.update(transform)
    }

    internal fun appendHistory(entry: ConversionHistoryEntry) {
        _history.update { current ->
            val updated = listOf(entry) + current
            queueStateStore.saveHistory(updated)
            updated
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
        queueStateStore.saveHistory(emptyList())
    }

    fun resetCancellationForNextRun() {
        pausedFlag.set(false)
        cancelledFlag.set(false)
    }
}
