package com.pdf2cbz.app.domain.util

object ComicInfoXmlBuilder {

    fun build(
        title: String,
        pageCount: Int,
        notes: String = "Converted by PDF2CBZ"
    ): String {
        val safeTitle = escapeXml(title.removeSuffix(".cbz").removeSuffix(".CBZ"))
        val safeNotes = escapeXml(notes)
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            append("<ComicInfo xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
            append("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n")
            append("  <Title>$safeTitle</Title>\n")
            append("  <PageCount>$pageCount</PageCount>\n")
            append("  <Notes>$safeNotes</Notes>\n")
            append("  <Manga>Yes</Manga>\n")
            append("</ComicInfo>\n")
        }
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
