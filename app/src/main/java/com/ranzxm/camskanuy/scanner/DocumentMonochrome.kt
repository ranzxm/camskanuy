package com.ranzxm.camskanuy.scanner

import kotlin.math.max
import kotlin.math.roundToInt

/** Two-dimensional background estimation with soft edges, not one-bit thresholding. */
internal object DocumentMonochrome {
    // ponytail: document enhancement, not archival 1-bit output; add a separate binary export if required.
    fun render(gray: ByteArray, width: Int, height: Int, writeRow: (Int, IntArray) -> Unit) {
        require(width > 0 && height > 0 && width.toLong() * height == gray.size.toLong())
        val radius = (max(width, height) / 32).coerceAtLeast(3)
        val columns = IntArray(width)
        val row = IntArray(width)
        var top = 0
        var bottom = -1
        for (y in 0 until height) {
            val nextBottom = (y + radius).coerceAtMost(height - 1)
            while (bottom < nextBottom) {
                bottom++
                for (x in 0 until width) columns[x] += gray[bottom * width + x].toInt() and 255
            }
            val nextTop = (y - radius).coerceAtLeast(0)
            while (top < nextTop) {
                for (x in 0 until width) columns[x] -= gray[top * width + x].toInt() and 255
                top++
            }
            var sum = 0L
            var left = 0
            var right = -1
            for (x in 0 until width) {
                val nextRight = (x + radius).coerceAtMost(width - 1)
                while (right < nextRight) sum += columns[++right]
                val nextLeft = (x - radius).coerceAtLeast(0)
                while (left < nextLeft) sum -= columns[left++]
                val mean = sum.toFloat() / ((right - left + 1) * (bottom - top + 1))
                val threshold = max(45f, mean * 0.85f)
                val luminance = gray[y * width + x].toInt() and 255
                val t = ((luminance - threshold + 18f) / 36f).coerceIn(0f, 1f)
                val value = (255f * t * t * (3f - 2f * t)).roundToInt()
                row[x] = (255 shl 24) or (value shl 16) or (value shl 8) or value
            }
            writeRow(y, row)
        }
    }
}
