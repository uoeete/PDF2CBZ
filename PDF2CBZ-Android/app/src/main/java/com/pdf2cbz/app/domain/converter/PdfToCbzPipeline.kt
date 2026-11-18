package com.pdf2cbz.app.domain.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.pdf2cbz.app.data.model.DuplicatePolicy
import com.pdf2cbz.app.data.model.ImageFormat
import com.pdf2cbz.app.domain.util.FilenameUtils
import com.pdf2cbz.app.domain.util.StorageSpaceChecker
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Single-book, single-page streaming PDF -> CBZ conversion pipeline.
 * Never loads all pages into memory simultaneously and cleans up bitmaps & temp files deterministically.
 */
class PdfToCbzPipeline(
    private val context: Context,
    private val storageSpaceChecker: StorageSpaceChecker
) {

    companion object {
        // Caps single-axis pixel size to prevent Canvas/Bitmap OOM on unusually huge PDF media boxes (e.g. posters at 600 DPI)
        private const val MAX_BITMAP_DIMENSION_PX = 4500
    }

    fun convertSinglePdf(
        sourceUri: Uri,
        sourceFileName: String,
        sourceSizeBytes: Long,
        outputTarget: CbzOutputTarget,
        config: PdfConversionConfig,
        isCancelled: () -> Boolean,
        awaitIfPaused: suspend () -> Unit,
        onPageProgress: (currentPage: Int, totalPages: Int, mode: PageMode) -> Unit
    ): BookConversionResult {
        throwIfCancelled(isCancelled)

        val desiredCbzName = FilenameUtils.toCbzFileName(sourceFileName)
        val destination = outputTarget.resolveDestination(desiredCbzName, config.duplicatePolicy)
        if (destination is ResolvedDestination.SkipExisting) {
            return BookConversionResult(
                outputFileName = destination.existingFileName,
                outputUriString = null,
                outputSizeBytes = 0L,
                pageCount = 0,
                directExtractedPages = 0,
                renderedPages = 0,
                skipped = true
            )
        }
        val writeDest = destination as ResolvedDestination.WriteTo

        val tempRoot = File(context.cacheDir, "pdf2cbz_work_${System.nanoTime()}")
        if (!tempRoot.mkdirs() && !tempRoot.exists()) {
            throw PdfConversionException.InsufficientStorage()
        }

        val estimatedRequiredBytes = max(sourceSizeBytes * 2L, 16L * 1024L * 1024L)
        if (!storageSpaceChecker.hasEnoughSpace(tempRoot, estimatedRequiredBytes)) {
            tempRoot.deleteRecursively()
            throw PdfConversionException.InsufficientStorage()
        }

        // Copy source PDF into a seekable local temp file only if ParcelFileDescriptor cannot be opened directly,
        // or use ParcelFileDescriptor directly from ContentResolver (zero duplicate disk copy when seekable!).
        var pfd: ParcelFileDescriptor? = null
        var tempPdfCopy: File? = null
        var pdfRenderer: PdfRenderer? = null
        var pdDocument: PDDocument? = null
        var directExtractor: PdfBoxDirectImageExtractor? = null

        try {
            pfd = openSeekablePdfDescriptor(sourceUri, tempRoot)?.also {
                if (it.second != null) tempPdfCopy = it.second
            }?.first ?: throw PdfConversionException.CannotOpen("ファイル記述子を取得できませんでした。")

            // Open PDFBox document first when direct extraction is enabled or to detect encryption cleanly
            if (config.preferDirectImageExtraction) {
                try {
                    context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                        pdDocument = PDDocument.load(stream)
                        if (pdDocument?.isEncrypted == true) {
                            throw PdfConversionException.PasswordProtected()
                        }
                        directExtractor = pdDocument?.let { PdfBoxDirectImageExtractor(it) }
                    }
                } catch (e: InvalidPasswordException) {
                    throw PdfConversionException.PasswordProtected(e)
                } catch (e: PdfConversionException) {
                    throw e
                } catch (_: Throwable) {
                    // If PDFBox fails to parse, we still allow PdfRenderer to try rendering the PDF
                    pdDocument?.close()
                    pdDocument = null
                    directExtractor = null
                }
            }

            try {
                pdfRenderer = PdfRenderer(pfd)
            } catch (e: SecurityException) {
                throw PdfConversionException.PasswordProtected(e)
            } catch (e: IOException) {
                throw PdfConversionException.CorruptedPdf(e)
            } catch (e: Throwable) {
                throw PdfConversionException.CannotOpen(e.localizedMessage, e)
            }

            val pageCount = pdfRenderer.pageCount
            if (pageCount <= 0) {
                throw PdfConversionException.CorruptedPdf()
            }

            val pageFiles = ArrayList<File>(pageCount)
            var directCount = 0
            var renderedCount = 0

            val pageImagesDir = File(tempRoot, "pages").apply { mkdirs() }

            for (pageIndex in 0 until pageCount) {
                throwIfCancelled(isCancelled)
                // Pause checkpoint (blocks safely inside coroutine via runBlocking bridge in worker or suspend)
                kotlinx.coroutines.runBlocking { awaitIfPaused() }
                throwIfCancelled(isCancelled)

                // Periodic storage space safety check every 10 pages
                if (pageIndex % 10 == 0 && !storageSpaceChecker.hasEnoughSpace(pageImagesDir, 8L * 1024L * 1024L)) {
                    throw PdfConversionException.InsufficientStorage()
                }

                val pageFileName = FilenameUtils.formatPageFileName(
                    zeroBasedPageIndex = pageIndex,
                    totalPages = pageCount,
                    extension = config.imageFormat.extension
                )
                val targetImageFile = File(pageImagesDir, pageFileName)

                val extracted = directExtractor?.tryExtractPageImage(pageIndex, targetImageFile, config) == true
                val mode = if (extracted) {
                    directCount++
                    PageMode.DIRECT_EXTRACTED
                } else {
                    renderPageWithPdfRenderer(
                        renderer = pdfRenderer,
                        pageIndex = pageIndex,
                        targetImageFile = targetImageFile,
                        config = config
                    )
                    renderedCount++
                    PageMode.PDF_RENDERED
                }

                pageFiles.add(targetImageFile)
                onPageProgress(pageIndex + 1, pageCount, mode)
            }

            throwIfCancelled(isCancelled)

            try {
                outputTarget.openOutputStream(writeDest).use { outputStream ->
                    CbzArchiveWriter.writeCbz(
                        pageFiles = pageFiles,
                        outputStream = outputStream,
                        comicTitle = writeDest.finalFileName,
                        includeComicInfoXml = config.includeComicInfoXml,
                        checkCancelled = { throwIfCancelled(isCancelled) }
                    )
                }
            } catch (e: PdfConversionException) {
                outputTarget.deleteIfBroken(writeDest)
                throw e
            } catch (e: IOException) {
                outputTarget.deleteIfBroken(writeDest)
                if (e.message?.contains("ENOSPC", ignoreCase = true) == true) {
                    throw PdfConversionException.InsufficientStorage(e)
                }
                throw PdfConversionException.OutputUnavailable(e.localizedMessage, e)
            } catch (e: Throwable) {
                outputTarget.deleteIfBroken(writeDest)
                throw PdfConversionException.OutputUnavailable(e.localizedMessage, e)
            }

            val outputSize = outputTarget.querySize(writeDest)
            return BookConversionResult(
                outputFileName = writeDest.finalFileName,
                outputUriString = writeDest.uriString,
                outputSizeBytes = outputSize,
                pageCount = pageCount,
                directExtractedPages = directCount,
                renderedPages = renderedCount,
                skipped = false
            )
        } finally {
            runCatching { pdfRenderer?.close() }
            runCatching { pdDocument?.close() }
            runCatching { pfd?.close() }
            runCatching { tempPdfCopy?.delete() }
            if (config.autoDeleteTempFiles) {
                runCatching { tempRoot.deleteRecursively() }
            }
        }
    }

    private fun openSeekablePdfDescriptor(uri: Uri, tempRoot: File): Pair<ParcelFileDescriptor, File?>? {
        val directPfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (directPfd != null && directPfd.statSize > 0) {
            return directPfd to null
        }
        directPfd?.close()

        // Fallback for non-seekable SAF providers: stream into temp file first
        val copyFile = File(tempRoot, "source_copy.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(copyFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        } ?: return null

        val filePfd = ParcelFileDescriptor.open(copyFile, ParcelFileDescriptor.MODE_READ_ONLY)
        return filePfd to copyFile
    }

    private fun renderPageWithPdfRenderer(
        renderer: PdfRenderer,
        pageIndex: Int,
        targetImageFile: File,
        config: PdfConversionConfig
    ) {
        val pageNumber = pageIndex + 1
        try {
            renderer.openPage(pageIndex).use { page ->
                val rawScale = config.dpi.toFloat() / 72f
                val desiredW = max(1, (page.width * rawScale).roundToInt())
                val desiredH = max(1, (page.height * rawScale).roundToInt())

                val maxDim = max(desiredW, desiredH)
                val capFactor = if (maxDim > MAX_BITMAP_DIMENSION_PX) {
                    MAX_BITMAP_DIMENSION_PX.toFloat() / maxDim.toFloat()
                } else {
                    1f
                }

                val targetW = max(1, (desiredW * capFactor).roundToInt())
                val targetH = max(1, (desiredH * capFactor).roundToInt())

                renderAtDimensionsWithOomFallback(page, pageNumber, targetW, targetH, targetImageFile, config)
            }
        } catch (e: PdfConversionException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw PdfConversionException.OutOfMemory(pageNumber, e)
        } catch (e: Throwable) {
            throw PdfConversionException.PageRenderFailed(pageNumber, e)
        }
    }

    private fun renderAtDimensionsWithOomFallback(
        page: PdfRenderer.Page,
        pageNumber: Int,
        width: Int,
        height: Int,
        targetImageFile: File,
        config: PdfConversionConfig
    ) {
        val scales = floatArrayOf(1.0f, 0.75f, 0.5f)
        var lastOom: OutOfMemoryError? = null

        for (scale in scales) {
            val w = max(1, (width * scale).roundToInt())
            val h = max(1, (height * scale).roundToInt())
            var bitmap: Bitmap? = null
            try {
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                FileOutputStream(targetImageFile).use { out ->
                    val ok = compressBitmap(bitmap, config, out)
                    if (!ok) {
                        throw PdfConversionException.PageRenderFailed(pageNumber)
                    }
                }
                return
            } catch (oom: OutOfMemoryError) {
                lastOom = oom
                targetImageFile.delete()
                System.gc()
            } finally {
                if (bitmap != null && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
        throw PdfConversionException.OutOfMemory(pageNumber, lastOom)
    }

    @Suppress("DEPRECATION")
    private fun compressBitmap(
        bitmap: Bitmap,
        config: PdfConversionConfig,
        out: FileOutputStream
    ): Boolean {
        val format = when (config.imageFormat) {
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
        }
        val quality = when (config.imageFormat) {
            ImageFormat.PNG -> 100
            ImageFormat.JPEG, ImageFormat.WEBP -> config.jpegQuality.coerceIn(1, 100)
        }
        return bitmap.compress(format, quality, out)
    }

    private fun throwIfCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) {
            throw PdfConversionException.Cancelled()
        }
    }
}
