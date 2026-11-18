package com.pdf2cbz.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdf2cbz.app.Pdf2CbzApplication
import com.pdf2cbz.app.data.model.AppSettings
import com.pdf2cbz.app.data.model.ConversionHistoryEntry
import com.pdf2cbz.app.data.model.ConversionProgress
import com.pdf2cbz.app.data.model.DuplicatePolicy
import com.pdf2cbz.app.data.model.ImageFormat
import com.pdf2cbz.app.data.model.PdfQueueItem
import com.pdf2cbz.app.data.model.SourcePdfAction
import com.pdf2cbz.app.data.model.ThemeMode
import com.pdf2cbz.app.domain.converter.SafMetadataHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = (application as Pdf2CbzApplication).conversionController
    private val settingsRepo = controller.settingsRepository

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val queue: StateFlow<List<PdfQueueItem>> = controller.queue
    val progress: StateFlow<ConversionProgress> = controller.progress
    val history: StateFlow<List<ConversionHistoryEntry>> = controller.history

    fun onPdfFilesPicked(uris: List<Uri>) {
        controller.addPdfUris(uris)
    }

    fun onOutputFolderPicked(uri: Uri) {
        val context = getApplication<Application>()
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
        val displayName = SafMetadataHelper.getTreeDisplayName(context, uri)
        viewModelScope.launch {
            settingsRepo.updateOutputFolder(uri.toString(), displayName)
        }
    }

    fun removeQueueItem(id: String) = controller.removeItem(id)

    fun clearAllQueue() = controller.clearPendingOrAll(onlyCompletedAndFailed = false)

    fun clearFinishedQueueItems() = controller.clearPendingOrAll(onlyCompletedAndFailed = true)

    fun startConversion() = controller.startBatchConversion()

    fun pauseConversion() = controller.pauseConversion()

    fun resumeConversion() = controller.resumeConversion()

    fun cancelConversion() = controller.cancelConversion()

    fun retryFailed() = controller.retryFailedItems()

    fun clearHistory() = controller.clearHistory()

    fun setImageFormat(format: ImageFormat) = viewModelScope.launch {
        settingsRepo.updateImageFormat(format)
    }

    fun setDpi(dpi: Int) = viewModelScope.launch {
        settingsRepo.updateDpi(dpi)
    }

    fun setJpegQuality(quality: Int) = viewModelScope.launch {
        settingsRepo.updateJpegQuality(quality)
    }

    fun setPreferDirectExtraction(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.updatePreferDirectExtraction(enabled)
    }

    fun setIncludeComicInfo(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.updateIncludeComicInfo(enabled)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsRepo.updateThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.updateDynamicColor(enabled)
    }

    fun setDuplicatePolicy(policy: DuplicatePolicy) = viewModelScope.launch {
        settingsRepo.updateDuplicatePolicy(policy)
    }

    fun setSourcePdfAction(action: SourcePdfAction) = viewModelScope.launch {
        settingsRepo.updateSourcePdfAction(action)
    }

    fun setAutoDeleteTemp(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.updateAutoDeleteTemp(enabled)
    }
}
