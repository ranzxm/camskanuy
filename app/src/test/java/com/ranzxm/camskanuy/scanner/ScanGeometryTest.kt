package com.ranzxm.camskanuy.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanGeometryTest {
    @Test fun cropSerializationPreservesSourceCoordinates() {
        val crop = ScanCrop(listOf(ScanPoint(.1f, .2f), ScanPoint(.8f, .1f), ScanPoint(.9f, .9f), ScanPoint(.2f, .8f)))
        assertTrue(crop.isValid())
        assertEquals(crop, ScanCrop.decode(crop.encode()))
        assertEquals(ScanRotation.R0, ScanRotation.R270.next())
        assertEquals(ScanRotation.R270, ScanRotation.fromDegrees(-90))
    }

    @Test fun crossingConcaveAndNonFiniteCropsAreRejected() {
        assertFalse(ScanCrop(listOf(ScanPoint(0f, 0f), ScanPoint(1f, 1f), ScanPoint(1f, 0f), ScanPoint(0f, 1f))).isValid())
        assertFalse(ScanCrop(listOf(ScanPoint(0f, 0f), ScanPoint(1f, 0f), ScanPoint(.2f, .2f), ScanPoint(0f, 1f))).isValid())
        assertNull(ScanCrop.decode("NaN,0;1,0;1,1;0,1"))
        assertNull(ScanCrop.decode("0,0;1,0;1,1"))
        assertNull(ScanCrop.decode("-1,0;1,0;1,1;0,1"))
        assertEquals(ScanCrop.FULL, ScanCrop.FULL.move(0, 1f, 1f))
    }

    @Test fun uniformImageFallsBackToManualInsteadOfInventingCorners() {
        assertNull(DocumentEdgeDetector.detect(IntArray(100 * 120) { 180 }, 100, 120))
    }

    @Test fun detectsBrightDocumentAndPreservesPerspective() {
        val w = 160
        val h = 180
        val gray = IntArray(w * h) { 35 }
        // A trapezoid with an unmistakable boundary and dark "printing" holes.
        for (y in 25..155) {
            val left = 28 - (y - 25) / 13
            val right = 130 + (y - 25) / 26
            for (x in left..right) gray[y * w + x] = 230
        }
        for (y in 55..125 step 12) for (x in 45..110) gray[y * w + x] = 50
        val crop = DocumentEdgeDetector.detect(gray, w, h)
        assertNotNull(crop)
        val detected = requireNotNull(crop)
        assertTrue(detected.isValid())
        assertTrue(detected.area() in .4f.. .75f)
        assertTrue(detected.points[0].x > detected.points[3].x)
        assertEquals(25f / (h - 1), detected.points[0].y, .03f)
        assertEquals(155f / (h - 1), detected.points[2].y, .03f)
    }

    @Test fun detectsDarkDocumentAgainstBrightBackground() {
        val w = 120
        val h = 140
        val gray = IntArray(w * h) { 230 }
        for (y in 20..120) for (x in 20..100) gray[y * w + x] = 55
        val crop = requireNotNull(DocumentEdgeDetector.detect(gray, w, h))
        assertTrue(crop.area() in .4f.. .65f)
        assertEquals(20f / (w - 1), crop.points[0].x, .04f)
    }

    @Test fun tinyBrightLogoIsNotMistakenForDocument() {
        val gray = IntArray(100 * 100) { 30 }
        for (y in 40..50) for (x in 40..50) gray[y * 100 + x] = 250
        assertNull(DocumentEdgeDetector.detect(gray, 100, 100))
    }
}
