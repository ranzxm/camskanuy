package com.ranzxm.camskanuy.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.databinding.DocChoiceSheetBinding

/** Argument-only sheets can be recreated without retaining a Fragment/View callback. */
class DocumentChoiceSheet : BottomSheetDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val binding = DocChoiceSheetBinding.inflate(inflater, container, false)
        val args = requireArguments()
        binding.title.text = args.getString("title")
        val keys = args.getStringArrayList("keys").orEmpty()
        val labels = args.getStringArrayList("labels").orEmpty()
        keys.forEachIndexed { index, key ->
            val label = labels[index]
            binding.choices.addView(MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonTextButtonStyle).apply {
                text = if (key == args.getString("selected")) getString(R.string.doc_selected, label) else label
                isAllCaps = false
                minHeight = (52 * resources.displayMetrics.density).toInt()
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    val manager = parentFragmentManager
                    dismissNow()
                    manager.setFragmentResult(args.getString("request").orEmpty(), bundleOf(
                        "choice" to key, "context" to args.getString("context"),
                    ))
                }
            })
        }
        return binding.root
    }

    companion object {
        fun show(manager: FragmentManager, request: String, title: String,
                 choices: List<Pair<String, String>>, context: String? = null, selected: String? = null) {
            if (manager.isStateSaved || manager.findFragmentByTag("doc_sheet") != null) return
            DocumentChoiceSheet().apply {
                arguments = bundleOf("request" to request, "title" to title, "context" to context,
                    "keys" to ArrayList(choices.map { it.first }),
                    "labels" to ArrayList(choices.map { it.second }), "selected" to selected)
            }.show(manager, "doc_sheet")
        }
    }
}
