package com.ranzxm.camskanuy.ui.common

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.databinding.DocNameInputBinding

class DocumentNameDialog : DialogFragment() {
    private var binding: DocNameInputBinding? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val input = DocNameInputBinding.inflate(layoutInflater)
        binding = input
        val args = requireArguments()
        input.root.hint = args.getString("title")
        input.input.setText(savedInstanceState?.getString("text") ?: args.getString("value"))
        input.input.selectAll()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(args.getString("title")).setView(input.root)
            .setNegativeButton(R.string.doc_cancel, null)
            .setPositiveButton(R.string.doc_save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.input.text?.toString().orEmpty().trim()
                val error = when {
                    name.isBlank() -> R.string.doc_name_required
                    name.length > 120 -> R.string.doc_name_too_long
                    else -> null
                }
                if (error != null) input.root.error = getString(error) else {
                    parentFragmentManager.setFragmentResult("doc_name", bundleOf(
                        "action" to args.getString("action"), "context" to args.getString("context"), "name" to name,
                    ))
                    dismiss()
                }
            }
        }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("text", binding?.input?.text?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() { binding = null; super.onDestroyView() }

    companion object {
        fun show(manager: FragmentManager, title: String, value: String, action: String, context: String? = null) {
            if (manager.isStateSaved || manager.findFragmentByTag("doc_name") != null) return
            DocumentNameDialog().apply {
                arguments = bundleOf("title" to title, "value" to value, "action" to action, "context" to context)
            }.show(manager, "doc_name")
        }
    }
}
