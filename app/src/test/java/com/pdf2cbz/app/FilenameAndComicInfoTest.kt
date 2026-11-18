package com.pdf2cbz.app

import com.pdf2cbz.app.domain.util.ComicInfoXmlBuilder
import com.pdf2cbz.app.domain.util.FilenameUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameAndComicInfoTest {

    @Test
    fun pageNamesAreZeroPaddedAndOrdered() {
        assertEquals("0001.jpg", FilenameUtils.formatPageFileName(0, 1, "jpg"))
        assertEquals("0001.jpg", FilenameUtils.formatPageFileName(0, 120, "jpg"))
        assertEquals("0120.jpg", FilenameUtils.formatPageFileName(119, 120, "jpg"))
        assertEquals("0005.png", FilenameUtils.formatPageFileName(4, 24, "PNG"))
        assertEquals("0010.webp", FilenameUtils.formatPageFileName(9, 50, "webp"))
        // Over 9999 pages expands digit width cleanly so lexical order still holds
        assertEquals("00001.jpg", FilenameUtils.formatPageFileName(0, 10000, "jpg"))
        assertEquals("10000.jpg", FilenameUtils.formatPageFileName(9999, 10000, "jpg"))
    }

    @Test
    fun cbzNamesAreDerivedIndividuallyFromPdf() {
        assertEquals("manga01.cbz", FilenameUtils.toCbzFileName("manga01.pdf"))
        assertEquals("manga02.cbz", FilenameUtils.toCbzFileName("manga02.PDF"))
        assertEquals("One_Piece_v01_.cbz", FilenameUtils.toCbzFileName("One:Piece*v01?.pdf"))
    }

    @Test
    fun duplicateNamesResolveWithIncrementalSuffix() {
        val existing = setOf("manga01.cbz", "manga01 (1).cbz")
        assertEquals("manga01 (2).cbz", FilenameUtils.resolveUniqueCbzName("manga01.cbz", existing))
        assertEquals("manga02.cbz", FilenameUtils.resolveUniqueCbzName("manga02.cbz", existing))
    }

    @Test
    fun comicInfoXmlEscapesSpecialCharactersAndIncludesPageCount() {
        val xml = ComicInfoXmlBuilder.build(
            title = "Vol 1 & <Special>.cbz",
            pageCount = 148
        )
        assertTrue(xml.contains("<Title>Vol 1 &amp; &lt;Special&gt;</Title>"))
        assertTrue(xml.contains("<PageCount>148</PageCount>"))
        assertTrue(xml.contains("<Manga>Yes</Manga>"))
    }

    @Test
    fun durationFormattingCoversSubSecondAndMinutes() {
        assertEquals("0.0秒", FilenameUtils.formatDuration(0L))
        assertEquals("12.4秒", FilenameUtils.formatDuration(12_400L))
        assertEquals("2分5秒", FilenameUtils.formatDuration(125_000L))
    }
}
