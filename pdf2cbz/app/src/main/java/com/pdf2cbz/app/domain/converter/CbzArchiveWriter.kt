package com.pdf2cbz.app.domain.converter

import com.pdf2cbz.app.domain.util.ComicInfoXmlBuilder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Packages ordered page images into a flat Mihon-compatible CBZ (ZIP) archive.
 * - Uses ZipEntry.STORED (method 0) for already-compressed JPEG/PNG/WebP images to eliminate wasted Deflate CPU.
 * - Streams directly with large 256KB buffering without loading the whole archive into memory.
 */
object CbzArchiveWriter {

    private const val STREAM_BUFFER_SIZE = 256 * 1024

    fun computeCrc32(bytes: ByteArray): Long {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value
    }

    class StreamingCbzSession(
        outputStream: OutputStream,
        private val comicTitle: String,
        private val includeComicInfoXml: Boolean
    ) : Closeable {
        private val zos = ZipOutputStream(BufferedOutputStream(outputStream, STREAM_BUFFER_SIZE))
        private var pagesWritten = 0
        private var finished = false

        fun writeStoredPage(page: EncodedPage) {
            val entry = ZipEntry(page.fileName).apply {
                method = ZipEntry.STORED
                size = page.bytes.size.toLong()
                compressedSize = page.bytes.size.toLong()
                crc = page.crc32
                time = System.currentTimeMillis()
            }
            zos.putNextEntry(entry)
            zos.write(page.bytes)
            zos.closeEntry()
            pagesWritten++
        }

        fun finishArchive() {
            if (finished) return
            if (includeComicInfoXml) {
                val xmlBytes = ComicInfoXmlBuilder.build(
                    title = comicTitle,
                    pageCount = pagesWritten
                ).toByteArray(Charsets.UTF_8)
                zos.setLevel(Deflater.BEST_SPEED)
                val xmlEntry = ZipEntry("ComicInfo.xml").apply {
                    method = ZipEntry.DEFLATED
                    time = System.currentTimeMillis()
                }
                zos.putNextEntry(xmlEntry)
                zos.write(xmlBytes)
                zos.closeEntry()
            }
            zos.finish()
            zos.flush()
            finished = true
        }

        override fun close() {
            zos.close()
        }
    }

    fun writeCbz(
        pageFiles: List<File>,
        outputStream: OutputStream,
        comicTitle: String,
        includeComicInfoXml: Boolean,
        checkCancelled: () -> Unit = {}
    ) {
        val sortedFiles = pageFiles.sortedBy { it.name }
        val buffer = ByteArray(64 * 1024)

        ZipOutputStream(BufferedOutputStream(outputStream, STREAM_BUFFER_SIZE)).use { zos ->
            for (file in sortedFiles) {
                checkCancelled()
                val fileLength = file.length()
                val crc = computeFileCrc32(file, buffer)
                val entry = ZipEntry(file.name).apply {
                    method = ZipEntry.STORED
                    size = fileLength
                    compressedSize = fileLength
                    this.crc = crc
                    time = file.lastModified()
                }
                zos.putNextEntry(entry)
                BufferedInputStream(FileInputStream(file), 64 * 1024).use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        zos.write(buffer, 0, read)
                    }
                }
                zos.closeEntry()
            }

            if (includeComicInfoXml) {
                checkCancelled()
                val xmlBytes = ComicInfoXmlBuilder.build(
                    title = comicTitle,
                    pageCount = sortedFiles.size
                ).toByteArray(Charsets.UTF_8)
                zos.setLevel(Deflater.BEST_SPEED)
                val xmlEntry = ZipEntry("ComicInfo.xml").apply {
                    method = ZipEntry.DEFLATED
                }
                zos.putNextEntry(xmlEntry)
                zos.write(xmlBytes)
                zos.closeEntry()
            }
            zos.finish()
        }
    }

    private fun computeFileCrc32(file: File, buffer: ByteArray): Long {
        val crc = CRC32()
        BufferedInputStream(FileInputStream(file), 64 * 1024).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }

    /**
     * Validates a CBZ archive on disk: checks ZIP integrity, flat structure,
     * absence of PDF entries, and readable image payloads.
     */
    fun validateCbzFile(
        cbzFile: File,
        expectedImageNames: List<String>,
        expectComicInfoXml: Boolean = false
    ): CbzValidationReport {
        val entries = mutableListOf<String>()
        ZipFile(cbzFile).use { zip ->
            val enum = zip.entries()
            while (enum.hasMoreElements()) {
                val entry = enum.nextElement()
                if (entry.isDirectory || entry.name.contains("/")) {
                    throw IllegalStateException("Unexpected nested directory in CBZ: ${entry.name}")
                }
                if (entry.name.lowercase().endsWith(".pdf")) {
                    throw IllegalStateException("CBZ must not contain source PDF: ${entry.name}")
                }
                zip.getInputStream(entry).use { stream: InputStream ->
                    val data = stream.readBytes()
                    if (data.isEmpty()) {
                        throw IllegalStateException("Empty CBZ entry: ${entry.name}")
                    }
                }
                entries.add(entry.name)
            }
        }
        val imageEntries = entries.filter { !it.equals("ComicInfo.xml", ignoreCase = true) }
        val hasComicInfo = entries.any { it.equals("ComicInfo.xml", ignoreCase = true) }
        return CbzValidationReport(
            imageEntries = imageEntries,
            hasComicInfoXml = hasComicInfo,
            matchesExpectedOrder = imageEntries == expectedImageNames &&
                (!expectComicInfoXml || hasComicInfo)
        )
    }
}

data class CbzValidationReport(
    val imageEntries: List<String>,
    val hasComicInfoXml: Boolean,
    val matchesExpectedOrder: Boolean
)
