package com.pdf2cbz.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.pdf2cbz.app.data.model.DuplicatePolicy
import com.pdf2cbz.app.data.model.ImageFormat
import com.pdf2cbz.app.domain.converter.BitmapPool
import com.pdf2cbz.app.domain.converter.CbzArchiveWriter
import com.pdf2cbz.app.domain.converter.CbzOutputTarget
import com.pdf2cbz.app.domain.converter.EncodedPage
import com.pdf2cbz.app.domain.converter.PageMode
import com.pdf2cbz.app.domain.converter.PdfBoxDirectImageExtractor
import com.pdf2cbz.app.domain.converter.PdfConversionConfig
import com.pdf2cbz.app.domain.converter.PdfConversionException
import com.pdf2cbz.app.domain.converter.ResolvedDestination
import com.pdf2cbz.app.domain.util.FilenameUtils
import com.pdf2cbz.app.domain.util.StorageSpaceChecker
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CbzPipelineComprehensiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    private fun createSampleJpegBytes(width: Int = 240, height: Int = 340, color: Int = Color.BLACK): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { this.color = color }
        canvas.drawRect(20f, 20f, width - 20f, height - 20f, paint)
        return ByteArrayOutputStream().use { bos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
            bmp.recycle()
            bos.toByteArray()
        }
    }

    private fun createTestPdfWithImages(file: File, pageCount: Int, width: Int = 240, height: Int = 340) {
        PDDocument().use { doc ->
            for (i in 0 until pageCount) {
                val page = PDPage(PDRectangle(width.toFloat(), height.toFloat()))
                doc.addPage(page)

                val jpegBytes = createSampleJpegBytes(
                    width = width,
                    height = height,
                    color = if (i % 2 == 0) Color.BLACK else Color.DKGRAY
                )
                val pdImage = JPEGFactory.createFromStream(doc, ByteArrayInputStream(jpegBytes))
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImage, 0f, 0f, width.toFloat(), height.toFloat())
                }
            }
            doc.save(file)
        }
    }

    private fun convertPdfUsingDirectAndStreamingCbzWriter(
        pdfFile: File,
        outputDir: File,
        config: PdfConversionConfig,
        storageChecker: StorageSpaceChecker = AlwaysEnoughStorageChecker(),
        cancelAfterPages: Int? = null
    ): File {
        if (!storageChecker.hasEnoughSpace(outputDir, pdfFile.length() * 2)) {
            throw PdfConversionException.InsufficientStorage()
        }

        val target = DirectoryCbzOutputTarget(outputDir)
        val desiredName = FilenameUtils.toCbzFileName(pdfFile.name)
        val destination = target.resolveDestination(desiredName, config.duplicatePolicy)
        if (destination is ResolvedDestination.SkipExisting) {
            return File(outputDir, destination.existingFileName)
        }
        val writeDest = destination as ResolvedDestination.WriteTo

        val workDir = tempFolder.newFolder("work_${System.nanoTime()}")
        val cancelled = AtomicBoolean(false)

        PDDocument.load(pdfFile).use { pdDoc ->
            if (pdDoc.isEncrypted) throw PdfConversionException.PasswordProtected()
            val extractor = PdfBoxDirectImageExtractor(pdDoc)
            val total = pdDoc.numberOfPages

            if (config.imageFormat == ImageFormat.JPEG) {
                target.openOutputStream(writeDest).use { os ->
                    CbzArchiveWriter.StreamingCbzSession(
                        outputStream = os,
                        comicTitle = writeDest.finalFileName,
                        includeComicInfoXml = config.includeComicInfoXml
                    ).use { session ->
                        for (i in 0 until total) {
                            if (cancelAfterPages != null && i >= cancelAfterPages) {
                                cancelled.set(true)
                            }
                            if (cancelled.get()) {
                                target.deleteIfBroken(writeDest)
                                throw PdfConversionException.Cancelled()
                            }
                            val name = FilenameUtils.formatPageFileName(i, total, config.imageFormat.extension)
                            val rawJpeg = extractor.tryExtractRawJpegBytes(i, config)
                            assertNotNull("Page $i should zero-copy extract cleanly", rawJpeg)
                            session.writeStoredPage(
                                EncodedPage(
                                    pageIndex = i,
                                    fileName = name,
                                    bytes = rawJpeg!!,
                                    crc32 = CbzArchiveWriter.computeCrc32(rawJpeg),
                                    mode = PageMode.DIRECT_EXTRACTED
                                )
                            )
                        }
                        session.finishArchive()
                    }
                }
            } else {
                val pageFiles = mutableListOf<File>()
                for (i in 0 until total) {
                    if (cancelAfterPages != null && i >= cancelAfterPages) {
                        cancelled.set(true)
                    }
                    if (cancelled.get()) {
                        target.deleteIfBroken(writeDest)
                        throw PdfConversionException.Cancelled()
                    }
                    val name = FilenameUtils.formatPageFileName(i, total, config.imageFormat.extension)
                    val outImg = File(workDir, name)
                    val ok = extractor.tryExtractPageImage(i, outImg, config)
                    assertTrue("Page $i should extract cleanly", ok)
                    pageFiles.add(outImg)
                }
                target.openOutputStream(writeDest).use { os ->
                    CbzArchiveWriter.writeCbz(
                        pageFiles = pageFiles,
                        outputStream = os,
                        comicTitle = writeDest.finalFileName,
                        includeComicInfoXml = config.includeComicInfoXml
                    )
                }
            }
        }
        return File(outputDir, writeDest.finalFileName)
    }

    private fun assertAllImagesReadableInZip(
        cbzFile: File,
        expectedNames: List<String>,
        expectComicInfo: Boolean = false
    ) {
        val report = CbzArchiveWriter.validateCbzFile(cbzFile, expectedNames, expectComicInfo)
        assertTrue("CBZ entry order must match expected page order", report.matchesExpectedOrder)

        ZipFile(cbzFile).use { zip ->
            for (name in expectedNames) {
                val entry = zip.getEntry(name)
                assertNotNull("Missing entry $name", entry)
                assertEquals("Image entry $name should use STORED method", ZipEntry.STORED, entry.method)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                assertNotNull("Image $name inside CBZ could not be decoded", decoded)
                assertTrue(decoded.width > 0 && decoded.height > 0)
                decoded.recycle()
            }
        }
    }

    @Test
    fun singlePagePdfToCbz() {
        val pdf = tempFolder.newFile("single_page.pdf")
        val outDir = tempFolder.newFolder("out_single")
        createTestPdfWithImages(pdf, pageCount = 1)

        val cbz = convertPdfUsingDirectAndStreamingCbzWriter(
            pdf,
            outDir,
            PdfConversionConfig(ImageFormat.JPEG, 150, 90, true, false, DuplicatePolicy.RENAME, true)
        )
        assertEquals("single_page.cbz", cbz.name)
        assertAllImagesReadableInZip(cbz, listOf("0001.jpg"))
    }

    @Test
    fun overHundredPagesPdfToCbz() {
        val pdf = tempFolder.newFile("long_manga.pdf")
        val outDir = tempFolder.newFolder("out_long")
        createTestPdfWithImages(pdf, pageCount = 105, width = 120, height = 160)

        val cbz = convertPdfUsingDirectAndStreamingCbzWriter(
            pdf,
            outDir,
            PdfConversionConfig(ImageFormat.JPEG, 150, 90, true, true, DuplicatePolicy.RENAME, true)
        )
        val expected = (0 until 105).map { FilenameUtils.formatPageFileName(it, 105, "jpg") }
        assertAllImagesReadableInZip(cbz, expected, expectComicInfo = true)
    }

    @Test
    fun multiplePdfsProduceSeparateCbzArchives() {
        val outDir = tempFolder.newFolder("out_batch")
        val config = PdfConversionConfig(ImageFormat.JPEG, 150, 90, true, false, DuplicatePolicy.RENAME, true)

        val inputs = listOf("manga01.pdf", "manga02.pdf", "manga03.pdf").map { name ->
            tempFolder.newFile(name).also { createTestPdfWithImages(it, pageCount = 3) }
        }

        val outputs = inputs.map { convertPdfUsingDirectAndStreamingCbzWriter(it, outDir, config) }
        assertEquals(listOf("manga01.cbz", "manga02.cbz", "manga03.cbz"), outputs.map { it.name })
        outputs.forEach { cbz ->
            assertAllImagesReadableInZip(cbz, listOf("0001.jpg", "0002.jpg", "0003.jpg"))
        }
    }

    @Test
    fun supportsJpegPngAndWebpOutputs() {
        val pdf = tempFolder.newFile("formats.pdf")
        createTestPdfWithImages(pdf, pageCount = 2)

        for (format in listOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP)) {
            val outDir = tempFolder.newFolder("out_${format.name}")
            val config = PdfConversionConfig(format, 150, 88, true, false, DuplicatePolicy.RENAME, true)
            val cbz = convertPdfUsingDirectAndStreamingCbzWriter(pdf, outDir, config)
            val expected = listOf(
                "0001.${format.extension}",
                "0002.${format.extension}"
            )
            assertAllImagesReadableInZip(cbz, expected)
        }
    }

    @Test
    fun handlesDuplicateFilenamesAndOutputFolderChange() {
        val pdf = tempFolder.newFile("dup_manga.pdf")
        createTestPdfWithImages(pdf, pageCount = 1)

        val outDir1 = tempFolder.newFolder("out_dir_1")
        val outDir2 = tempFolder.newFolder("out_dir_2")

        val renameConfig = PdfConversionConfig(ImageFormat.JPEG, 150, 90, true, false, DuplicatePolicy.RENAME, true)
        val first = convertPdfUsingDirectAndStreamingCbzWriter(pdf, outDir1, renameConfig)
        val second = convertPdfUsingDirectAndStreamingCbzWriter(pdf, outDir1, renameConfig)
        val thirdInAnotherDir = convertPdfUsingDirectAndStreamingCbzWriter(pdf, outDir2, renameConfig)

        assertEquals("dup_manga.cbz", first.name)
        assertEquals("dup_manga (1).cbz", second.name)
        assertEquals("dup_manga.cbz", thirdInAnotherDir.name)
    }

    @Test(expected = PdfConversionException.InsufficientStorage::class)
    fun throwsInsufficientStorageWhenDiskSpaceTooLow() {
        val pdf = tempFolder.newFile("low_space.pdf")
        createTestPdfWithImages(pdf, pageCount = 1)
        val outDir = tempFolder.newFolder("out_low")
        val lowChecker = object : StorageSpaceChecker {
            override fun getAvailableBytes(path: File): Long = 1024L
            override fun hasEnoughSpace(path: File, requiredBytes: Long): Boolean = false
        }
        convertPdfUsingDirectAndStreamingCbzWriter(
            pdf,
            outDir,
            PdfConversionConfig(ImageFormat.JPEG, 150, 90, true, false, DuplicatePolicy.RENAME, true),
            storageChecker = lowChecker
        )
    }

    @Test
    fun cancellationCleansUpPartialCbzAndAllowsRetry() = runBlocking {
        val pdf = tempFolder.newFile("cancel_test.pdf")
        createTestPdfWithImages(pdf, pageCount = 4)
        val outDir = tempFolder.newFolder("out_cancel")
        val config = PdfConversionConfig(ImageFormat.JPEG, 150, 90, true, false, DuplicatePolicy.RENAME, true)

        var threwCancel = false
        try {
            convertPdfUsingDirectAndStreamingCbzWriter(pdf, outDir, config, cancelAfterPages = 2)
        } catch (_: PdfConversionException.Cancelled) {
            threwCancel = true
        }
        assertTrue(threwCancel)
        assertFalse(File(outDir, "cancel_test.cbz").exists())

        val retried = convertPdfUsingDirectAndStreamingCbzWriter(pdf, outDir, config)
        assertTrue(retried.exists())
        assertAllImagesReadableInZip(retried, listOf("0001.jpg", "0002.jpg", "0003.jpg", "0004.jpg"))
    }

    @Test
    fun directExtractorRejectsNonFaithfulPagesAndAcceptsCleanFullPageJpeg() {
        val config = PdfConversionConfig(ImageFormat.JPEG, 150, 90, true, false, DuplicatePolicy.RENAME, true)
        val jpegBytes = createSampleJpegBytes(width = 240, height = 340)

        PDDocument().use { doc ->
            val pdImage = JPEGFactory.createFromStream(doc, ByteArrayInputStream(jpegBytes))

            // Page 0: Clean upright full-page JPEG -> MUST succeed
            val cleanPage = PDPage(PDRectangle(240f, 340f))
            doc.addPage(cleanPage)
            PDPageContentStream(doc, cleanPage).use { cs ->
                cs.drawImage(pdImage, 0f, 0f, 240f, 340f)
            }

            // Page 1: Rotated page -> MUST reject
            val rotatedPage = PDPage(PDRectangle(240f, 340f)).apply { rotation = 90 }
            doc.addPage(rotatedPage)
            PDPageContentStream(doc, rotatedPage).use { cs ->
                cs.drawImage(pdImage, 0f, 0f, 240f, 340f)
            }

            // Page 2: Annotated page -> MUST reject
            val annotatedPage = PDPage(PDRectangle(240f, 340f))
            doc.addPage(annotatedPage)
            PDPageContentStream(doc, annotatedPage).use { cs ->
                cs.drawImage(pdImage, 0f, 0f, 240f, 340f)
            }
            annotatedPage.annotations.add(PDAnnotationText().apply {
                rectangle = PDRectangle(10f, 10f, 30f, 30f)
                contents = "Note"
            })

            // Page 3: Text overlay -> MUST reject
            val textOverlayPage = PDPage(PDRectangle(240f, 340f))
            doc.addPage(textOverlayPage)
            PDPageContentStream(doc, textOverlayPage).use { cs ->
                cs.drawImage(pdImage, 0f, 0f, 240f, 340f)
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(20f, 20f)
                cs.showText("Subtitle")
                cs.endText()
            }

            // Page 4: Rotated CTM transform -> MUST reject
            val rotatedCtmPage = PDPage(PDRectangle(340f, 240f))
            doc.addPage(rotatedCtmPage)
            PDPageContentStream(doc, rotatedCtmPage).use { cs ->
                cs.drawImage(pdImage, Matrix(0f, 240f, -340f, 0f, 340f, 0f))
            }

            // Page 5: Partial / cropped CTM placement -> MUST reject
            val partialPage = PDPage(PDRectangle(240f, 340f))
            doc.addPage(partialPage)
            PDPageContentStream(doc, partialPage).use { cs ->
                cs.drawImage(pdImage, 20f, 20f, 120f, 170f)
            }

            // Page 6: Inverted Decode array -> MUST reject
            val invertedImage = JPEGFactory.createFromStream(doc, ByteArrayInputStream(jpegBytes)).apply {
                cosObject.setItem(
                    COSName.DECODE,
                    COSArray().apply {
                        add(COSInteger.ONE); add(COSInteger.ZERO)
                        add(COSInteger.ONE); add(COSInteger.ZERO)
                        add(COSInteger.ONE); add(COSInteger.ZERO)
                    }
                )
            }
            val invertedPage = PDPage(PDRectangle(240f, 340f))
            doc.addPage(invertedPage)
            PDPageContentStream(doc, invertedPage).use { cs ->
                cs.drawImage(invertedImage, 0f, 0f, 240f, 340f)
            }

            val extractor = PdfBoxDirectImageExtractor(doc)
            assertNotNull("Clean page should extract raw JPEG", extractor.tryExtractRawJpegBytes(0, config))
            assertNull("Rotated page must fall back to PdfRenderer", extractor.tryExtractRawJpegBytes(1, config))
            assertNull("Annotated page must fall back to PdfRenderer", extractor.tryExtractRawJpegBytes(2, config))
            assertNull("Text overlay page must fall back to PdfRenderer", extractor.tryExtractRawJpegBytes(3, config))
            assertNull("Rotated CTM page must fall back to PdfRenderer", extractor.tryExtractRawJpegBytes(4, config))
            assertNull("Partial placement page must fall back to PdfRenderer", extractor.tryExtractRawJpegBytes(5, config))
            assertNull("Inverted Decode array page must fall back to PdfRenderer", extractor.tryExtractRawJpegBytes(6, config))
        }
    }

    @Test
    fun bitmapPoolReusesSameSizedBitmapsAndEnforcesBudget() {
        // 100x100 ARGB_8888 = 40,000 bytes; budget 50,000 bytes allows 1 pooled bitmap at a time
        val pool = BitmapPool(maxTotalBytes = 50_000L)
        val b1 = pool.obtain(100, 100, Bitmap.Config.ARGB_8888)
        assertTrue(b1.isMutable)
        pool.release(b1)

        val reused = pool.obtain(100, 100, Bitmap.Config.ARGB_8888)
        assertSame("Pool should return the released bitmap", b1, reused)

        val b2 = pool.obtain(100, 100, Bitmap.Config.ARGB_8888)
        assertNotSame(reused, b2)

        pool.release(reused)
        pool.release(b2)
        // Releasing b2 when budget is 50,000 bytes evicts reused
        assertTrue("Oldest bitmap should be recycled on LRU eviction", reused.isRecycled)
        assertFalse("Newest bitmap should remain pooled", b2.isRecycled)

        pool.clear()
        assertTrue("clear() should recycle all pooled bitmaps", b2.isRecycled)
    }
}

private class AlwaysEnoughStorageChecker : StorageSpaceChecker {
    override fun getAvailableBytes(path: File): Long = 10L * 1024L * 1024L * 1024L
    override fun hasEnoughSpace(path: File, requiredBytes: Long): Boolean = true
}

private class DirectoryCbzOutputTarget(private val dir: File) : CbzOutputTarget {
    override fun resolveDestination(desiredCbzName: String, policy: DuplicatePolicy): ResolvedDestination {
        val existingNames = dir.listFiles()?.map { it.name }?.toSet().orEmpty()
        return when (policy) {
            DuplicatePolicy.SKIP -> {
                if (existingNames.any { it.equals(desiredCbzName, ignoreCase = true) }) {
                    ResolvedDestination.SkipExisting(desiredCbzName)
                } else {
                    ResolvedDestination.WriteTo(desiredCbzName, File(dir, desiredCbzName).toURI().toString())
                }
            }
            DuplicatePolicy.OVERWRITE -> {
                File(dir, desiredCbzName).delete()
                ResolvedDestination.WriteTo(desiredCbzName, File(dir, desiredCbzName).toURI().toString())
            }
            DuplicatePolicy.RENAME -> {
                val unique = FilenameUtils.resolveUniqueCbzName(desiredCbzName, existingNames)
                ResolvedDestination.WriteTo(unique, File(dir, unique).toURI().toString())
            }
        }
    }

    override fun openOutputStream(destination: ResolvedDestination): OutputStream {
        val write = destination as ResolvedDestination.WriteTo
        return FileOutputStream(File(dir, write.finalFileName))
    }

    override fun querySize(destination: ResolvedDestination): Long {
        val write = destination as ResolvedDestination.WriteTo
        return File(dir, write.finalFileName).length()
    }

    override fun deleteIfBroken(destination: ResolvedDestination) {
        val write = destination as? ResolvedDestination.WriteTo ?: return
        File(dir, write.finalFileName).delete()
    }
}
