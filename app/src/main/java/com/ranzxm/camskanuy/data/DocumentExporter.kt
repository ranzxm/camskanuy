package com.ranzxm.camskanuy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Rendered page files already contain the scanner's crop, rotation and filter.
 * Exports strip source metadata and use UUID filenames, never document names.
 * Completed exports are retained for at least 24 hours to allow delayed share reads.
 * Android itself can evict cache files under storage pressure.
 */
class DocumentExporter(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val files = PrivateFiles(context)

    suspend fun createPdf(document: DocumentWithPages): File = export("pdf") { output ->
        require(document.pages.isNotEmpty()) { "Cannot export an empty document" }
        require(document.pages.all { it.documentId == document.document.id }) { "Invalid document pages" }
        PdfDocument().use { pdf ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            document.sortedPages.forEachIndexed { index, page ->
                currentCoroutineContext().ensureActive()
                val bitmap = decodePage(page)
                try {
                    // A4's long edge in points, retaining the cropped image aspect ratio.
                    val scale = PDF_LONG_EDGE_POINTS / max(bitmap.width, bitmap.height).toFloat()
                    val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
                    val pdfPage = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                    try {
                        pdfPage.canvas.drawColor(Color.WHITE)
                        pdfPage.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
                    } finally {
                        pdf.finishPage(pdfPage)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            currentCoroutineContext().ensureActive()
            FileOutputStream(output).use { stream ->
                pdf.writeTo(stream)
                stream.fd.sync()
            }
        }
    }

    suspend fun createJpg(page: PageEntity): File = export("jpg") { output ->
        val bitmap = decodePage(page)
        try {
            if (bitmap.hasAlpha()) {
                Canvas(bitmap).drawColor(Color.WHITE, PorterDuff.Mode.DST_OVER)
            }
            currentCoroutineContext().ensureActive()
            FileOutputStream(output).use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                    throw IOException("Cannot encode JPG export")
                }
                stream.fd.sync()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** A failing/canceled provider write may leave a partial destination; local exports stay intact. */
    suspend fun writeToUri(file: File, uri: Uri): Unit = serialized {
        require(uri.scheme == "content") { "Export requires a system document URI" }
        val source = files.sharedFile(file.absolutePath)
        require(source.isFile && source.length() > 0) { "Export file is unavailable" }
        currentCoroutineContext().ensureActive()
        source.inputStream().use { input ->
            val output = resolver.openOutputStream(uri, "wt")
                ?: throw IOException("Cannot open export destination")
            output.use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    stream.write(buffer, 0, count)
                }
                stream.flush()
                currentCoroutineContext().ensureActive()
            }
        }
    }

    private suspend fun export(extension: String, write: suspend (File) -> Unit): File = serialized {
        files.cleanupExpiredExports()
        val output = files.newExport(extension)
        check(output.createNewFile()) { "Export already exists" }
        var complete = false
        try {
            write(output)
            currentCoroutineContext().ensureActive()
            if (output.length() == 0L) throw IOException("Empty export")
            complete = true
            output
        } finally {
            if (!complete) files.cleanupExport(output)
        }
    }

    private fun decodePage(page: PageEntity): Bitmap {
        val source = files.documentFile(page.documentId, page.processedPath)
        if (!source.isFile) throw IOException("Page output is unavailable")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth > 65_535 || bounds.outHeight > 65_535 ||
            bounds.outWidth.toLong() * bounds.outHeight > 200_000_000L
        ) {
            throw IOException("Unsupported or corrupt page image")
        }
        var sample = 1
        while ((max(bounds.outWidth, bounds.outHeight) + sample - 1) / sample > MAX_IMAGE_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeFile(source.absolutePath, options)
            ?: throw IOException("Cannot decode page image")
        if (max(bitmap.width, bitmap.height) <= MAX_IMAGE_DIMENSION) return bitmap
        // Some image codecs round sample sizes differently; enforce the memory/output bound.
        try {
            val scale = MAX_IMAGE_DIMENSION.toFloat() / max(bitmap.width, bitmap.height)
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
            if (scaled.isMutable) return scaled
            try {
                return scaled.copy(Bitmap.Config.ARGB_8888, true)
                    ?: throw IOException("Cannot prepare page image")
            } finally {
                scaled.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun <T> serialized(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        DocumentStorageLock.mutex.withLock { block() }
    }

    private companion object {
        const val MAX_IMAGE_DIMENSION = 2400
        const val PDF_LONG_EDGE_POINTS = 842f
    }
}
