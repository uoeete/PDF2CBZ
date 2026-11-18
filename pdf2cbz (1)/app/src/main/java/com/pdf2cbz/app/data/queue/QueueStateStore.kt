package com.pdf2cbz.app.data.queue

import android.content.Context
import com.pdf2cbz.app.data.model.ConversionHistoryEntry
import com.pdf2cbz.app.data.model.PdfQueueItem
import com.pdf2cbz.app.data.model.QueueItemStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class QueueStateStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val queueFile: File
        get() = File(context.filesDir, "pdf2cbz_queue.json")

    private val historyFile: File
        get() = File(context.filesDir, "pdf2cbz_history.json")

    @Synchronized
    fun loadQueue(): List<PdfQueueItem> {
        if (!queueFile.exists()) return emptyList()
        return runCatching {
            val raw = queueFile.readText(Charsets.UTF_8)
            val loaded = json.decodeFromString<List<PdfQueueItem>>(raw)
            // If the app was killed mid-item, recover PROCESSING items back to PENDING so they can resume cleanly.
            loaded.map { item ->
                if (item.status == QueueItemStatus.PROCESSING) {
                    item.copy(status = QueueItemStatus.PENDING, errorMessage = null)
                } else {
                    item
                }
            }
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun saveQueue(items: List<PdfQueueItem>) {
        writeAtomic(queueFile, json.encodeToString(items))
    }

    @Synchronized
    fun loadHistory(): List<ConversionHistoryEntry> {
        if (!historyFile.exists()) return emptyList()
        return runCatching {
            val raw = historyFile.readText(Charsets.UTF_8)
            json.decodeFromString<List<ConversionHistoryEntry>>(raw)
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun saveHistory(entries: List<ConversionHistoryEntry>) {
        writeAtomic(historyFile, json.encodeToString(entries.take(200)))
    }

    private fun writeAtomic(target: File, content: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content, Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.writeText(content, Charsets.UTF_8)
            temp.delete()
        }
    }
}
