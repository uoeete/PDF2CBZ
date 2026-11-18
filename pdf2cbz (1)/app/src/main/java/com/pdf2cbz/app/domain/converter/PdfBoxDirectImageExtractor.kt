package com.pdf2cbz.app.domain.converter

import android.graphics.Bitmap
import android.os.Build
import com.pdf2cbz.app.data.model.ImageFormat
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColorSpace
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * Safely extracts embedded page images directly from a PDF ONLY when the entire page appearance
 * is guaranteed to be visually identical to PdfRenderer:
 * - No page annotations or page rotation
 * - Exactly one upright, non-masked, non-stencil image XObject
 * - Standard RGB/Gray color space without custom Decode inversion
 * - Content stream contains no text, vector strokes/fills, blend modes, or transparency
 * - CTM at `Do` has zero rotation/skew/mirroring and covers the page CropBox cleanly
 */
class PdfBoxDirectImageExtractor(
    private val document: PDDocument
) {

    private data class AffineMatrix(
        val a: Float = 1f,
        val b: Float = 0f,
        val c: Float = 0f,
        val d: Float = 1f,
        val e: Float = 0f,
        val f: Float = 0f
    ) {
        fun preConcatenate(m: AffineMatrix): AffineMatrix = AffineMatrix(
            a = m.a * a + m.b * c,
            b = m.a * b + m.b * d,
            c = m.c * a + m.d * c,
            d = m.c * b + m.d * d,
            e = m.e * a + m.f * c + e,
            f = m.e * b + m.f * d + f
        )
    }

    /**
     * Zero-copy fast path for JPEG output: returns raw DCTDecode JPEG bytes directly if and only if
     * the embedded image reproduces the entire page faithfully without re-encoding.
     */
    fun tryExtractRawJpegBytes(
        pageIndex: Int,
        config: PdfConversionConfig
    ): ByteArray? {
        if (config.imageFormat != ImageFormat.JPEG) return null
        return try {
            val imageXObject = findSafeSingleFullPageImage(pageIndex) ?: return null
            val suffix = imageXObject.suffix?.lowercase()
            if (suffix != "jpg" && suffix != "jpeg") return null

            val baos = ByteArrayOutputStream(64 * 1024)
            imageXObject.createInputStream(listOf(COSName.DCT_DECODE.name)).use { input ->
                input.copyTo(baos, bufferSize = 32 * 1024)
            }
            val bytes = baos.toByteArray()
            if (bytes.size > 4) bytes else null
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Standalone file extraction helper (preserves unit test and multi-format compatibility).
     */
    fun tryExtractPageImage(
        pageIndex: Int,
        targetImageFile: File,
        config: PdfConversionConfig
    ): Boolean {
        return try {
            val imageXObject = findSafeSingleFullPageImage(pageIndex) ?: return false
            val suffix = imageXObject.suffix?.lowercase()

            if (config.imageFormat == ImageFormat.JPEG && (suffix == "jpg" || suffix == "jpeg")) {
                imageXObject.createInputStream(listOf(COSName.DCT_DECODE.name)).use { input ->
                    FileOutputStream(targetImageFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                return targetImageFile.exists() && targetImageFile.length() > 0
            }

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

    private fun findSafeSingleFullPageImage(pageIndex: Int): PDImageXObject? {
        if (pageIndex < 0 || pageIndex >= document.numberOfPages) return null
        val page = document.getPage(pageIndex)

        if (page.annotations.isNotEmpty()) return null
        if ((page.rotation % 360) != 0) return null

        val resources = page.resources ?: return null
        val xObjectNames = resources.xObjectNames?.toList().orEmpty()
        var targetName: COSName? = null
        var targetImage: PDImageXObject? = null

        for (name in xObjectNames) {
            if (!resources.isImageXObject(name)) {
                return null
            }
            if (targetImage != null) {
                return null
            }
            val img = resources.getXObject(name) as? PDImageXObject ?: return null
            targetName = name
            targetImage = img
        }

        val imageXObject = targetImage ?: return null
        val imageCosName = targetName ?: return null

        if (imageXObject.isStencil) return null
        if (imageXObject.softMask != null) return null
        if (imageXObject.mask != null) return null
        if (imageXObject.width < 100 || imageXObject.height < 100) return null

        if (!isStandardColorSpace(imageXObject.colorSpace)) return null
        if (hasCustomDecodeArray(imageXObject)) return null

        if (!isPureFullPageImageContentStream(page, resources, imageCosName)) return null
        return imageXObject
    }

    private fun isStandardColorSpace(cs: PDColorSpace?): Boolean {
        return when {
            cs is PDDeviceRGB || cs is PDDeviceGray -> true
            cs != null && cs.name.equals(COSName.ICCBASED.name, ignoreCase = true) -> {
                val comps = cs.numberOfComponents
                comps == 1 || comps == 3
            }
            else -> false
        }
    }

    private fun hasCustomDecodeArray(image: PDImageXObject): Boolean {
        val decode = image.cosObject.getDictionaryObject(COSName.DECODE) as? COSArray ?: return false
        for (i in 0 until decode.size() step 2) {
            val minVal = (decode.getObject(i) as? COSNumber)?.floatValue() ?: return true
            val maxVal = (decode.getObject(i + 1) as? COSNumber)?.floatValue() ?: return true
            if (abs(minVal - 0f) > 0.01f || abs(maxVal - 1f) > 0.01f) {
                return true
            }
        }
        return false
    }

    private fun isPureFullPageImageContentStream(
        page: PDPage,
        resources: PDResources,
        expectedImageName: COSName
    ): Boolean {
        val parser = PDFStreamParser(page)
        parser.parse()
        val tokens = parser.tokens ?: return false

        val stack = ArrayDeque<AffineMatrix>()
        var ctm = AffineMatrix()
        val operands = ArrayList<COSBase>(8)
        var pendingRect: FloatArray? = null
        var doCount = 0

        val cropBox = page.cropBox ?: page.mediaBox ?: return false
        val tolX = max(4f, cropBox.width * 0.02f)
        val tolY = max(4f, cropBox.height * 0.02f)

        for (token in tokens) {
            if (token is Operator) {
                when (token.name) {
                    "q" -> stack.addLast(ctm)
                    "Q" -> ctm = if (stack.isNotEmpty()) stack.removeLast() else return false
                    "cm" -> {
                        if (operands.size < 6) return false
                        val a = (operands[operands.size - 6] as? COSNumber)?.floatValue() ?: return false
                        val b = (operands[operands.size - 5] as? COSNumber)?.floatValue() ?: return false
                        val c = (operands[operands.size - 4] as? COSNumber)?.floatValue() ?: return false
                        val d = (operands[operands.size - 3] as? COSNumber)?.floatValue() ?: return false
                        val e = (operands[operands.size - 2] as? COSNumber)?.floatValue() ?: return false
                        val f = (operands[operands.size - 1] as? COSNumber)?.floatValue() ?: return false
                        ctm = ctm.preConcatenate(AffineMatrix(a, b, c, d, e, f))
                    }
                    "gs" -> {
                        val gsName = operands.lastOrNull() as? COSName ?: return false
                        val gs = resources.getExtGState(gsName) ?: return false
                        if ((gs.nonStrokingAlphaConstant ?: 1f) < 0.99f) return false
                        if ((gs.strokingAlphaConstant ?: 1f) < 0.99f) return false
                        if (gs.softMask != null) return false
                        val bm = gs.cosObject.getDictionaryObject(COSName.BM)
                        if (bm != null && bm != COSName.NORMAL && bm != COSName.COMPATIBLE) return false
                    }
                    "re" -> {
                        if (operands.size < 4) return false
                        val rx = (operands[operands.size - 4] as? COSNumber)?.floatValue() ?: return false
                        val ry = (operands[operands.size - 3] as? COSNumber)?.floatValue() ?: return false
                        val rw = (operands[operands.size - 2] as? COSNumber)?.floatValue() ?: return false
                        val rh = (operands[operands.size - 1] as? COSNumber)?.floatValue() ?: return false
                        pendingRect = floatArrayOf(rx, ry, rw, rh)
                    }
                    "W", "W*" -> {
                        val rect = pendingRect ?: return false
                        if (abs(ctm.b) > 0.01f || abs(ctm.c) > 0.01f) return false
                        val x1 = ctm.e + rect[0] * ctm.a
                        val y1 = ctm.f + rect[1] * ctm.d
                        val x2 = x1 + rect[2] * ctm.a
                        val y2 = y1 + rect[3] * ctm.d
                        val clipLeft = minOf(x1, x2)
                        val clipRight = maxOf(x1, x2)
                        val clipBottom = minOf(y1, y2)
                        val clipTop = maxOf(y1, y2)
                        // Reject if clip crops inside the visible page box
                        if (clipLeft > cropBox.lowerLeftX + tolX ||
                            clipBottom > cropBox.lowerLeftY + tolY ||
                            clipRight < cropBox.upperRightX - tolX ||
                            clipTop < cropBox.upperRightY - tolY
                        ) {
                            return false
                        }
                    }
                    "n" -> pendingRect = null
                    "Do" -> {
                        val name = operands.lastOrNull() as? COSName ?: return false
                        if (name != expectedImageName) return false
                        doCount++
                        if (doCount > 1) return false

                        // Must be upright, unskewed, non-mirrored
                        if (abs(ctm.b) > 0.01f || abs(ctm.c) > 0.01f) return false
                        if (ctm.a <= 0f || ctm.d <= 0f) return false

                        // Drawn bounds must cover the page CropBox faithfully
                        val left = ctm.e
                        val bottom = ctm.f
                        val right = ctm.e + ctm.a
                        val top = ctm.f + ctm.d

                        if (abs(left - cropBox.lowerLeftX) > tolX ||
                            abs(bottom - cropBox.lowerLeftY) > tolY ||
                            abs(right - cropBox.upperRightX) > tolX ||
                            abs(top - cropBox.upperRightY) > tolY
                        ) {
                            return false
                        }
                    }
                    else -> return false
                }
                operands.clear()
            } else if (token is COSBase) {
                operands.add(token)
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
