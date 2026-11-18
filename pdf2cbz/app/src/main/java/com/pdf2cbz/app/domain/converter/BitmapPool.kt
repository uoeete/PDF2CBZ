package com.pdf2cbz.app.domain.converter

import android.graphics.Bitmap

/**
 * Thread-safe, byte-budgeted mutable Bitmap pool for PdfRenderer page rendering.
 * Eliminates repetitive multi-megabyte Ashmem/native allocations across 200-500 page volumes
 * where consecutive manga pages share identical dimensions.
 */
class BitmapPool(
    private val maxTotalBytes: Long = calculateDefaultBudgetBytes()
) {
    private data class Key(val width: Int, val height: Int, val config: Bitmap.Config)

    private val lock = Any()
    private val buckets = HashMap<Key, ArrayDeque<Bitmap>>()
    private val lruOrder = ArrayDeque<Key>()
    private var currentBytes: Long = 0L

    fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        val key = Key(width, height, config)
        synchronized(lock) {
            val deque = buckets[key]
            while (deque != null && deque.isNotEmpty()) {
                val candidate = deque.removeLast()
                currentBytes -= candidate.allocationByteCountCompat()
                if (deque.isEmpty()) {
                    buckets.remove(key)
                    lruOrder.remove(key)
                }
                if (!candidate.isRecycled && candidate.isMutable) {
                    return candidate
                }
            }
        }
        return Bitmap.createBitmap(width, height, config)
    }

    fun release(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        if (!bitmap.isMutable) {
            bitmap.recycle()
            return
        }
        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val byteSize = bitmap.allocationByteCountCompat()
        if (byteSize > maxTotalBytes) {
            bitmap.recycle()
            return
        }

        val key = Key(bitmap.width, bitmap.height, config)
        synchronized(lock) {
            while (currentBytes + byteSize > maxTotalBytes && lruOrder.isNotEmpty()) {
                val oldestKey = lruOrder.removeFirst()
                val oldestDeque = buckets[oldestKey]
                val evicted = oldestDeque?.removeFirstOrNull()
                if (evicted != null) {
                    currentBytes -= evicted.allocationByteCountCompat()
                    if (!evicted.isRecycled) {
                        evicted.recycle()
                    }
                }
                if (oldestDeque == null || oldestDeque.isEmpty()) {
                    buckets.remove(oldestKey)
                } else {
                    lruOrder.addLast(oldestKey)
                }
            }

            val deque = buckets.getOrPut(key) { ArrayDeque() }
            deque.addLast(bitmap)
            if (!lruOrder.contains(key)) {
                lruOrder.addLast(key)
            }
            currentBytes += byteSize
        }
    }

    fun clear() {
        synchronized(lock) {
            for (deque in buckets.values) {
                for (bmp in deque) {
                    if (!bmp.isRecycled) {
                        bmp.recycle()
                    }
                }
                deque.clear()
            }
            buckets.clear()
            lruOrder.clear()
            currentBytes = 0L
        }
    }

    private fun Bitmap.allocationByteCountCompat(): Long {
        return allocationByteCount.toLong().coerceAtLeast(width.toLong() * height.toLong() * 4L)
    }

    companion object {
        private fun calculateDefaultBudgetBytes(): Long {
            val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)
            return when {
                maxHeapMb >= 512L -> 64L * 1024L * 1024L
                maxHeapMb >= 256L -> 36L * 1024L * 1024L
                else -> 20L * 1024L * 1024L
            }
        }
    }
}
