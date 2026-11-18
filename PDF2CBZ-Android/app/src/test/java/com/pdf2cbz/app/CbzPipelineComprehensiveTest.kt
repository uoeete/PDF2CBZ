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
import com.pdf2cbz.app.domain.converter.CbzArchiveWriter
import com.pdf2cbz.app.domain.converter.CbzOutputTarget
import com.pdf2cbz.app.domain.converter.PdfBoxDirectImageExtractor
import com.pdf2cbz.app.domain.converter.PdfConversionConfig
import com.pdf2cbz.app.domain.converter.PdfConversionException
import com.pdf2cbz.app.domain.converter.ResolvedDestination
import com.pdf2cbz.app.domain.util.FilenameUtils
import com.pdf2cbz.app.domain.util.StorageSpaceChecker
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private fun createTestPdfWithImages(file: File, pageCount: Int, width: Int = 240, height: Int = 340) {
        PDDocument().use { doc ->
            for (i in 0 until pageCount) {
                val page = PDPage(PDRectangle(width.toFloat(), height.toFloat()))
                doc.addPage(page)

                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                val paint = Paint().apply {
                    color = if (i % 2 == 0) Color.BLACK else Color.DKGRAY
                    textSize = 24f
                }
                canvas.drawRect(20f, 20f, width - 20f, height - 20f, paint)

                val jpegBytes = ByteArrayOutputStream().use { bos ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
                    bos.toByteArray()
                }
                bmp.recycle()

                val pdImage = JPEGFactory.createFromStream(doc, ByteArrayInputStream(jpegBytes))
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImage, 0f, 0f, width.toFloat(), height.toFloat())
                }
            }
            doc.save(file)
        }
    }

    private fun convertPdfUsingDirectAndCbzWriter(
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
        val pageFiles = mutableListOf<File>()
        val cancelled = AtomicBoolean(false)

        PDDocument.load(pdfFile).use { pdDoc ->
            if (pdDoc.isEncrypted) throw PdfConversionException.PasswordProtected()
            val extractor = PdfBoxDirectImageExtractor(pdDoc)
            val total = pdDoc.numberOfPages

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
        }

        target.openOutputStream(writeDest).use { os ->
            CbzArchiveWriter.writeCbz(
                pageFiles = pageFiles,
                outputStream = os,
                comicTitle = writeDest.finalFileName,
                includeComicInfoXml = config.includeComicInfoXml
            )
        }
        return File(outputDir, writeDest.finalFileName)
    }

    private fun assertAllImagesReadableInZip(cbzFile: File, expectedNames: List<String>, expectComicInfo: Boolean = false) {
        val report = CbzArchiveWriter.validateCbzFile(cbzFile, expectedNames, expectComicInfo)
        assertTrue("CBZ entry order must match expected page order", report.matchesExpectedOrder)

        ZipFile(cbzFile).use { zip ->
            for (name in expectedNames) {
                val entry = zip.getEntry(name)
                assertNotNull("Missing entry $name", entry)
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

        val cbz = convertPdfUsingDirectAndCbzWriter(
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

        val cbz = convertPdfUsingDirectAndCbzWriter(
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

        val outputs = inputs.map { convertPdfUsingDirectAndCbzWriter(it, outDir, config) }
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
            val cbz = convertPdfUsingDirectAndCbzWriter(pdf, outDir, config)
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
        val first = convertPdfUsingDirectAndCbzWriter(pdf, outDir1, renameConfig)
        val second = convertPdfUsingDirectAndCbzWriter(pdf, outDir1, renameConfig)
        val thirdInAnotherDir = convertPdfUsingDirectAndCbzWriter(pdf, outDir2, renameConfig)

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
        convertPdfUsingDirectAndCbzWriter(
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
            convertPdfUsingDirectAndCbzWriter(pdf, outDir, config, cancelAfterPages = 2)
        } catch (_: PdfConversionException.Cancelled) {
            threwCancel = true
        }
        assertTrue(threwCancel)
        assertFalse(File(outDir, "cancel_test.cbz").exists())

        // Retry after cancellation succeeds cleanly
        val retried = convertPdfUsingDirectAndCbzWriter(pdf, outDir, config)
        assertTrue(retried.exists())
        assertAllImagesReadableInZip(retried, listOf("0001.jpg", "0002.jpg", "0003.jpg", "0004.jpg"))
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
