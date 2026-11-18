package com.pdf2cbz.app

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pdf2cbz.app.data.model.DuplicatePolicy
import com.pdf2cbz.app.data.model.ImageFormat
import com.pdf2cbz.app.domain.converter.CbzArchiveWriter
import com.pdf2cbz.app.domain.converter.CbzOutputTarget
import com.pdf2cbz.app.domain.converter.PdfConversionConfig
import com.pdf2cbz.app.domain.converter.PdfToCbzPipeline
import com.pdf2cbz.app.domain.converter.ResolvedDestination
import com.pdf2cbz.app.domain.util.AndroidStorageSpaceChecker
import com.pdf2cbz.app.domain.util.FilenameUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

@RunWith(AndroidJUnit4::class)
class PdfRendererEndToEndInstrumentedTest {

    @Test
    fun convertsVectorPdfViaPdfRendererIntoValidCbz() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val pdfFile = File(context.cacheDir, "instrumented_test.pdf")
        val outDir = File(context.cacheDir, "instrumented_out").apply { mkdirs() }

        val doc = PdfDocument()
        for (i in 1..3) {
            val pageInfo = PdfDocument.PageInfo.Builder(400, 600, i).create()
            val page = doc.startPage(pageInfo)
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 36f
            }
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText("Page $i", 40f, 120f, paint)
            doc.finishPage(page)
        }
        FileOutputStream(pdfFile).use { doc.writeTo(it) }
        doc.close()

        val pipeline = PdfToCbzPipeline(context, AndroidStorageSpaceChecker())
        val sink = object : CbzOutputTarget {
            override fun resolveDestination(desiredCbzName: String, policy: DuplicatePolicy): ResolvedDestination {
                val target = File(outDir, desiredCbzName)
                return ResolvedDestination.WriteTo(desiredCbzName, target.toURI().toString())
            }

            override fun openOutputStream(destination: ResolvedDestination): OutputStream {
                val write = destination as ResolvedDestination.WriteTo
                return FileOutputStream(File(outDir, write.finalFileName))
            }

            override fun querySize(destination: ResolvedDestination): Long {
                val write = destination as ResolvedDestination.WriteTo
                return File(outDir, write.finalFileName).length()
            }

            override fun deleteIfBroken(destination: ResolvedDestination) {
                val write = destination as? ResolvedDestination.WriteTo ?: return
                File(outDir, write.finalFileName).delete()
            }
        }

        val result = pipeline.convertSinglePdf(
            sourceUri = Uri.fromFile(pdfFile),
            sourceFileName = pdfFile.name,
            sourceSizeBytes = pdfFile.length(),
            outputTarget = sink,
            config = PdfConversionConfig(
                imageFormat = ImageFormat.JPEG,
                dpi = 150,
                jpegQuality = 90,
                preferDirectImageExtraction = true,
                includeComicInfoXml = true,
                duplicatePolicy = DuplicatePolicy.OVERWRITE,
                autoDeleteTempFiles = true
            ),
            isCancelled = { false },
            awaitIfPaused = {},
            onPageProgress = { _, _, _ -> }
        )

        val cbzFile = File(outDir, result.outputFileName)
        val report = CbzArchiveWriter.validateCbzFile(
            cbzFile,
            listOf("0001.jpg", "0002.jpg", "0003.jpg"),
            expectComicInfoXml = true
        )
        assertTrue(report.matchesExpectedOrder)
        pdfFile.delete()
        outDir.deleteRecursively()
    }
}
