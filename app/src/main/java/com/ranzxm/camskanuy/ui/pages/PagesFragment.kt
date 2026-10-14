package com.ranzxm.camskanuy.ui.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.data.PageEntity
import com.ranzxm.camskanuy.databinding.FragmentPagesBinding
import com.ranzxm.camskanuy.ui.common.DocumentFragment
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PagesFragment : DocumentFragment() {
    private var binding: FragmentPagesBinding? = null
    private val documentId: String get() = requireArguments().getString("documentId").orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentPagesBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = requireNotNull(binding)
        val adapter = PagesAdapter(viewLifecycleOwner.lifecycleScope,
            edit = { openEditor(documentId, it.id) }, more = ::showPageActions)
        ui.list.layoutManager = LinearLayoutManager(requireContext())
        ui.list.adapter = adapter
        ui.toolbar.setNavigationOnClickListener { leave() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { leave() }
        })
        ui.add.setOnClickListener {
            if (!model.busy) choices("doc_source", getString(R.string.doc_add_page), listOf(
                "camera" to getString(R.string.doc_camera), "gallery" to getString(R.string.doc_gallery),
            ))
        }
        ui.save.setOnClickListener {
            val doc = document(documentId) ?: return@setOnClickListener
            if (!model.busy && doc.pages.isNotEmpty()) nameDialog(R.string.doc_name,
                doc.document.name.ifBlank { defaultName(doc.document.createdAt) }, "save", documentId)
        }
        listen("doc_source") { result ->
            if (!model.busy) openCapture(documentId, result.getString("choice") == "gallery")
        }
        listen("doc_page_actions") { result ->
            val pages = document(documentId)?.sortedPages ?: return@listen
            val pageId = result.getString("context") ?: return@listen
            val index = pages.indexOfFirst { it.id == pageId }
            if (index < 0 || model.busy) return@listen
            when (result.getString("choice")) {
                "edit", "rotate" -> openEditor(documentId, pageId)
                "earlier", "later" -> {
                    val target = index + if (result.getString("choice") == "earlier") -1 else 1
                    if (target in pages.indices) {
                        val ids = pages.map { it.id }.toMutableList()
                        ids.add(target, ids.removeAt(index))
                        model.reorder(documentId, ids)
                    }
                }
                "delete" -> confirm(getString(R.string.doc_delete_page_title),
                    getString(R.string.doc_delete_page_message, index + 1)) { model.deletePage(pageId) }
            }
        }
        listen("doc_leave") { result ->
            if (!model.busy) when (result.getString("choice")) {
                "keep" -> goHome()
                "discard" -> document(documentId)?.let(::confirmDelete)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(model.documents, model.working, model.error) { docs, working, error ->
                    val doc = docs?.find { it.document.id == documentId }
                    val pages = doc?.sortedPages.orEmpty()
                    adapter.submitList(pages)
                    ui.toolbar.subtitle = getString(R.string.doc_page_count, pages.size)
                    ui.progress.isVisible = working || (docs == null && !error)
                    ui.empty.isVisible = docs != null && pages.isEmpty()
                    ui.empty.setText(if (doc == null) R.string.doc_missing else R.string.doc_empty_pages)
                    ui.add.isEnabled = doc != null && !working
                    ui.save.isEnabled = pages.isNotEmpty() && !working
                }.collect { }
            }
        }
    }

    private fun showPageActions(page: PageEntity) {
        if (model.busy) return
        val pages = document(documentId)?.sortedPages ?: return
        val index = pages.indexOfFirst { it.id == page.id }
        if (index < 0) return
        choices("doc_page_actions", getString(R.string.doc_page_number, index + 1), buildList {
            add("edit" to getString(R.string.doc_edit))
            add("rotate" to getString(R.string.doc_rotate_editor))
            if (index > 0) add("earlier" to getString(R.string.doc_earlier))
            if (index < pages.lastIndex) add("later" to getString(R.string.doc_later))
            add("delete" to getString(R.string.doc_delete))
        }, page.id)
    }

    private fun leave() {
        if (model.busy) { showMessage(R.string.doc_processing); return }
        val doc = document(documentId)
        if (doc == null) { goHome(); return }
        if (doc?.document?.isDraft == true) {
            choices("doc_leave", getString(R.string.doc_leave_draft), listOf(
                "keep" to getString(R.string.doc_keep_draft), "discard" to getString(R.string.doc_discard),
                "stay" to getString(R.string.doc_keep_editing),
            ))
        } else findNavController().popBackStack()
    }

    override fun onDestroyView() { binding?.list?.adapter = null; binding = null; super.onDestroyView() }
}
