package com.pdf2cbz.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ImageFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String
) {
    JPEG("JPEG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WebP", "webp", "image/webp")
}

@Serializable
enum class DpiOption(val dpi: Int, val label: String) {
    DPI_72(72, "72 DPI（軽量・低解像度）"),
    DPI_96(96, "96 DPI（標準画面向け）"),
    DPI_150(150, "150 DPI（推奨・バランス）"),
    DPI_200(200, "200 DPI（高画質漫画向け）"),
    DPI_300(300, "300 DPI（印刷品質）"),
    DPI_400(400, "400 DPI（超高精細）"),
    DPI_600(600, "600 DPI（最高解像度）");

    companion object {
        fun fromDpi(value: Int): DpiOption =
            entries.firstOrNull { it.dpi == value } ?: DPI_150
    }
}

@Serializable
enum class ThemeMode(val label: String) {
    SYSTEM("端末の設定に従う"),
    LIGHT("ライトモード"),
    DARK("ダークモード")
}

@Serializable
enum class DuplicatePolicy(val label: String, val description: String) {
    RENAME("連番を付けて別名保存", "例: manga01 (1).cbz"),
    OVERWRITE("既存のCBZを上書き", "同名ファイルがある場合は置き換えます"),
    SKIP("変換をスキップ", "同名ファイルがある場合は処理しません")
}

@Serializable
enum class SourcePdfAction(val label: String, val description: String) {
    KEEP("削除しない（初期設定）", "変換後も元のPDFファイルを保持します"),
    DELETE_AFTER_SUCCESS("変換成功後に元PDFを削除", "CBZの保存が完了したPDFのみ削除します")
}

@Serializable
data class AppSettings(
    val imageFormat: ImageFormat = ImageFormat.JPEG,
    val dpi: Int = 150,
    val jpegQuality: Int = 90,
    val preferDirectImageExtraction: Boolean = true,
    val includeComicInfoXml: Boolean = false,
    val outputTreeUri: String? = null,
    val outputTreeDisplayName: String = "未選択",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.RENAME,
    val sourcePdfAction: SourcePdfAction = SourcePdfAction.KEEP,
    val autoDeleteTempFiles: Boolean = true
)

@Serializable
enum class QueueItemStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    SKIPPED,
    CANCELLED
}

@Serializable
data class PdfQueueItem(
    val id: String,
    val uriString: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val status: QueueItemStatus = QueueItemStatus.PENDING,
    val errorMessage: String? = null,
    val outputFileName: String? = null,
    val outputUriString: String? = null,
    val outputSizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val extractedDirectPages: Int = 0,
    val renderedPages: Int = 0
)

@Serializable
data class ConversionProgress(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isCancelled: Boolean = false,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val completedCount: Int = 0,
    val remainingCount: Int = 0,
    val errorCount: Int = 0,
    val lastModeNote: String = ""
)

@Serializable
data class ConversionHistoryEntry(
    val id: String,
    val sourceFileName: String,
    val outputFileName: String,
    val outputUriString: String? = null,
    val outputFolderName: String,
    val pageCount: Int,
    val outputSizeBytes: Long,
    val imageFormat: ImageFormat,
    val dpi: Int,
    val timestampMillis: Long,
    val success: Boolean,
    val errorMessage: String? = null
)
