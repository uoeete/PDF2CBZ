package com.pdf2cbz.app.domain.converter

import android.graphics.Bitmap
import android.os.Build
import com.pdf2cbz.app.data.model.ImageFormat
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.FileOutputStream

/**
 * Safely extracts embedded page images directly from a PDF when the page consists
 * solely of a single upright image without text layers, vector drawings, masks,
 * or non-trivial transforms. Falls back to null whenever anything indicates
 * rendering via PdfRenderer is safer to preserve visual fidelity.
 */
class PdfBoxDirectImageExtractor(
    private val document: PDDocument
) {

    fun tryExtractPageImage(
        pageIndex: Int,
        targetImageFile: File,
        config: PdfConversionConfig
    ): Boolean {
        return try {
            if (pageIndex < 0 || pageIndex >= document.numberOfPages) return false
            val page = document.getPage(pageIndex)

            // Reject pages with annotations (links/stamps/notes that alter visual rendering)
            if (page.annotations.isNotEmpty()) return false
            // Reject rotated pages so we never emit sideways scans; PdfRenderer handles rotation natively
            if ((page.rotation % 360) != 0) return false

            val resources = page.resources ?: return false
            val xObjectNames = resources.xObjectNames?.toList().orEmpty()
            val imageXObjects = mutableListOf<Pair<COSName, PDImageXObject>>()

            for (name in xObjectNames) {
                if (resources.isImageXObject(name)) {
                    val img = resources.getXObject(name) as? PDImageXObject ?: return false
                    imageXObjects.add(name to img)
                } else {
                    // Form XObjects or shading XObjects require full PDF rendering
                    return false
                }
            }

            // Only direct-extract when the page is backed by exactly one primary image XObject
            if (imageXObjects.size != 1) return false
            val (_, imageXObject) = imageXObjects.first()

            // Reject masked / stencil / inverted images that need PDF blending
            if (imageXObject.isStencil) return false
            if (imageXObject.softMask != null) return false
            if (imageXObject.mask != null) return false

            // Inspect page content stream operators to ensure no text or vector paths exist
            if (!isPureSingleImageContentStream(page)) return false

            // Avoid blowing up tiny thumbnails or heavily upscaling; direct extraction preserves original scan pixels
            if (imageXObject.width < 100 || imageXObject.height < 100) return false

            val suffix = imageXObject.suffix?.lowercase()
            // Zero-recompression fast path: embedded JPEG -> output JPEG
            if (config.imageFormat == ImageFormat.JPEG && (suffix == "jpg" || suffix == "jpeg")) {
                imageXObject.createInputStream(listOf(COSName.DCT_DECODE.name)).use { input ->
                    FileOutputStream(targetImageFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                return targetImageFile.exists() && targetImageFile.length() > 0
            }

            // Otherwise decode the embedded image bitmap without PDF page rasterization loss and encode to requested format
            val bitmap: Bitmap = imageXObject.image ?: return false
            try {
                FileOutputStream(targetImageFile).use { out ->
                    val compressed = compressBitmap(bitmap, config, out)
                    if (!compressed) return false
                }
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            targetImageFile.exists() && targetImageFile.length() > 0
        } catch (_: OutOfMemoryError) {
            targetImageFile.delete()
            false
        } catch (_: Throwable) {
            targetImageFile.delete()
            false
        }
    }

    private fun isPureSingleImageContentStream(page: PDPage): Boolean {
        val parser = PDFStreamParser(page)
        parser.parse()
        val tokens = parser.tokens ?: return false

        var doCount = 0
        for (token in tokens) {
            if (token is Operator) {
                when (token.name) {
                    "Do" -> doCount++
                    // Allowed graphics state & matrix placement operators around a single Do
                    "q", "Q", "cm", "gs", "re", "W", "W*", "n" -> Unit
                    // Any text, stroke, fill, color path, shading, or inline image -> fallback to PdfRenderer
                    else -> return false
                }
                if (doCount > 1) return false
            }
        }
        return doCount == 1
    }

    @Suppress("DEPRECATION")
    private fun compressBitmap(
        bitmap: Bitmap,
        config: PdfConversionConfig,
        out: FileOutputStream
    ): Boolean {
        val compressFormat = when (config.imageFormat) {
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
        return bitmap.compress(compressFormat, quality, out)
    }
}
