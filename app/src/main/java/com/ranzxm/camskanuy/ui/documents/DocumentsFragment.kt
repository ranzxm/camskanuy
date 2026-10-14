package com.ranzxm.camskanuy.ui.documents

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.databinding.FragmentDocumentsBinding
import com.ranzxm.camskanuy.ui.common.DocumentFragment
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DocumentsFragment : DocumentFragment() {
    private var binding: FragmentDocumentsBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentDocumentsBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = requireNotNull(binding)
        val adapter = DocumentsAdapter(viewLifecycleOwner.lifecycleScope, open = { doc ->
            if (!model.busy) {
                if (doc.document.isDraft) openPages(doc.document.id) else findNavController().navigate(
                    R.id.detailFragment, bundleOf("documentId" to doc.document.id))
            }
        }, more = ::showDocumentActions)
        ui.list.layoutManager = LinearLayoutManager(requireContext())
        ui.list.adapter = adapter
        ui.search.setText(model.query.value)
        ui.search.doAfterTextChanged { model.setQuery(it?.toString().orEmpty()) }
        ui.clearSearch.setOnClickListener { ui.search.setText(""); model.setFolder("all") }
        ui.folder.setOnClickListener { showFolders() }
        ui.scan.setOnClickListener { model.createDraft(false) }
        ui.importImages.setOnClickListener { model.createDraft(true) }
        ui.toolbar.menu.add(0, 1, 0, R.string.doc_appearance).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        ui.toolbar.menu.add(0, 2, 1, R.string.doc_info).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        ui.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showAppearance()
                2 -> confirm(getString(R.string.doc_info), getString(R.string.doc_info_body), R.string.doc_close) { }
            }
            true
        }
        listen("doc_folders") { result ->
            when (val choice = result.getString("choice")) {
                "new" -> nameDialog(R.string.doc_folder_name, "", "new_folder")
                "manage" -> choices("doc_folder_select", getString(R.string.doc_manage_folder),
                    model.folders.value.map { it.id to it.name })
                null -> Unit
                else -> model.setFolder(choice)
            }
        }
        listen("doc_folder_select") { result ->
            val folder = model.folders.value.find { it.id == result.getString("choice") } ?: return@listen
            choices("doc_folder_manage", folder.name, listOf(
                "rename" to getString(R.string.doc_rename), "delete" to getString(R.string.doc_delete),
            ), folder.id)
        }
        listen("doc_folder_manage") { result ->
            val folder = model.folders.value.find { it.id == result.getString("context") } ?: return@listen
            when (result.getString("choice")) {
                "rename" -> nameDialog(R.string.doc_folder_name, folder.name, "rename_folder", folder.id)
                "delete" -> confirm(getString(R.string.doc_delete), getString(R.string.doc_delete_folder_message, folder.name)) {
                    model.deleteFolder(folder.id)
                }
            }
        }
        listen("doc_appearance") { result ->
            val value = result.getString("choice") ?: return@listen
            requireContext().getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("mode", value).apply()
            AppCompatDelegate.setDefaultNightMode(when (value) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            })
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(model.documents, model.query, model.folder, model.folders) { docs, query, filter, folders ->
                        val visible = docs?.filter { doc ->
                            (!doc.document.isDraft || doc.pages.isNotEmpty()) &&
                                doc.document.name.contains(query.trim(), ignoreCase = true) &&
                                when (filter) {
                                    "all" -> true
                                    "unfiled" -> doc.document.folderId == null
                                    else -> doc.document.folderId == filter
                                }
                        }?.sortedWith(compareByDescending<com.ranzxm.camskanuy.data.DocumentWithPages> { it.document.isDraft }
                            .thenByDescending { it.document.updatedAt })
                        ui.folder.text = when (filter) {
                            "all" -> getString(R.string.doc_all)
                            "unfiled" -> getString(R.string.doc_unfiled)
                            else -> folders.find { it.id == filter }?.name ?: getString(R.string.doc_unfiled)
                        }
                        val searching = query.isNotBlank() || filter != "all"
                        ui.emptyTitle.setText(if (searching) R.string.doc_no_results else R.string.doc_empty_title)
                        ui.emptyBody.setText(if (searching) R.string.doc_no_results_body else R.string.doc_empty_body)
                        ui.clearSearch.isVisible = searching
                        ui.empty.isVisible = visible != null && visible.isEmpty()
                        visible
                    }.collect { adapter.submitList(it.orEmpty()) }
                }
                launch {
                    combine(model.working, model.documents, model.pendingExport, model.error) { working, docs, pending, error ->
                        ui.progress.isVisible = working || (docs == null && !error)
                        val enabled = !working && pending == null
                        ui.scan.isEnabled = enabled
                        ui.importImages.isEnabled = enabled
                        ui.folder.isEnabled = enabled
                    }.collect { }
                }
            }
        }
    }

    private fun showFolders() {
        choices("doc_folders", getString(R.string.doc_folders), buildList {
            add("all" to getString(R.string.doc_all))
            add("unfiled" to getString(R.string.doc_unfiled))
            addAll(model.folders.value.map { it.id to it.name })
            add("new" to getString(R.string.doc_new_folder))
            if (model.folders.value.isNotEmpty()) add("manage" to getString(R.string.doc_manage_folder))
        }, selected = model.folder.value)
    }

    private fun showAppearance() {
        val mode = requireContext().getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("mode", "system")
        choices("doc_appearance", getString(R.string.doc_appearance), listOf(
            "system" to getString(R.string.doc_system), "light" to getString(R.string.doc_light), "dark" to getString(R.string.doc_dark),
        ), selected = mode)
    }

    override fun onDestroyView() { binding?.list?.adapter = null; binding = null; super.onDestroyView() }
}
