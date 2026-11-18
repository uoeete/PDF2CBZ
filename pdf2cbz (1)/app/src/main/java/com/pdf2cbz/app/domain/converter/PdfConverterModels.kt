package com.pdf2cbz.app.domain.converter

import com.pdf2cbz.app.data.model.DuplicatePolicy
import com.pdf2cbz.app.data.model.ImageFormat
import java.io.File
import java.io.OutputStream

data class PdfConversionConfig(
    val imageFormat: ImageFormat,
    val dpi: Int,
    val jpegQuality: Int,
    val preferDirectImageExtraction: Boolean,
    val includeComicInfoXml: Boolean,
    val duplicatePolicy: DuplicatePolicy,
    val autoDeleteTempFiles: Boolean
)

data class BookConversionResult(
    val outputFileName: String,
    val outputUriString: String?,
    val outputSizeBytes: Long,
    val pageCount: Int,
    val directExtractedPages: Int,
    val renderedPages: Int,
    val durationMillis: Long = 0L,
    val skipped: Boolean = false
)

enum class PageMode {
    DIRECT_EXTRACTED,
    PDF_RENDERED
}

data class EncodedPage(
    val pageIndex: Int,
    val fileName: String,
    val bytes: ByteArray,
    val crc32: Long,
    val mode: PageMode
)

interface PageOutputWriter {
    fun getPageCount(): Int
    fun renderOrExtractPage(
        pageIndex: Int,
        totalPages: Int,
        targetImageFile: File,
        config: PdfConversionConfig
    ): PageMode
}

interface CbzOutputTarget {
    fun resolveDestination(desiredCbzName: String, policy: DuplicatePolicy): ResolvedDestination
    fun openOutputStream(destination: ResolvedDestination): OutputStream
    fun querySize(destination: ResolvedDestination): Long
    fun deleteIfBroken(destination: ResolvedDestination)
}

sealed class ResolvedDestination {
    data class WriteTo(
        val finalFileName: String,
        val uriString: String?
    ) : ResolvedDestination()

    data class SkipExisting(
        val existingFileName: String
    ) : ResolvedDestination()
}

sealed class PdfConversionException(val userMessageJa: String, cause: Throwable? = null) :
    Exception(userMessageJa, cause) {

    class PasswordProtected(cause: Throwable? = null) : PdfConversionException(
        "パスワードで保護されたPDFのため変換できません。保護を解除してからお試しください。",
        cause
    )

    class CorruptedPdf(cause: Throwable? = null) : PdfConversionException(
        "PDFファイルが破損しているか、形式が不正なため読み込めませんでした。",
        cause
    )

    class CannotOpen(detail: String? = null, cause: Throwable? = null) : PdfConversionException(
        if (detail.isNullOrBlank()) "PDFファイルを開けませんでした。" else "PDFファイルを開けませんでした: $detail",
        cause
    )

    class InsufficientStorage(cause: Throwable? = null) : PdfConversionException(
        "端末のストレージ空き容量が不足しているため変換を中断しました。",
        cause
    )

    class OutputUnavailable(detail: String? = null, cause: Throwable? = null) : PdfConversionException(
        if (detail.isNullOrBlank()) "保存先フォルダへアクセスできません。設定から保存先を再指定してください。"
        else "保存先フォルダへ書き込めませんでした: $detail",
        cause
    )

    class PageRenderFailed(pageNumber: Int, cause: Throwable? = null) : PdfConversionException(
        "${pageNumber}ページ目の画像化に失敗しました。",
        cause
    )

    class OutOfMemory(pageNumber: Int, cause: Throwable? = null) : PdfConversionException(
        "${pageNumber}ページ目の処理中にメモリ不足が発生しました。DPIを下げて再試行してください。",
        cause
    )

    class Cancelled : PdfConversionException("変換がキャンセルされました。")
}
