package com.ranzxm.camskanuy.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.databinding.FragmentDetailBinding
import com.ranzxm.camskanuy.ui.common.DocumentFragment
import com.ranzxm.camskanuy.ui.common.DocumentImages
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DetailFragment : DocumentFragment() {
    private var binding: FragmentDetailBinding? = null
    private var imageJob: Job? = null
    private var previewKey: String? = null
    private val documentId: String get() = requireArguments().getString("documentId").orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentDetailBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = requireNotNull(binding)
        ui.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        ui.toolbar.menu.add(0, 1, 0, R.string.doc_more).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        ui.toolbar.setOnMenuItemClickListener {
            document(documentId)?.let(::showDocumentActions)
            true
        }
        ui.previous.setOnClickListener { model.setPageIndex(model.pageIndex.value - 1) }
        ui.next.setOnClickListener { model.setPageIndex(model.pageIndex.value + 1) }
        ui.zoomIn.setOnClickListener { ui.preview.changeZoom(1.5f) }
        ui.zoomOut.setOnClickListener { ui.preview.changeZoom(1 / 1.5f) }
        ui.zoomReset.setOnClickListener { ui.preview.resetZoom() }
        ui.edit.setOnClickListener { if (!model.busy) openPages(documentId) }
        ui.share.setOnClickListener { document(documentId)?.let { showExport(it, true) } }
        ui.export.setOnClickListener { document(documentId)?.let { showExport(it, false) } }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(model.documents, model.pageIndex, model.working, model.pendingExport, model.error) {
                    docs, selected, working, pending, error ->
                    val doc = docs?.find { it.document.id == documentId }
                    val pages = doc?.sortedPages.orEmpty()
                    val index = selected.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
                    if (index != selected) model.setPageIndex(index)
                    val page = pages.getOrNull(index)
                    ui.toolbar.title = doc?.document?.name ?: getString(R.string.doc_title)
                    ui.indicator.text = getString(R.string.doc_page_indicator, if (page == null) 0 else index + 1, pages.size)
                    ui.preview.contentDescription = getString(R.string.doc_preview, index + 1)
                    val busy = working || pending != null
                    ui.progress.isVisible = working || (docs == null && !error)
                    ui.status.isVisible = working
                    ui.previous.isEnabled = index > 0 && !busy
                    ui.next.isEnabled = index < pages.lastIndex && !busy
                    ui.edit.isEnabled = doc != null && !busy
                    ui.share.isEnabled = page != null && !busy
                    ui.export.isEnabled = page != null && !busy
                    ui.zoomIn.isEnabled = page != null
                    ui.zoomOut.isEnabled = page != null
                    ui.zoomReset.isEnabled = page != null
                    val key = page?.let { "${it.id}:${it.processedPath}:${doc?.document?.updatedAt}" }
                    if (key != previewKey) {
                        previewKey = key
                        imageJob?.cancel()
                        ui.empty.isVisible = false
                        imageJob = DocumentImages.load(viewLifecycleOwner.lifecycleScope,
                            page?.processedPath, ui.preview, 1200) { success ->
                            if (!success && page != null) {
                                ui.empty.setText(R.string.doc_image_error)
                                ui.empty.isVisible = true
                            }
                        }
                    }
                    if (page == null) {
                        ui.empty.isVisible = docs != null
                        ui.empty.setText(if (doc == null) R.string.doc_missing else R.string.doc_empty_pages)
                    }
                }.collect { }
            }
        }
    }

    override fun currentPageId(documentId: String): String? {
        val pages = document(documentId)?.sortedPages.orEmpty()
        return pages.getOrNull(model.pageIndex.value.coerceIn(0, pages.lastIndex.coerceAtLeast(0)))?.id
    }

    override fun onDestroyView() {
        imageJob?.cancel(); imageJob = null; previewKey = null
        binding?.preview?.setImageDrawable(null)
        binding = null
        super.onDestroyView()
    }
}
