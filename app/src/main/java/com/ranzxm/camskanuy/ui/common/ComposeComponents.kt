package com.ranzxm.camskanuy.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ranzxm.camskanuy.R
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val imageDecoders = Semaphore(2)

private sealed interface DocumentImageState {
    data object Loading : DocumentImageState
    data object Missing : DocumentImageState
    data class Ready(val bitmap: Bitmap) : DocumentImageState
}

/** Two concurrent, downsampled decodes; lifecycle cancellation never publishes a stale image. */
// ponytail: previews stop at 2048 px; add tiled decoding only if full-resolution zoom is required.
@Composable
fun AsyncDocumentImage(
    path: String?,
    modifier: Modifier = Modifier,
    description: String? = null,
    maxSize: Int = 1200,
    zoomable: Boolean = false,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val limit = maxSize.coerceIn(64, 2048)
    key(path, limit, lifecycle) {
        val state by produceState<DocumentImageState>(DocumentImageState.Loading) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (value !is DocumentImageState.Loading) return@repeatOnLifecycle
                val bitmap = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
                    imageDecoders.withPermit {
                        ensureActive()
                        val decoded = decodeDocumentImage(path, limit)
                        try {
                            ensureActive()
                            decoded
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            decoded?.recycle()
                            throw cancelled
                        }
                    }
                }
                value = bitmap?.let { DocumentImageState.Ready(it) } ?: DocumentImageState.Missing
            }
        }
        Box(modifier, contentAlignment = Alignment.Center) {
            when (val loaded = state) {
                DocumentImageState.Loading -> CircularProgressIndicator(
                    Modifier.size(24.dp), strokeWidth = 2.dp,
                )
                DocumentImageState.Missing -> Column(
                    Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.BrokenImage,
                        contentDescription = stringResource(R.string.doc_image_error),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (limit > 320) Text(
                        stringResource(R.string.doc_image_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                is DocumentImageState.Ready -> if (zoomable) {
                    var zoomView by remember { mutableStateOf<DocumentZoomView?>(null) }
                    DisposableEffect(loaded.bitmap) { onDispose { zoomView?.setImageDrawable(null) } }
                    AndroidView(factory = { context -> DocumentZoomView(context).also { view ->
                        view.setImageBitmap(loaded.bitmap)
                        view.contentDescription = description
                        zoomView = view
                    } }, modifier = Modifier.fillMaxSize())
                    Row(Modifier.align(Alignment.BottomCenter)) {
                        IconButton(onClick = { zoomView?.changeZoom(1 / 1.5f) }) {
                            Icon(Icons.Outlined.ZoomOut, stringResource(R.string.doc_zoom_out))
                        }
                        IconButton(onClick = { zoomView?.resetZoom() }) {
                            Icon(Icons.Outlined.CenterFocusStrong, stringResource(R.string.doc_zoom_reset))
                        }
                        IconButton(onClick = { zoomView?.changeZoom(1.5f) }) {
                            Icon(Icons.Outlined.ZoomIn, stringResource(R.string.doc_zoom_in))
                        }
                    }
                } else Image(
                    bitmap = remember(loaded.bitmap) { loaded.bitmap.asImageBitmap() },
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private fun decodeDocumentImage(path: String, maxSize: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
        var sample = 1
        while ((bounds.outWidth.toLong() + sample - 1) / sample > maxSize ||
            (bounds.outHeight.toLong() + sample - 1) / sample > maxSize) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    }
} catch (_: IOException) {
    null
} catch (_: SecurityException) {
    null
} catch (_: IllegalArgumentException) {
    null
}
