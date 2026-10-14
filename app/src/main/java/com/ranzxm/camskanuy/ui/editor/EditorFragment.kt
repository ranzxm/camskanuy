package com.ranzxm.camskanuy.ui.editor

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.addCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.databinding.FragmentEditorBinding
import com.ranzxm.camskanuy.scanner.ScanCrop
import com.ranzxm.camskanuy.scanner.ScanFilter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class EditorFragment : Fragment() {
    private var binding: FragmentEditorBinding? = null
    private val model: EditorViewModel by viewModels()
    private var rendering = false
    private var discardDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentEditorBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = requireNotNull(binding)
        ui.toolbar.setTitle(R.string.scan_editor_title)
        ui.toolbar.navigationIcon = AppCompatResources.getDrawable(requireContext(), androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        ui.toolbar.setNavigationContentDescription(R.string.scan_back)
        ui.toolbar.setNavigationOnClickListener { back() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { back() }
        ui.cropOverlay.onCropChanged = model::setCrop
        ui.cropOverlay.onCornerSelected = model::selectCorner
        ui.resetCrop.setOnClickListener { model.setCrop(ScanCrop.FULL) }
        ui.rotate.setOnClickListener { model.rotate() }
        ui.usePage.setOnClickListener { model.confirm() }
        ui.retry.setOnClickListener { model.retry() }
        ui.skip.setOnClickListener { model.skip() }
        ui.moveLeft.setOnClickListener { model.moveCorner(-0.01f, 0f) }
        ui.moveRight.setOnClickListener { model.moveCorner(0.01f, 0f) }
        ui.moveUp.setOnClickListener { model.moveCorner(0f, -0.01f) }
        ui.moveDown.setOnClickListener { model.moveCorner(0f, 0.01f) }
        val corners = listOf(R.string.scan_corner_top_left, R.string.scan_corner_top_right,
            R.string.scan_corner_bottom_right, R.string.scan_corner_bottom_left).map(::getString)
        ui.corner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, corners)
        ui.corner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!rendering) model.selectCorner(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        ui.modes.setOnCheckedChangeListener { _, checked ->
            if (!rendering) model.setCropMode(checked == R.id.cropMode)
        }
        ui.filters.setOnCheckedChangeListener { _, checked ->
            if (!rendering) model.setFilter(when (checked) {
                R.id.filterColor -> ScanFilter.COLOR
                R.id.filterGrayscale -> ScanFilter.GRAYSCALE
                R.id.filterBlackWhite -> ScanFilter.BLACK_WHITE
                else -> ScanFilter.ORIGINAL
            })
        }
        ui.showOriginal.setOnCheckedChangeListener { _, checked -> if (!rendering) model.showOriginal(checked) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    render(state)
                    if (state.exit != EditorState.Exit.NONE && findNavController().currentDestination?.id == R.id.editorFragment) {
                        if (state.exit == EditorState.Exit.CANCEL && model.pageId == null &&
                            findNavController().previousBackStackEntry?.destination?.id == R.id.captureFragment) {
                            findNavController().popBackStack()
                        } else {
                            findNavController().navigate(R.id.pagesFragment, bundleOf("documentId" to model.documentId),
                                navOptions { popUpTo(R.id.documentsFragment) { inclusive = false } })
                        }
                    }
                }
            }
        }
    }

    private fun render(state: EditorState) {
        val ui = binding ?: return
        rendering = true
        ui.pagePosition.text = getString(R.string.scan_page_position, state.position, state.total)
        ui.cropOverlay.setImage(state.original)
        ui.cropOverlay.crop = state.crop
        ui.cropOverlay.selectedCorner = state.corner
        ui.cropOverlay.isVisible = state.cropMode
        ui.preview.isVisible = !state.cropMode
        ui.preview.setImageBitmap(if (state.showOriginal) state.original else state.preview)
        ui.preview.contentDescription = getString(if (state.showOriginal) R.string.scan_source_preview else R.string.scan_preview)
        ui.cropTools.isVisible = state.cropMode
        ui.filterTools.isVisible = !state.cropMode
        ui.progress.isVisible = state.busy || state.previewLoading
        ui.status.setText(when {
            state.saving -> R.string.scan_saving
            state.loading -> R.string.scan_processing
            state.error != null -> state.error
            state.previewLoading -> R.string.scan_preview_processing
            state.manual -> R.string.scan_manual_crop
            else -> R.string.scan_detected_crop
        })
        ui.retry.isVisible = state.error != null && !state.busy
        ui.skip.isVisible = state.error != null && !state.busy && model.pageId == null && state.original == null
        ui.modes.check(if (state.cropMode) R.id.cropMode else R.id.filterMode)
        ui.filters.check(when (state.filter) {
            ScanFilter.ORIGINAL -> R.id.filterOriginal
            ScanFilter.COLOR -> R.id.filterColor
            ScanFilter.GRAYSCALE -> R.id.filterGrayscale
            ScanFilter.BLACK_WHITE -> R.id.filterBlackWhite
        })
        val filterButtons = listOf(ui.filterOriginal, ui.filterColor, ui.filterGrayscale, ui.filterBlackWhite)
        state.filterThumbnails.forEachIndexed { index, bitmap ->
            val drawable = BitmapDrawable(resources, bitmap)
            val size = (resources.displayMetrics.density * 72).roundToInt()
            drawable.setBounds(0, 0, size, size)
            filterButtons[index].setCompoundDrawables(null, drawable, null, null)
        }
        if (state.filterThumbnails.isEmpty()) filterButtons.forEach { it.setCompoundDrawables(null, null, null, null) }
        ui.showOriginal.isChecked = state.showOriginal
        ui.corner.setSelection(state.corner)
        val point = state.crop.points[state.corner]
        ui.cornerPosition.text = getString(R.string.scan_corner_position, (point.x * 100).roundToInt(), (point.y * 100).roundToInt())
        ui.rotationState.text = getString(R.string.scan_rotation_state, state.rotation.degrees)
        val enabled = !state.busy && state.original != null
        listOf(ui.cropOverlay, ui.resetCrop, ui.rotate, ui.moveLeft, ui.moveRight, ui.moveUp, ui.moveDown,
            ui.corner, ui.cropMode, ui.filterMode, ui.filterOriginal, ui.filterColor, ui.filterGrayscale,
            ui.filterBlackWhite, ui.showOriginal).forEach { it.isEnabled = enabled }
        ui.usePage.isEnabled = enabled && !state.previewLoading
        ui.usePage.setText(if (model.pageId == null) R.string.scan_use_page else R.string.scan_save_changes)
        rendering = false
    }

    private fun back() {
        val state = model.state.value
        if (state.saving) return
        if (!state.dirty && model.pageId != null) { model.discard(); return }
        if (discardDialog?.isShowing == true) return
        discardDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.scan_discard_title)
            .setMessage(R.string.scan_discard_message)
            .setNegativeButton(R.string.scan_keep_editing, null)
            .setPositiveButton(R.string.scan_discard) { _, _ -> model.discard() }
            .show()
    }

    override fun onDestroyView() {
        discardDialog?.dismiss()
        discardDialog = null
        binding?.cropOverlay?.onCropChanged = null
        binding?.cropOverlay?.onCornerSelected = null
        binding = null
        super.onDestroyView()
    }
}
