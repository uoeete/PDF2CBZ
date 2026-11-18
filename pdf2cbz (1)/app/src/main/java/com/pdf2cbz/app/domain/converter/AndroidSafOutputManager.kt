package com.pdf2cbz.app.domain.converter

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.pdf2cbz.app.data.model.DuplicatePolicy
import com.pdf2cbz.app.domain.util.FilenameUtils
import java.io.OutputStream

class AndroidSafOutputTarget(
    private val context: Context,
    private val treeUri: Uri
) : CbzOutputTarget {

    private val rootDir: DocumentFile by lazy {
        DocumentFile.fromTreeUri(context, treeUri)?.takeIf { it.exists() && it.canWrite() }
            ?: throw PdfConversionException.OutputUnavailable("保存先フォルダへの書き込み権限がありません。")
    }

    private val createdFiles = mutableMapOf<String, DocumentFile>()

    override fun resolveDestination(
        desiredCbzName: String,
        policy: DuplicatePolicy
    ): ResolvedDestination {
        val existingFiles = rootDir.listFiles()
        val existingByName = existingFiles.associateBy { it.name?.lowercase().orEmpty() }
        val lowerDesired = desiredCbzName.lowercase()

        return when (policy) {
            DuplicatePolicy.SKIP -> {
                if (existingByName.containsKey(lowerDesired)) {
                    ResolvedDestination.SkipExisting(desiredCbzName)
                } else {
                    createTarget(desiredCbzName)
                }
            }
            DuplicatePolicy.OVERWRITE -> {
                existingByName[lowerDesired]?.delete()
                createTarget(desiredCbzName)
            }
            DuplicatePolicy.RENAME -> {
                val existingNames = existingFiles.mapNotNull { it.name }.toSet()
                val uniqueName = FilenameUtils.resolveUniqueCbzName(desiredCbzName, existingNames)
                createTarget(uniqueName)
            }
        }
    }

    private fun createTarget(fileName: String): ResolvedDestination.WriteTo {
        val created = rootDir.createFile("application/vnd.comicbook+zip", fileName)
            ?: throw PdfConversionException.OutputUnavailable("出力ファイル $fileName を作成できませんでした。")
        val uriString = created.uri.toString()
        createdFiles[uriString] = created
        return ResolvedDestination.WriteTo(
            finalFileName = created.name ?: fileName,
            uriString = uriString
        )
    }

    override fun openOutputStream(destination: ResolvedDestination): OutputStream {
        val writeDest = destination as? ResolvedDestination.WriteTo
            ?: throw PdfConversionException.OutputUnavailable()
        val docUri = writeDest.uriString?.let(Uri::parse)
            ?: throw PdfConversionException.OutputUnavailable()
        return context.contentResolver.openOutputStream(docUri, "w")
            ?: throw PdfConversionException.OutputUnavailable("出力ストリームを開けませんでした。")
    }

    override fun querySize(destination: ResolvedDestination): Long {
        val writeDest = destination as? ResolvedDestination.WriteTo ?: return 0L
        val docUri = writeDest.uriString?.let(Uri::parse) ?: return 0L
        val doc = createdFiles[writeDest.uriString] ?: DocumentFile.fromSingleUri(context, docUri)
        return doc?.length() ?: 0L
    }

    override fun deleteIfBroken(destination: ResolvedDestination) {
        val writeDest = destination as? ResolvedDestination.WriteTo ?: return
        val doc = writeDest.uriString?.let { createdFiles[it] ?: DocumentFile.fromSingleUri(context, Uri.parse(it)) }
        runCatching { doc?.delete() }
    }
}

object SafMetadataHelper {
    data class SafFileInfo(
        val fileName: String,
        val sizeBytes: Long
    )

    fun queryPdfMetadata(context: Context, uri: Uri): SafFileInfo {
        var name = "document.pdf"
        var size = 0L

        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    name = cursor.getString(nameIndex)
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return SafFileInfo(fileName = name, sizeBytes = size)
    }

    fun getTreeDisplayName(context: Context, treeUri: Uri): String {
        val doc = DocumentFile.fromTreeUri(context, treeUri)
        return doc?.name ?: treeUri.lastPathSegment ?: "選択済みフォルダ"
    }
}
