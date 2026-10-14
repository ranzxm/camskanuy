package com.ranzxm.camskanuy.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ranzxm.camskanuy.CamskanuyApp
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.scanner.ImageProcessor
import com.ranzxm.camskanuy.scanner.ScanCrop
import com.ranzxm.camskanuy.scanner.ScanFilter
import com.ranzxm.camskanuy.scanner.ScanRotation
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val previewLoading: Boolean = false,
    val original: Bitmap? = null,
    val preview: Bitmap? = null,
    val filterThumbnails: List<Bitmap> = emptyList(),
    val crop: ScanCrop = ScanCrop.FULL,
    val rotation: ScanRotation = ScanRotation.R0,
    val filter: ScanFilter = ScanFilter.ORIGINAL,
    val cropMode: Boolean = true,
    val showOriginal: Boolean = false,
    val corner: Int = 0,
    val position: Int = 1,
    val total: Int = 1,
    val manual: Boolean = false,
    val error: Int? = null,
    val dirty: Boolean = false,
    val exit: Exit = Exit.NONE,
) {
    enum class Exit { NONE, PAGES, CANCEL }
    val busy: Boolean get() = loading || saving
}

/** Owns the review queue and temporary source independently of the fragment's view lifetime. */
class EditorViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as CamskanuyApp
    private val processor = app.imageProcessor
    private val repository = app.repository
    val documentId: String = saved.get<String>("documentId").orEmpty()
    val pageId: String? = saved["pageId"]
    private val queue: List<String> = saved.get<ArrayList<String>>("inputUris")?.toList().orEmpty()
    private var index: Int
        get() = saved.get<Int>("queueIndex") ?: 0
        set(value) { saved["queueIndex"] = value }
    private var source: File? = saved.get<String>("workingSource")?.let(::File)
    private val mutableState = MutableStateFlow(EditorState(total = queue.size.coerceAtLeast(1)))
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var previewJob: Job? = null
    private var actionJob: Job? = null

    init {
        if (saved.get<Boolean>("completed") == true) {
            mutableState.value = mutableState.value.copy(loading = false, exit = EditorState.Exit.PAGES)
        } else load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            previewJob?.cancelAndJoin()
            mutableState.update { it.copy(loading = true, error = null, original = null, preview = null,
                filterThumbnails = emptyList(), position = index + 1) }
            try {
                if (documentId.isBlank() || repository.getDocument(documentId) == null) {
                    mutableState.update { it.copy(loading = false, error = R.string.scan_missing_document) }
                    return@launch
                }
                val existing = pageId?.let { repository.getPage(it) }
                if (pageId != null && (existing == null || existing.documentId != documentId)) {
                    mutableState.update { it.copy(loading = false, error = R.string.scan_load_failed) }
                    return@launch
                }
                if (source == null) {
                    source = when {
                        existing != null -> File(existing.originalPath)
                        queue.isNotEmpty() -> processor.importUri(Uri.parse(queue[index]))
                        else -> saved.get<String>("sourcePath")?.let(::File)
                    }
                    saved["workingSource"] = source?.absolutePath
                }
                val input = requireNotNull(source)
                if (existing == null) require(processor.isTemporary(input))
                val storedCrop = ScanCrop.decode(saved["currentCrop"])
                    ?: existing?.let { ScanCrop.decode(it.crop) }
                val loaded = processor.loadPreview(input, detect = storedCrop == null)
                val crop = storedCrop ?: loaded.crop
                val rotation = saved.get<String>("currentRotation")?.let { name -> ScanRotation.entries.find { it.name == name } }
                    ?: ScanRotation.fromDegrees(existing?.rotation ?: 0)
                val filter = saved.get<String>("currentFilter")?.let { name -> ScanFilter.entries.find { it.name == name } }
                    ?: ScanFilter.entries.find { it.name == existing?.filter } ?: ScanFilter.ORIGINAL
                mutableState.update { it.copy(loading = false, original = loaded.bitmap, crop = crop, rotation = rotation,
                    filter = filter, manual = saved.get<Boolean>("manualCrop") ?: (storedCrop == null && !loaded.detected),
                    cropMode = saved.get<Boolean>("cropMode") ?: true, showOriginal = saved.get<Boolean>("showOriginal") ?: false,
                    corner = (saved.get<Int>("corner") ?: 0).coerceIn(0, 3),
                    dirty = saved.get<Boolean>("dirty") ?: (pageId == null)) }
                persistParameters()
                if (!state.value.cropMode) refreshPreview(thumbnails = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = R.string.scan_load_failed) }
            } catch (_: OutOfMemoryError) {
                mutableState.update { it.copy(loading = false, error = R.string.scan_load_failed) }
            }
        }
    }

    fun setCrop(crop: ScanCrop) {
        if (state.value.busy || !crop.isValid() || crop == state.value.crop) return
        mutableState.update { it.copy(crop = crop, dirty = true, preview = null, filterThumbnails = emptyList(), error = null) }
        persistParameters()
    }

    fun selectCorner(index: Int) {
        mutableState.update { it.copy(corner = index.coerceIn(0, 3)) }
        saved["corner"] = state.value.corner
    }

    fun moveCorner(dx: Float, dy: Float) {
        val current = state.value
        val point = current.crop.points[current.corner]
        setCrop(current.crop.move(current.corner, point.x + dx, point.y + dy))
    }

    fun setCropMode(enabled: Boolean) {
        if (state.value.busy || enabled == state.value.cropMode) return
        mutableState.update { it.copy(cropMode = enabled, showOriginal = false) }
        saved["cropMode"] = enabled
        saved["showOriginal"] = false
        if (!enabled) refreshPreview(thumbnails = true)
        else {
            previewJob?.cancel()
            mutableState.update { it.copy(previewLoading = false) }
        }
    }

    fun rotate() {
        if (state.value.busy) return
        mutableState.update { it.copy(rotation = it.rotation.next(), dirty = true, cropMode = false,
            showOriginal = false, filterThumbnails = emptyList()) }
        saved["cropMode"] = false
        saved["showOriginal"] = false
        persistParameters()
        refreshPreview(thumbnails = true)
    }

    fun setFilter(filter: ScanFilter) {
        if (state.value.busy || state.value.filter == filter) return
        mutableState.update { it.copy(filter = filter, dirty = true, showOriginal = false) }
        saved["showOriginal"] = false
        persistParameters()
        refreshPreview(thumbnails = state.value.filterThumbnails.isEmpty())
    }

    fun showOriginal(show: Boolean) {
        mutableState.update { it.copy(showOriginal = show) }
        saved["showOriginal"] = show
    }

    private fun persistParameters() {
        val current = state.value
        saved["currentCrop"] = current.crop.encode()
        saved["currentRotation"] = current.rotation.name
        saved["currentFilter"] = current.filter.name
        saved["manualCrop"] = current.manual
        saved["dirty"] = current.dirty
    }

    private fun refreshPreview(thumbnails: Boolean) {
        previewJob?.cancel()
        val input = source ?: return
        val current = state.value
        if (current.original == null || current.busy) return
        previewJob = viewModelScope.launch {
            mutableState.update { it.copy(previewLoading = true, error = null) }
            try {
                val preview = processor.preview(input, current.crop, current.rotation, current.filter)
                mutableState.update { it.copy(preview = preview) }
                if (thumbnails) {
                    val small = ScanFilter.entries.map { processor.preview(input, current.crop, current.rotation, it, 160) }
                    mutableState.update { it.copy(filterThumbnails = small) }
                }
                mutableState.update { it.copy(previewLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(previewLoading = false, error = R.string.scan_render_failed) }
            } catch (_: OutOfMemoryError) {
                mutableState.update { it.copy(previewLoading = false, error = R.string.scan_render_failed) }
            }
        }
    }

    fun retry() {
        if (state.value.busy) return
        when (state.value.error) {
            R.string.scan_save_failed -> confirm()
            R.string.scan_render_failed -> refreshPreview(thumbnails = true)
            else -> load()
        }
    }

    fun confirm() {
        if (state.value.busy || state.value.original == null || actionJob?.isActive == true) return
        val input = source ?: return
        val current = state.value
        mutableState.update { it.copy(saving = true, error = null) }
        actionJob = viewModelScope.launch {
            previewJob?.cancelAndJoin()
            var rendered: ImageProcessor.Rendered? = null
            var committed = false
            try {
                rendered = processor.render(input, current.crop, current.rotation, current.filter)
                val output = rendered
                // Keep repository commit and queue advancement together through view-model cancellation.
                withContext(NonCancellable) {
                    if (pageId == null) {
                        repository.addPage(documentId, input, output.image, output.thumbnail,
                            current.crop.encode(), current.rotation.degrees, current.filter.name)
                    } else {
                        repository.updatePage(pageId, output.image, output.thumbnail,
                            current.crop.encode(), current.rotation.degrees, current.filter.name)
                    }
                    committed = true
                    advance()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(saving = false, error = R.string.scan_save_failed) }
            } catch (_: OutOfMemoryError) {
                mutableState.update { it.copy(saving = false, error = R.string.scan_save_failed) }
            } finally {
                withContext(NonCancellable) {
                    cleanup(rendered?.image, rendered?.thumbnail, if (committed && pageId == null) input else null)
                }
            }
            if (committed && state.value.exit == EditorState.Exit.NONE) load()
        }
    }

    /** Called only after a successful commit (or an explicit skip), never when a save fails. */
    private fun advance() {
        source = null
        listOf("workingSource", "currentCrop", "currentRotation", "currentFilter", "manualCrop", "dirty", "cropMode", "showOriginal")
            .forEach { saved.remove<Any>(it) }
        if (pageId != null || queue.isEmpty() || index + 1 >= queue.size) {
            saved["completed"] = true
            mutableState.update { it.copy(saving = false, dirty = false, exit = EditorState.Exit.PAGES) }
        } else {
            index++
            mutableState.value = EditorState(position = index + 1, total = queue.size)
        }
    }

    fun skip() {
        if (state.value.busy || pageId != null || actionJob?.isActive == true) return
        mutableState.update { it.copy(loading = true) }
        actionJob = viewModelScope.launch {
            loadJob?.cancelAndJoin()
            previewJob?.cancelAndJoin()
            cleanup(source)
            advance()
            if (state.value.exit == EditorState.Exit.NONE) load()
        }
    }

    fun discard() {
        if (state.value.saving || actionJob?.isActive == true) return
        mutableState.update { it.copy(loading = true) }
        actionJob = viewModelScope.launch {
            loadJob?.cancelAndJoin()
            previewJob?.cancelAndJoin()
            if (pageId == null) cleanup(source ?: saved.get<String>("sourcePath")?.let(::File))
            mutableState.update { it.copy(loading = false, exit = EditorState.Exit.CANCEL) }
        }
    }

    private suspend fun cleanup(vararg files: File?) {
        try { processor.deleteTemporary(*files) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.update { it.copy(error = R.string.scan_cleanup_failed) } }
    }
}
