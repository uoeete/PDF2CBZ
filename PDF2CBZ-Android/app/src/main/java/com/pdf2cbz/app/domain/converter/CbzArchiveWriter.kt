package com.pdf2cbz.app.domain.converter

import com.pdf2cbz.app.domain.util.ComicInfoXmlBuilder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Packages ordered page image files into a flat Mihon-compatible CBZ (ZIP) archive.
 * - No nested folder hierarchy inside the archive
 * - Never includes the source PDF
 * - Streams page files sequentially without loading the archive into memory
 */
object CbzArchiveWriter {

    private const val STREAM_BUFFER_SIZE = 64 * 1024

    fun writeCbz(
        pageFiles: List<File>,
        outputStream: OutputStream,
        comicTitle: String,
        includeComicInfoXml: Boolean,
        checkCancelled: () -> Unit = {}
    ) {
        val sortedFiles = pageFiles.sortedBy { it.name }
        val buffer = ByteArray(STREAM_BUFFER_SIZE)

        ZipOutputStream(BufferedOutputStream(outputStream, STREAM_BUFFER_SIZE)).use { zos ->
            // Images (JPEG/PNG/WebP) are already compressed; BEST_SPEED or STORED avoids wasted CPU.
            // Using level 1 (BEST_SPEED) keeps high speed and valid standard deflate ZIP compatibility.
            zos.setLevel(Deflater.BEST_SPEED)

            for (file in sortedFiles) {
                checkCancelled()
                val entry = ZipEntry(file.name).apply {
                    time = file.lastModified()
                }
                zos.putNextEntry(entry)
                BufferedInputStream(FileInputStream(file), STREAM_BUFFER_SIZE).use { input ->
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
                val xmlEntry = ZipEntry("ComicInfo.xml")
                zos.putNextEntry(xmlEntry)
                zos.write(xmlBytes)
                zos.closeEntry()
            }
            zos.finish()
        }
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
