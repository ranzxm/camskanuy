package com.ranzxm.camskanuy.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentMonochromeTest {
    @Test fun shadedNoisyPaperBecomesWhiteWhileInkRemainsDark() {
        val width = 96
        val height = 96
        val gray = ByteArray(width * height) { i ->
            (150 + (i / width) * 80 / height + (i % 5) - 2).toByte()
        }
        for (y in 12..83) for (x in 45..47) gray[y * width + x] = 25
        val output = render(gray, width, height)
        for (y in 0 until height) assertEquals(255, output[y * width + 10])
        assertEquals(0, output[48 * width + 46])
    }

    @Test fun antialiasedInkEdgesRetainIntermediateGrayLevels() {
        val gray = ByteArray(64 * 64) { 240.toByte() }
        gray[32 * 64 + 30] = 20
        gray[32 * 64 + 31] = 195.toByte()
        val output = render(gray, 64, 64)
        assertEquals(0, output[32 * 64 + 30])
        assertTrue(output[32 * 64 + 31] in 1..254)
        assertEquals(255, output[32 * 64 + 32])
    }

    @Test fun horizontalAndVerticalLinesReceiveEquivalentTreatment() {
        val gray = ByteArray(48 * 48) { 225.toByte() }
        for (x in 8..39) gray[24 * 48 + x] = 170.toByte()
        val transposed = ByteArray(gray.size) { i -> gray[(i % 48) * 48 + i / 48] }
        val horizontal = render(gray, 48, 48)
        val vertical = render(transposed, 48, 48)
        for (y in 0 until 48) for (x in 0 until 48) {
            assertEquals(horizontal[y * 48 + x], vertical[x * 48 + y])
        }
    }

    @Test fun tinyImageHasValidBorders() {
        assertEquals(255, render(byteArrayOf(220.toByte()), 1, 1).single())
    }

    private fun render(gray: ByteArray, width: Int, height: Int): IntArray {
        val result = IntArray(gray.size)
        DocumentMonochrome.render(gray, width, height) { y, row ->
            for (x in row.indices) result[y * width + x] = row[x] and 255
        }
        return result
    }
}
