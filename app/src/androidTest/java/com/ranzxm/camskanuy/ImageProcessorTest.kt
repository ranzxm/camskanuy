package com.ranzxm.camskanuy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ranzxm.camskanuy.scanner.ImageProcessor
import com.ranzxm.camskanuy.scanner.ScanCrop
import com.ranzxm.camskanuy.scanner.ScanFilter
import com.ranzxm.camskanuy.scanner.ScanRotation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageProcessorTest {
    @Test
    fun exifOrientationAndAllFiltersProduceBoundedOutput() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val processor = ImageProcessor(context)
        val source = processor.newCaptureFile()
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawRect(30f, 80f, 210f, 100f, Paint().apply { color = Color.BLACK })
            }
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally {
            bitmap.recycle()
        }
        try {
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val loaded = processor.loadPreview(source, detect = false)
            try {
                assertEquals(320, loaded.bitmap.width)
                assertEquals(240, loaded.bitmap.height)
            } finally {
                loaded.bitmap.recycle()
            }
            for (filter in ScanFilter.entries) {
                val result = processor.render(source, ScanCrop.FULL, ScanRotation.R90, filter)
                try {
                    val output = requireNotNull(BitmapFactory.decodeFile(result.image.path))
                    try {
                        assertTrue(output.height > output.width)
                        assertTrue(maxOf(output.width, output.height) <= 2400)
                        if (filter == ScanFilter.GRAYSCALE || filter == ScanFilter.BLACK_WHITE) {
                            val color = output.getPixel(output.width / 2, output.height / 2)
                            assertTrue(kotlin.math.abs(Color.red(color) - Color.green(color)) <= 2)
                            assertTrue(kotlin.math.abs(Color.green(color) - Color.blue(color)) <= 2)
                        }
                    } finally {
                        output.recycle()
                    }
                    assertTrue(result.thumbnail.length() > 0)
                } finally {
                    processor.deleteTemporary(result.image, result.thumbnail)
                }
            }
        } finally {
            processor.deleteTemporary(source)
        }
    }
}
