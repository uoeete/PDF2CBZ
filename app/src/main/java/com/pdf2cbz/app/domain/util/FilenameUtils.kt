package com.pdf2cbz.app.domain.util

import java.util.Locale
import kotlin.math.max

object FilenameUtils {

    /**
     * Formats 1-based page numbers with zero-padding so comic readers (including Mihon)
     * sort pages strictly in reading order:
     * pageIndex = 0 -> 0001.jpg
     * pageIndex = 9999 -> 10000.jpg (automatically expands width above 9999 pages)
     */
    fun formatPageFileName(zeroBasedPageIndex: Int, totalPages: Int, extension: String): String {
        val pageNumber = zeroBasedPageIndex + 1
        val digits = max(4, totalPages.toString().length)
        val cleanExt = extension.lowercase(Locale.US).trimStart('.')
        return String.format(Locale.US, "%0${digits}d.%s", pageNumber, cleanExt)
    }

    /**
     * Derives the base CBZ filename from a PDF filename:
     * manga01.pdf -> manga01.cbz
     */
    fun toCbzFileName(pdfFileName: String): String {
        val sanitized = sanitizeFileName(pdfFileName)
        val base = if (sanitized.lowercase(Locale.US).endsWith(".pdf")) {
            sanitized.dropLast(4)
        } else {
            sanitized
        }.ifBlank { "manga" }
        return "$base.cbz"
    }

    /**
     * Generates a non-colliding filename given existing filenames in the target directory.
     */
    fun resolveUniqueCbzName(desiredFileName: String, existingNames: Set<String>): String {
        val lowerExisting = existingNames.mapTo(HashSet()) { it.lowercase(Locale.US) }
        if (desiredFileName.lowercase(Locale.US) !in lowerExisting) {
            return desiredFileName
        }
        val base = if (desiredFileName.lowercase(Locale.US).endsWith(".cbz")) {
            desiredFileName.dropLast(4)
        } else {
            desiredFileName
        }
        var counter = 1
        while (true) {
            val candidate = "$base ($counter).cbz"
            if (candidate.lowercase(Locale.US) !in lowerExisting) {
                return candidate
            }
            counter++
        }
    }

    fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim()
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes < 0) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < mb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            bytes < gb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            else -> String.format(Locale.US, "%.2f GB", bytes / gb)
        }
    }

    fun formatDuration(millis: Long): String {
        if (millis <= 0L) return "0.0秒"
        val seconds = millis / 1000.0
        return if (seconds < 60.0) {
            String.format(Locale.US, "%.1f秒", seconds)
        } else {
            val mins = (millis / 60000L)
            val remSec = (millis % 60000L) / 1000.0
            String.format(Locale.US, "%d分%.0f秒", mins, remSec)
        }
    }
}
