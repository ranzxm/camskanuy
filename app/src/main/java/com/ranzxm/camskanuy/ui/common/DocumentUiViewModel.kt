package com.ranzxm.camskanuy.ui.common

import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.data.DocumentExporter
import com.ranzxm.camskanuy.data.DocumentRepository
import com.ranzxm.camskanuy.data.DocumentWithPages
import com.ranzxm.camskanuy.data.FolderEntity
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Per-screen state. Export paths and picker hand-off survive activity/process recreation. */
class DocumentUiViewModel(
    private val repository: DocumentRepository,
    private val exporter: DocumentExporter,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _documents = MutableStateFlow<List<DocumentWithPages>?>(null)
    val documents = _documents.asStateFlow()
    private val _folders = MutableStateFlow<List<FolderEntity>>(emptyList())
    val folders = _folders.asStateFlow()
    private val _working = MutableStateFlow(false)
    val working = _working.asStateFlow()
    private val _error = MutableStateFlow(false)
    val error = _error.asStateFlow()
    val query: StateFlow<String> = saved.getStateFlow("query", "")
    // "all", "unfiled", or a stable folder ID.
    val folder: StateFlow<String> = saved.getStateFlow("folder", "all")
    val pageIndex: StateFlow<Int> = saved.getStateFlow("pageIndex", 0)
    val event: StateFlow<Bundle?> = saved.getStateFlow("event", null)
    val pendingExport: StateFlow<Bundle?> = saved.getStateFlow("pendingExport", null)
    private var retryAction: (() -> Unit)? = null
    private var observation: Job? = null
    private var queuedExportResult: Uri? = null
    private var exportResultQueued = false

    init {
        observe()
        // A provider write interrupted by process death can safely be retried explicitly.
        if (saved.get<String>("writeUri") != null) {
            _error.value = true
            retryAction = { finishExport(Uri.parse(saved.get<String>("writeUri"))) }
        }
    }

    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            try {
                combine(repository.observeDocuments(), repository.observeFolders()) { docs, folders ->
                    docs to folders
                }.collect { (docs, folders) ->
                    _documents.value = docs
                    _folders.value = folders
                    if (folder.value != "all" && folder.value != "unfiled" &&
                        folders.none { it.id == folder.value }) setFolder("unfiled")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail { observe() }
            }
        }
    }

    fun setQuery(value: String) { saved["query"] = value }
    fun setFolder(value: String) { saved["folder"] = value }
    fun setPageIndex(value: Int) { saved["pageIndex"] = value.coerceAtLeast(0) }
    fun consumeEvent() { saved["event"] = null }
    fun dismissError() { _error.value = false }
    fun retry() { _error.value = false; retryAction?.invoke() }
    val busy: Boolean get() = _working.value || pendingExport.value != null

    private fun fail(retry: () -> Unit) {
        retryAction = retry
        _error.value = true
    }

    private fun perform(block: suspend () -> Bundle?) {
        if (_working.value) return
        _working.value = true
        _error.value = false
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { block() }
                if (result != null) saved["event"] = result
                retryAction = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail { perform(block) }
            } finally {
                _working.value = false
                if (exportResultQueued) {
                    val uri = queuedExportResult
                    exportResultQueued = false
                    queuedExportResult = null
                    finishExport(uri)
                }
            }
        }
    }

    fun createDraft(gallery: Boolean) {
        if (busy) return
        perform {
            val id = repository.createDraft()
            bundleOf("kind" to "capture", "documentId" to id, "openGallery" to gallery)
        }
    }

    fun rename(id: String, name: String) = mutate { repository.renameDocument(id, name) }
    fun move(id: String, folderId: String?) = mutate { repository.moveDocument(id, folderId) }
    fun createFolder(name: String) = mutate { repository.createFolder(name) }
    fun renameFolder(id: String, name: String) = mutate { repository.renameFolder(id, name) }
    fun deleteFolder(id: String) = mutate { repository.deleteFolder(id) }
    fun deletePage(id: String) = mutate { repository.deletePage(id) }
    fun reorder(id: String, pageIds: List<String>) = mutate { repository.reorderPages(id, pageIds) }

    private fun mutate(block: suspend () -> Unit) {
        if (busy) return
        perform { block(); bundleOf("kind" to "message", "message" to R.string.doc_saved) }
    }

    fun deleteDocument(id: String, leave: Boolean) {
        if (busy) return
        perform {
            repository.deleteDocument(id)
            if (leave) bundleOf("kind" to "home") else null
        }
    }

    fun save(id: String, name: String) {
        if (busy) return
        perform {
            repository.saveDocument(id, name)
            bundleOf("kind" to "saved", "documentId" to id)
        }
    }

    fun prepareExport(documentId: String, pageId: String?, pdf: Boolean, share: Boolean) {
        if (busy) return
        perform {
            val doc = requireNotNull(repository.getDocument(documentId))
            require(doc.sortedPages.isNotEmpty())
            val file = if (pdf) exporter.createPdf(doc) else {
                val page = requireNotNull(doc.sortedPages.find { it.id == pageId })
                exporter.createJpg(page)
            }
            val pending = bundleOf(
                "path" to file.absolutePath, "mime" to if (pdf) "application/pdf" else "image/jpeg",
                "name" to safeExportName(doc.document.name, pdf), "share" to share, "launched" to false,
            )
            withContext(Dispatchers.Main) { saved["pendingExport"] = pending }
            null
        }
    }

    fun markExportLaunched() {
        val pending = pendingExport.value ?: return
        saved["pendingExport"] = Bundle(pending).apply { putBoolean("launched", true) }
    }

    fun clearExport() { saved["pendingExport"] = null; saved["writeUri"] = null }

    fun finishExport(uri: Uri?) {
        if (_working.value) {
            queuedExportResult = uri
            exportResultQueued = true
            return
        }
        if (uri == null) { clearExport(); return }
        val path = pendingExport.value?.getString("path") ?: run {
            fail { clearExport() }
            return
        }
        saved["writeUri"] = uri.toString()
        perform {
            exporter.writeToUri(File(path), uri)
            withContext(Dispatchers.Main) { clearExport() }
            bundleOf("kind" to "message", "message" to R.string.doc_export_success)
        }
    }

    private fun safeExportName(name: String, pdf: Boolean): String {
        val base = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(100)
        return "${base.ifBlank { "Camskanuy" }}.${if (pdf) "pdf" else "jpg"}"
    }
}
