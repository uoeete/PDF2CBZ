package com.pdf2cbz.app.domain.util

import android.os.StatFs
import java.io.File

interface StorageSpaceChecker {
    fun getAvailableBytes(path: File): Long
    fun hasEnoughSpace(path: File, requiredBytes: Long): Boolean
}

class AndroidStorageSpaceChecker(
    private val minimumSafetyBufferBytes: Long = 32L * 1024L * 1024L // 32 MB safety headroom
) : StorageSpaceChecker {

    override fun getAvailableBytes(path: File): Long {
        val target = if (path.exists()) path else path.parentFile ?: path
        return runCatching {
            val stat = StatFs(target.absolutePath)
            stat.availableBytes
        }.getOrElse { 0L }
    }

    override fun hasEnoughSpace(path: File, requiredBytes: Long): Boolean {
        val available = getAvailableBytes(path)
        return available >= (requiredBytes + minimumSafetyBufferBytes)
    }
}
