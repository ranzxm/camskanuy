package com.ranzxm.camskanuy.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ranzxm.camskanuy.CamskanuyApp
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.data.DocumentWithPages
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** Shared document actions; no View reference is retained in a ViewModel. */
abstract class DocumentFragment : Fragment() {
    protected val model: DocumentUiViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = requireActivity().application as CamskanuyApp
                DocumentUiViewModel(app.repository, app.exporter, createSavedStateHandle())
            }
        }
    }
    private var confirmation: AlertDialog? = null
    private var errorBar: Snackbar? = null
    private val pdfDestination = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        model.finishExport(it)
    }
    private val jpgDestination = registerForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) {
        model.finishExport(it)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listen("doc_name") { result ->
            val name = result.getString("name") ?: return@listen
            val id = result.getString("context").orEmpty()
            when (result.getString("action")) {
                "rename" -> model.rename(id, name)
                "save" -> model.save(id, name)
                "new_folder" -> model.createFolder(name)
                "rename_folder" -> model.renameFolder(id, name)
            }
        }
        listen("doc_actions") { result ->
            val id = result.getString("context") ?: return@listen
            val doc = document(id) ?: return@listen
            if (model.busy) return@listen
            when (result.getString("choice")) {
                "rename" -> nameDialog(R.string.doc_name, doc.document.name, "rename", id)
                "move" -> showMove(doc)
                "share" -> showExport(doc, true)
                "resume" -> openPages(id)
                "delete", "discard" -> confirmDelete(doc)
            }
        }
        listen("doc_move") { result ->
            val id = result.getString("context") ?: return@listen
            val folder = result.getString("choice") ?: return@listen
            model.move(id, folder.takeUnless { it == "unfiled" })
        }
        listen("doc_export") { result ->
            val id = result.getString("context") ?: return@listen
            val choice = result.getString("choice").orEmpty()
            model.prepareExport(id, currentPageId(id), choice.endsWith("pdf"), choice.startsWith("share"))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.event.collect { event ->
                    if (event == null) return@collect
                    model.consumeEvent()
                    when (event.getString("kind")) {
                        "capture" -> openCapture(event.getString("documentId").orEmpty(), event.getBoolean("openGallery"))
                        "saved" -> findNavController().navigate(R.id.detailFragment,
                            bundleOf("documentId" to event.getString("documentId")),
                            navOptions { popUpTo(R.id.documentsFragment); launchSingleTop = true })
                        "home" -> goHome()
                        "message" -> showMessage(event.getInt("message", R.string.doc_saved))
                    }
                } }
                launch { model.error.collect { failed ->
                    errorBar?.dismiss()
                    errorBar = if (failed) Snackbar.make(view, R.string.doc_error, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.doc_retry) { model.retry() }.also { it.show() } else null
                } }
                launch { model.pendingExport.collect { pending ->
                    if (pending == null || pending.getBoolean("launched")) return@collect
                    model.markExportLaunched()
                    if (pending.getBoolean("share")) shareFile(pending) else {
                        try {
                            val name = pending.getString("name").orEmpty()
                            if (pending.getString("mime") == "application/pdf") pdfDestination.launch(name)
                            else jpgDestination.launch(name)
                        } catch (_: ActivityNotFoundException) {
                            model.clearExport(); showMessage(R.string.doc_no_receiver)
                        }
                    }
                } }
            }
        }
    }

    protected fun listen(request: String, listener: (Bundle) -> Unit) {
        childFragmentManager.setFragmentResultListener(request, viewLifecycleOwner) { _, result -> listener(result) }
    }

    protected fun document(id: String): DocumentWithPages? = model.documents.value?.find { it.document.id == id }
    protected open fun currentPageId(documentId: String): String? = document(documentId)?.sortedPages?.firstOrNull()?.id

    protected fun showDocumentActions(doc: DocumentWithPages) {
        if (model.busy) return
        val choices = buildList {
            if (doc.document.isDraft) add("resume" to getString(R.string.doc_resume))
            add("rename" to getString(R.string.doc_rename))
            add("move" to getString(R.string.doc_move))
            if (doc.sortedPages.isNotEmpty()) add("share" to getString(R.string.doc_share))
            add((if (doc.document.isDraft) "discard" else "delete") to
                getString(if (doc.document.isDraft) R.string.doc_discard else R.string.doc_delete))
        }
        choices("doc_actions", doc.document.name, choices, doc.document.id)
    }

    protected fun showExport(doc: DocumentWithPages, share: Boolean) {
        if (model.busy || doc.sortedPages.isEmpty()) return
        val prefix = if (share) "share" else "export"
        choices("doc_export", getString(if (share) R.string.doc_share else R.string.doc_export), listOf(
            "${prefix}_pdf" to getString(R.string.doc_pdf), "${prefix}_jpg" to getString(R.string.doc_jpg),
        ), doc.document.id)
    }

    private fun showMove(doc: DocumentWithPages) {
        choices("doc_move", getString(R.string.doc_move),
            listOf("unfiled" to getString(R.string.doc_unfiled)) + model.folders.value.map { it.id to it.name },
            doc.document.id, doc.document.folderId ?: "unfiled")
    }

    protected fun confirmDelete(doc: DocumentWithPages) {
        val draft = doc.document.isDraft
        confirm(getString(if (draft) R.string.doc_discard_title else R.string.doc_delete_title),
            if (draft) getString(R.string.doc_discard_message) else getString(R.string.doc_delete_message, doc.document.name),
            if (draft) R.string.doc_discard else R.string.doc_delete) {
            model.deleteDocument(doc.document.id, arguments?.getString("documentId") == doc.document.id)
        }
    }

    protected fun confirm(title: String, message: String, positive: Int = R.string.doc_delete, action: () -> Unit) {
        confirmation?.dismiss()
        confirmation = MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(message)
            .setNegativeButton(R.string.doc_cancel, null).setPositiveButton(positive) { _, _ -> action() }.show()
    }

    protected fun choices(request: String, title: String, entries: List<Pair<String, String>>,
                          context: String? = null, selected: String? = null) {
        DocumentChoiceSheet.show(childFragmentManager, request, title, entries, context, selected)
    }

    protected fun nameDialog(title: Int, value: String, action: String, context: String? = null) {
        DocumentNameDialog.show(childFragmentManager, getString(title), value, action, context)
    }

    protected fun defaultName(createdAt: Long): String = getString(R.string.doc_default_name,
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(createdAt)))

    protected fun openCapture(id: String, gallery: Boolean) {
        findNavController().navigate(R.id.captureFragment, bundleOf("documentId" to id, "openGallery" to gallery))
    }
    protected fun openPages(id: String) {
        findNavController().navigate(R.id.pagesFragment, bundleOf("documentId" to id), navOptions { launchSingleTop = true })
    }
    protected fun openEditor(id: String, pageId: String) {
        if (model.busy) return
        findNavController().navigate(R.id.editorFragment, bundleOf("documentId" to id, "pageId" to pageId))
    }
    protected fun goHome() { findNavController().popBackStack(R.id.documentsFragment, false) }
    protected fun showMessage(message: Int) { view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() } }

    private fun shareFile(pending: Bundle) {
        try {
            val file = File(requireNotNull(pending.getString("path")))
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.files", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = pending.getString("mime")
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.doc_share)).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.doc_no_receiver)
        } catch (_: IllegalArgumentException) {
            showMessage(R.string.doc_error)
        } catch (_: SecurityException) {
            showMessage(R.string.doc_error)
        } finally {
            model.clearExport()
        }
    }

    override fun onDestroyView() {
        confirmation?.dismiss(); confirmation = null
        errorBar?.dismiss(); errorBar = null
        super.onDestroyView()
    }
}
