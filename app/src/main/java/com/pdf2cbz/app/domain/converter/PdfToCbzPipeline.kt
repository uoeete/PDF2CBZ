package com.pdf2cbz.app.domain.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.pdf2cbz.app.data.model.ImageFormat
import com.pdf2cbz.app.domain.util.FilenameUtils
import com.pdf2cbz.app.domain.util.StorageSpaceChecker
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-speed parallel-pipelined, single-book streaming PDF -> CBZ conversion engine.
 *
 * Key optimizations:
 * 1. Bounded MemoryUsageSetting for PDFBox (skips loading PDDocument completely if direct extraction is off).
 * 2. Zero-copy raw JPEG stream extraction (no decode/re-encode) with strict CTM/CropBox/ColorSpace checks.
 * 3. Multi-worker native PdfRenderer pool + shared byte-budgeted BitmapPool (eliminates GBs of GC churn).
 * 4. Direct in-memory encode -> ordered streaming ZipEntry.STORED CBZ output (zero per-page temp files, zero wasted Deflate CPU).
 * 5. Deadlock-free ordered in-flight windowing so peak RAM remains strictly bounded even on 500-page volumes.
 */
class PdfToCbzPipeline(
    private val context: Context,
    private val storageSpaceChecker: StorageSpaceChecker
) {

    companion object {
        private const val MAX_BITMAP_DIMENSION_PX = 4500
        private const val SMALL_PDF_HEAP_THRESHOLD_BYTES = 24L * 1024L * 1024L
        private const val PDFBOX_MIXED_MEMORY_BUDGET_BYTES = 16L * 1024L * 1024L
    }

    private class WorkerRenderContext(
        val pfd: ParcelFileDescriptor,
        val renderer: PdfRenderer
    ) {
        val baos = ByteArrayOutputStream(128 * 1024)

        fun closeQuietly() {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
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
        val startTimeMs = System.currentTimeMillis()
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
                durationMillis = 0L,
                skipped = true
            )
        }
        val writeDest = destination as ResolvedDestination.WriteTo

        val tempRoot = File(context.cacheDir, "pdf2cbz_work_${System.nanoTime()}")
        if (!tempRoot.mkdirs() && !tempRoot.exists()) {
            throw PdfConversionException.InsufficientStorage()
        }

        // Streaming CBZ requires far less temp disk than writing 500 intermediate image files
        val estimatedRequiredBytes = max(sourceSizeBytes, 16L * 1024L * 1024L)
        if (!storageSpaceChecker.hasEnoughSpace(tempRoot, estimatedRequiredBytes)) {
            tempRoot.deleteRecursively()
            throw PdfConversionException.InsufficientStorage()
        }

        var primaryPfd: ParcelFileDescriptor? = null
        var tempPdfCopy: File? = null
        var primaryRenderer: PdfRenderer? = null
        var pdDocument: PDDocument? = null
        var directExtractor: PdfBoxDirectImageExtractor? = null
        val workerContexts = mutableListOf<WorkerRenderContext>()
        val bitmapPool = BitmapPool()

        try {
            val descriptorResult = openSeekablePdfDescriptor(sourceUri, tempRoot)
                ?: throw PdfConversionException.CannotOpen("ファイル記述子を取得できませんでした。")
            primaryPfd = descriptorResult.first
            tempPdfCopy = descriptorResult.second

            // Skip loading PDFBox entirely when direct extraction is disabled (PdfRenderer detects encryption natively)
            if (config.preferDirectImageExtraction && config.imageFormat == ImageFormat.JPEG) {
                try {
                    val memorySetting = if (sourceSizeBytes in 1..SMALL_PDF_HEAP_THRESHOLD_BYTES) {
                        MemoryUsageSetting.setupMainMemoryOnly()
                    } else {
                        MemoryUsageSetting.setupMixed(PDFBOX_MIXED_MEMORY_BUDGET_BYTES).setTempDir(tempRoot)
                    }
                    pdDocument = if (tempPdfCopy != null) {
                        PDDocument.load(tempPdfCopy, memorySetting)
                    } else {
                        context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                            PDDocument.load(stream, memorySetting)
                        }
                    }
                    if (pdDocument?.isEncrypted == true) {
                        throw PdfConversionException.PasswordProtected()
                    }
                    directExtractor = pdDocument?.let { PdfBoxDirectImageExtractor(it) }
                } catch (e: InvalidPasswordException) {
                    throw PdfConversionException.PasswordProtected(e)
                } catch (e: PdfConversionException) {
                    throw e
                } catch (_: Throwable) {
                    runCatching { pdDocument?.close() }
                    pdDocument = null
                    directExtractor = null
                }
            }

            try {
                primaryRenderer = PdfRenderer(primaryPfd)
            } catch (e: SecurityException) {
                throw PdfConversionException.PasswordProtected(e)
            } catch (e: IOException) {
                throw PdfConversionException.CorruptedPdf(e)
            } catch (e: Throwable) {
                throw PdfConversionException.CannotOpen(e.localizedMessage, e)
            }

            val pageCount = primaryRenderer.pageCount
            if (pageCount <= 0) {
                throw PdfConversionException.CorruptedPdf()
            }

            val targetWorkerCount = determineWorkerCount(pageCount)
            workerContexts.add(WorkerRenderContext(primaryPfd, primaryRenderer))
            // Ownership transferred to workerContexts[0]
            primaryPfd = null
            primaryRenderer = null

            for (w in 1 until targetWorkerCount) {
                val extraCtx = createDuplicateWorkerContext(
                    sourceUri = sourceUri,
                    primaryPfd = descriptorResult.first,
                    tempPdfCopy = tempPdfCopy
                )
                if (extraCtx != null) {
                    workerContexts.add(extraCtx)
                } else {
                    break
                }
            }

            val actualWorkers = workerContexts.size
            val directCount = AtomicInteger(0)
            val renderedCount = AtomicInteger(0)

            try {
                runBlocking(Dispatchers.IO) {
                    executePipelinedConversion(
                        pageCount = pageCount,
                        workerContexts = workerContexts,
                        directExtractor = directExtractor,
                        bitmapPool = bitmapPool,
                        outputTarget = outputTarget,
                        writeDest = writeDest,
                        config = config,
                        isCancelled = isCancelled,
                        awaitIfPaused = awaitIfPaused,
                        onPageEncoded = { mode ->
                            when (mode) {
                                PageMode.DIRECT_EXTRACTED -> directCount.incrementAndGet()
                                PageMode.PDF_RENDERED -> renderedCount.incrementAndGet()
                            }
                        },
                        onPageProgress = onPageProgress
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
            val elapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
            return BookConversionResult(
                outputFileName = writeDest.finalFileName,
                outputUriString = writeDest.uriString,
                outputSizeBytes = outputSize,
                pageCount = pageCount,
                directExtractedPages = directCount.get(),
                renderedPages = renderedCount.get(),
                durationMillis = elapsedMs,
                skipped = false
            )
        } finally {
            workerContexts.forEach { it.closeQuietly() }
            runCatching { primaryRenderer?.close() }
            runCatching { pdDocument?.close() }
            runCatching { primaryPfd?.close() }
            bitmapPool.clear()
            runCatching { tempPdfCopy?.delete() }
            if (config.autoDeleteTempFiles) {
                runCatching { tempRoot.deleteRecursively() }
            }
        }
    }

    private suspend fun executePipelinedConversion(
        pageCount: Int,
        workerContexts: List<WorkerRenderContext>,
        directExtractor: PdfBoxDirectImageExtractor?,
        bitmapPool: BitmapPool,
        outputTarget: CbzOutputTarget,
        writeDest: ResolvedDestination.WriteTo,
        config: PdfConversionConfig,
        isCancelled: () -> Boolean,
        awaitIfPaused: suspend () -> Unit,
        onPageEncoded: (PageMode) -> Unit,
        onPageProgress: (currentPage: Int, totalPages: Int, mode: PageMode) -> Unit
    ) = coroutineScope {
        val workerCount = workerContexts.size
        val windowSize = (workerCount * 2).coerceAtLeast(2)
        val inFlightPermits = Semaphore(windowSize)
        val availableContexts = Channel<WorkerRenderContext>(Channel.UNLIMITED).apply {
            workerContexts.forEach { trySend(it) }
        }
        val completedPages = ConcurrentHashMap<Int, EncodedPage>(windowSize * 2)
        val pageReadySignal = Channel<Unit>(Channel.CONFLATED)
        val extractorLock = Any()
        val firstFailure = AtomicReference<Throwable?>(null)

        fun recordFailure(t: Throwable) {
            if (t !is CancellationException) {
                firstFailure.compareAndSet(null, t)
            }
        }

        // Dedicated ordered ZIP writer (strictly sequential 0 until pageCount)
        val writerJob: Job = launch(Dispatchers.IO) {
            outputTarget.openOutputStream(writeDest).use { outputStream ->
                CbzArchiveWriter.StreamingCbzSession(
                    outputStream = outputStream,
                    comicTitle = writeDest.finalFileName,
                    includeComicInfoXml = config.includeComicInfoXml
                ).use { session ->
                    for (expectedIndex in 0 until pageCount) {
                        var page = completedPages.remove(expectedIndex)
                        while (page == null) {
                            throwIfCancelled(isCancelled)
                            firstFailure.get()?.let { throw it }
                            pageReadySignal.receive()
                            page = completedPages.remove(expectedIndex)
                        }

                        throwIfCancelled(isCancelled)
                        session.writeStoredPage(page)
                        inFlightPermits.release()
                        onPageProgress(expectedIndex + 1, pageCount, page.mode)
                    }
                    throwIfCancelled(isCancelled)
                    session.finishArchive()
                }
            }
        }

        // Ordered feeder dispatching pages onto worker pool (deadlock-free because inFlightPermits are acquired in page order)
        val feederJob: Job = launch(Dispatchers.Default) {
            try {
                for (pageIndex in 0 until pageCount) {
                    throwIfCancelled(isCancelled)
                    awaitIfPaused()
                    throwIfCancelled(isCancelled)
                    firstFailure.get()?.let { throw it }

                    inFlightPermits.acquire()
                    val ctx = availableContexts.receive()

                    launch(Dispatchers.Default) {
                        try {
                            throwIfCancelled(isCancelled)
                            val encoded = produceEncodedPage(
                                pageIndex = pageIndex,
                                totalPages = pageCount,
                                ctx = ctx,
                                directExtractor = directExtractor,
                                extractorLock = extractorLock,
                                bitmapPool = bitmapPool,
                                config = config
                            )
                            onPageEncoded(encoded.mode)
                            completedPages[pageIndex] = encoded
                            pageReadySignal.trySend(Unit)
                        } catch (t: Throwable) {
                            recordFailure(t)
                            pageReadySignal.trySend(Unit)
                            throw t
                        } finally {
                            availableContexts.trySend(ctx)
                        }
                    }
                }
            } catch (t: Throwable) {
                recordFailure(t)
                pageReadySignal.trySend(Unit)
                throw t
            }
        }

        try {
            feederJob.join()
            writerJob.join()
            firstFailure.get()?.let { throw it }
        } catch (t: Throwable) {
            feederJob.cancel()
            writerJob.cancel()
            pageReadySignal.close()
            throw firstFailure.get() ?: t
        }
    }

    private fun produceEncodedPage(
        pageIndex: Int,
        totalPages: Int,
        ctx: WorkerRenderContext,
        directExtractor: PdfBoxDirectImageExtractor?,
        extractorLock: Any,
        bitmapPool: BitmapPool,
        config: PdfConversionConfig
    ): EncodedPage {
        val pageFileName = FilenameUtils.formatPageFileName(
            zeroBasedPageIndex = pageIndex,
            totalPages = totalPages,
            extension = config.imageFormat.extension
        )

        if (directExtractor != null && config.imageFormat == ImageFormat.JPEG) {
            val rawJpeg = synchronized(extractorLock) {
                directExtractor.tryExtractRawJpegBytes(pageIndex, config)
            }
            if (rawJpeg != null) {
                return EncodedPage(
                    pageIndex = pageIndex,
                    fileName = pageFileName,
                    bytes = rawJpeg,
                    crc32 = CbzArchiveWriter.computeCrc32(rawJpeg),
                    mode = PageMode.DIRECT_EXTRACTED
                )
            }
        }

        val renderedBytes = renderPageToBytesWithPdfRenderer(
            ctx = ctx,
            pageIndex = pageIndex,
            bitmapPool = bitmapPool,
            config = config
        )
        return EncodedPage(
            pageIndex = pageIndex,
            fileName = pageFileName,
            bytes = renderedBytes,
            crc32 = CbzArchiveWriter.computeCrc32(renderedBytes),
            mode = PageMode.PDF_RENDERED
        )
    }

    private fun determineWorkerCount(pageCount: Int): Int {
        if (pageCount <= 1) return 1
        val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val desired = when {
            maxHeapMb < 192L -> 1
            maxHeapMb < 384L -> min(cores, 2)
            else -> cores.coerceIn(2, 3)
        }
        return min(desired, pageCount)
    }

    private fun createDuplicateWorkerContext(
        sourceUri: Uri,
        primaryPfd: ParcelFileDescriptor,
        tempPdfCopy: File?
    ): WorkerRenderContext? {
        return runCatching {
            val pfd = if (tempPdfCopy != null) {
                ParcelFileDescriptor.open(tempPdfCopy, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                context.contentResolver.openFileDescriptor(sourceUri, "r") ?: primaryPfd.dup()
            }
            try {
                val renderer = PdfRenderer(pfd)
                WorkerRenderContext(pfd, renderer)
            } catch (t: Throwable) {
                runCatching { pfd.close() }
                throw t
            }
        }.getOrNull()
    }

    private fun openSeekablePdfDescriptor(uri: Uri, tempRoot: File): Pair<ParcelFileDescriptor, File?>? {
        val directPfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (directPfd != null && directPfd.statSize > 0) {
            return directPfd to null
        }
        directPfd?.close()

        val copyFile = File(tempRoot, "source_copy.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(copyFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        } ?: return null

        val filePfd = ParcelFileDescriptor.open(copyFile, ParcelFileDescriptor.MODE_READ_ONLY)
        return filePfd to copyFile
    }

    private fun renderPageToBytesWithPdfRenderer(
        ctx: WorkerRenderContext,
        pageIndex: Int,
        bitmapPool: BitmapPool,
        config: PdfConversionConfig
    ): ByteArray {
        val pageNumber = pageIndex + 1
        try {
            ctx.renderer.openPage(pageIndex).use { page ->
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

                return renderAtDimensionsWithOomFallback(
                    page = page,
                    pageNumber = pageNumber,
                    width = targetW,
                    height = targetH,
                    bitmapPool = bitmapPool,
                    baos = ctx.baos,
                    config = config
                )
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
        bitmapPool: BitmapPool,
        baos: ByteArrayOutputStream,
        config: PdfConversionConfig
    ): ByteArray {
        val scales = floatArrayOf(1.0f, 0.75f, 0.5f)
        var lastOom: OutOfMemoryError? = null

        for (scale in scales) {
            val w = max(1, (width * scale).roundToInt())
            val h = max(1, (height * scale).roundToInt())
            var bitmap: Bitmap? = null
            try {
                bitmap = bitmapPool.obtain(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                baos.reset()
                val ok = compressBitmap(bitmap, config, baos)
                if (!ok) {
                    throw PdfConversionException.PageRenderFailed(pageNumber)
                }
                return baos.toByteArray()
            } catch (oom: OutOfMemoryError) {
                lastOom = oom
                bitmap?.recycle()
                bitmap = null
                bitmapPool.clear()
                System.gc()
            } finally {
                if (bitmap != null) {
                    bitmapPool.release(bitmap)
                }
            }
        }
        throw PdfConversionException.OutOfMemory(pageNumber, lastOom)
    }

    @Suppress("DEPRECATION")
    private fun compressBitmap(
        bitmap: Bitmap,
        config: PdfConversionConfig,
        out: ByteArrayOutputStream
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
