package com.ranzxm.camskanuy.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** No full-resolution decode, bitmap cache, or bitmap sharing between holders. */
object DocumentImages {
    private val decoders = Semaphore(2)

    fun load(scope: CoroutineScope, path: String?, target: ImageView, maxSize: Int = 300,
             finished: (Boolean) -> Unit = {}): Job {
        target.setImageDrawable(null)
        return scope.launch {
            val bitmap = if (path == null) null else withContext(Dispatchers.IO) {
                decoders.withPermit {
                    ensureActive()
                    decode(path, maxSize).also { ensureActive() }
                }
            }
            ensureActive()
            target.setImageBitmap(bitmap)
            finished(bitmap != null)
        }
    }

    private fun decode(path: String, maxSize: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            // Round up so even exceptionally large files stay bounded.
            while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            })
        } catch (_: IOException) { null } catch (_: SecurityException) { null } catch (_: IllegalArgumentException) { null }
    }
}
