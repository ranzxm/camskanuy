package com.ranzxm.camskanuy.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ImageProcessor(context: Context) {
    private val app = context.applicationContext
    private val directory get() = File(app.cacheDir, "scan-inputs")

    data class Preview(val bitmap: Bitmap, val crop: ScanCrop, val detected: Boolean)
    data class Rendered(val image: File, val thumbnail: File)

    suspend fun newCaptureFile(): File = withContext(Dispatchers.IO) { temporary("capture", ".jpg") }

    /** Copies provider bytes, preserving the source and its EXIF, with a bounded input size. */
    suspend fun importUri(uri: Uri): File = withContext(Dispatchers.IO) {
        if (uri.scheme != "content") throw IOException("Unsupported image URI")
        val output = temporary("import", ".image")
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                output.outputStream().buffered().use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    var size = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > 80L * 1024 * 1024) throw IOException("Input exceeds size limit")
                        stream.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("Image unavailable")
            // Validate now, before assigning this source to the editor.
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(output.path, options)
            validateBounds(options)
            currentCoroutineContext().ensureActive()
            output
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        }
    }

    suspend fun loadPreview(source: File, detect: Boolean = true): Preview = withContext(Dispatchers.Default) {
        val bitmap = decode(source, 1200)
        try {
            val crop = if (detect) detectCorners(bitmap) else null
            currentCoroutineContext().ensureActive()
            Preview(bitmap, crop ?: ScanCrop.FULL, crop != null)
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    suspend fun preview(source: File, crop: ScanCrop, rotation: ScanRotation, filter: ScanFilter, maxSide: Int = 1200): Bitmap =
        withContext(Dispatchers.Default) { transform(source, crop, rotation, filter, maxSide.coerceIn(64, 1200)) }

    suspend fun render(source: File, crop: ScanCrop, rotation: ScanRotation, filter: ScanFilter): Rendered =
        withContext(Dispatchers.IO) {
            val image = temporary("render", ".jpg")
            var thumbnail: File? = null
            try {
                thumbnail = temporary("thumb", ".jpg")
                val bitmap = transform(source, crop, rotation, filter, 2400)
                try {
                    writeJpeg(bitmap, image, 94)
                    currentCoroutineContext().ensureActive()
                    val thumb = fit(bitmap, 360)
                    try { writeJpeg(thumb, requireNotNull(thumbnail), 85) }
                    finally { if (thumb !== bitmap) thumb.recycle() }
                } finally { bitmap.recycle() }
                currentCoroutineContext().ensureActive()
                Rendered(image, requireNotNull(thumbnail))
            } catch (failure: Throwable) {
                image.delete()
                thumbnail?.delete()
                throw failure
            }
        }

    /** Only scanner-owned cache files can be removed; persisted originals are never touched. */
    suspend fun deleteTemporary(vararg files: File?) = withContext(Dispatchers.IO) {
        files.filterNotNull().forEach { file ->
            if (file.canonicalFile.parentFile == directory.canonicalFile && file.exists() && !file.delete()) {
                throw IOException("Temporary cleanup failed")
            }
        }
    }

    fun isTemporary(file: File): Boolean = file.absoluteFile.parentFile == directory.absoluteFile

    private fun temporary(prefix: String, suffix: String): File {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cache unavailable")
        return File.createTempFile(prefix, suffix, directory)
    }

    private suspend fun decode(file: File, maxSide: Int): Bitmap {
        currentCoroutineContext().ensureActive()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        validateBounds(options)
        var sample = 1
        // Decode at or below the memory bound, including non-power-of-two source dimensions.
        while (max(options.outWidth, options.outHeight) / sample > maxSide) sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        options.inMutable = true
        val original = BitmapFactory.decodeFile(file.path, options) ?: throw IOException("Invalid image")
        try {
            val exif = ExifInterface(file)
            val matrix = Matrix().apply {
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            currentCoroutineContext().ensureActive()
            if (matrix.isIdentity) return original
            val oriented = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            if (oriented !== original) original.recycle()
            return oriented
        } catch (failure: Throwable) {
            original.recycle()
            throw failure
        }
    }

    private fun validateBounds(options: BitmapFactory.Options) {
        if (options.outWidth <= 0 || options.outHeight <= 0 ||
            options.outWidth > 65_535 || options.outHeight > 65_535 ||
            options.outWidth.toLong() * options.outHeight > 200_000_000L) throw IOException("Invalid image dimensions")
    }

    private suspend fun detectCorners(bitmap: Bitmap): ScanCrop? {
        val small = fit(bitmap, 320)
        try {
            val pixels = IntArray(small.width * small.height)
            small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
            for (i in pixels.indices) pixels[i] = luminance(pixels[i])
            val context = currentCoroutineContext()
            return DocumentEdgeDetector.detect(pixels, small.width, small.height) { context.ensureActive() }
        } finally { if (small !== bitmap) small.recycle() }
    }

    private suspend fun transform(source: File, crop: ScanCrop, rotation: ScanRotation, filter: ScanFilter, limit: Int): Bitmap {
        require(crop.isValid()) { "Invalid crop" }
        val original = decode(source, limit)
        var output: Bitmap? = null
        try {
            val points = crop.points.map { ScanPoint(it.x * (original.width - 1), it.y * (original.height - 1)) }
            fun distance(a: Int, b: Int) = hypot(points[a].x - points[b].x, points[a].y - points[b].y)
            val width = max(distance(0, 1), distance(3, 2)).roundToInt().coerceIn(2, limit)
            val height = max(distance(0, 3), distance(1, 2)).roundToInt().coerceIn(2, limit)
            val matrix = Matrix()
            val valid = matrix.setPolyToPoly(points.flatMap { listOf(it.x, it.y) }.toFloatArray(), 0,
                floatArrayOf(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat(), 0f, height.toFloat()), 0, 4)
            if (!valid) throw IOException("Invalid perspective")
            val cropped = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            output = cropped
            Canvas(cropped).apply {
                drawColor(Color.WHITE)
                drawBitmap(original, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            original.recycle()
            currentCoroutineContext().ensureActive()
            if (rotation != ScanRotation.R0) {
                val rotated = Bitmap.createBitmap(cropped, 0, 0, width, height,
                    Matrix().apply { setRotate(rotation.degrees.toFloat()) }, true)
                output = if (rotated.isMutable) rotated else {
                    val mutable = rotated.copy(Bitmap.Config.ARGB_8888, true) ?: throw IOException("Image allocation failed")
                    if (rotated !== cropped) rotated.recycle()
                    mutable
                }
                if (rotated !== cropped) cropped.recycle()
            }
            val result = requireNotNull(output)
            applyFilter(result, filter)
            currentCoroutineContext().ensureActive()
            return result
        } catch (failure: Throwable) {
            output?.recycle()
            throw failure
        } finally { if (!original.isRecycled) original.recycle() }
    }

    private suspend fun applyFilter(bitmap: Bitmap, filter: ScanFilter) {
        if (filter == ScanFilter.ORIGINAL) return
        val row = IntArray(bitmap.width)
        if (filter == ScanFilter.BLACK_WHITE) {
            val gray = ByteArray(bitmap.width * bitmap.height)
            for (y in 0 until bitmap.height) {
                currentCoroutineContext().ensureActive()
                bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                for (x in row.indices) gray[y * bitmap.width + x] = luminance(row[x]).toByte()
            }
            val context = currentCoroutineContext()
            DocumentMonochrome.render(gray, bitmap.width, bitmap.height) { y, output ->
                context.ensureActive()
                bitmap.setPixels(output, 0, bitmap.width, 0, y, bitmap.width, 1)
            }
            return
        }
        for (y in 0 until bitmap.height) {
            currentCoroutineContext().ensureActive()
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                val c = row[x]
                row[x] = when (filter) {
                    ScanFilter.COLOR -> Color.rgb(enhance(Color.red(c)), enhance(Color.green(c)), enhance(Color.blue(c)))
                    ScanFilter.GRAYSCALE -> luminance(c).let { Color.rgb(it, it, it) }
                    ScanFilter.ORIGINAL, ScanFilter.BLACK_WHITE -> c
                }
            }
            bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
    }

    private fun enhance(value: Int): Int = ((value - 128) * 1.12f + 134).roundToInt().coerceIn(0, 255)
    private fun luminance(color: Int): Int = (77 * Color.red(color) + 150 * Color.green(color) + 29 * Color.blue(color)) shr 8

    private fun fit(bitmap: Bitmap, side: Int): Bitmap {
        val scale = side.toFloat() / max(bitmap.width, bitmap.height)
        if (scale >= 1f) return bitmap
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
    }

    private fun writeJpeg(bitmap: Bitmap, file: File, quality: Int) {
        file.outputStream().buffered().use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) throw IOException("Encoding failed")
        }
    }
}
