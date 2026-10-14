package com.ranzxm.camskanuy.ui.editor

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.scanner.ScanCrop
import com.ranzxm.camskanuy.scanner.ScanFilter
import kotlin.math.roundToInt

@Composable
fun EditorScreen(model: EditorViewModel, onBack: () -> Unit, onPages: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnPages by rememberUpdatedState(onPages)
    var discard by rememberSaveable(model.documentId, model.pageId) { mutableStateOf(false) }
    var navigated by remember(model) { mutableStateOf(false) }
    val back: () -> Unit = {
        if (!model.state.value.busy && model.state.value.exit == EditorState.Exit.NONE) {
            if (model.state.value.dirty || model.pageId == null) discard = true else model.discard()
        }
    }
    BackHandler(onBack = back)
    LaunchedEffect(state.exit) {
        if (!navigated && state.exit != EditorState.Exit.NONE) {
            navigated = true
            if (state.exit == EditorState.Exit.PAGES) currentOnPages() else currentOnBack()
        }
    }
    if (discard) AlertDialog(
        onDismissRequest = { if (!state.busy) discard = false },
        title = { Text(stringResource(R.string.scan_discard_title)) },
        text = { Text(stringResource(R.string.scan_discard_message)) },
        confirmButton = {
            TextButton(onClick = { discard = false; model.discard() }, enabled = !state.busy) {
                Text(stringResource(R.string.scan_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = { discard = false }, enabled = !state.busy) {
                Text(stringResource(R.string.scan_keep_editing))
            }
        },
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back, enabled = !state.busy && state.exit == EditorState.Exit.NONE) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.scan_back))
                }
                Column(Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 8.dp)) {
                    Text(stringResource(R.string.scan_editor_title), style = MaterialTheme.typography.titleMedium)
                    if (state.total > 1) Text(stringResource(R.string.scan_page_position, state.position, state.total),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val availableWidth = maxWidth
                val availableHeight = maxHeight
                if (availableWidth > availableHeight && availableWidth >= 600.dp) {
                    Row(Modifier.fillMaxSize()) {
                        EditorPreview(model, state, Modifier.weight(1f).fillMaxSize())
                        EditorToolPanel(model, state, Modifier.width(availableWidth * 0.46f).fillMaxSize())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        EditorPreview(model, state, Modifier.weight(1f).fillMaxWidth())
                        EditorToolPanel(model, state, Modifier.fillMaxWidth().heightIn(max = availableHeight * 0.48f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorPreview(model: EditorViewModel, state: EditorState, modifier: Modifier) {
    val cropHint = stringResource(R.string.scan_crop_accessibility)
    Box(modifier.background(MaterialTheme.colorScheme.scrim), contentAlignment = Alignment.Center) {
        if (state.cropMode && state.original != null) {
            AndroidView(
                factory = { context -> CropOverlayView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO } },
                modifier = Modifier.fillMaxSize().semantics { contentDescription = cropHint },
                onRelease = { view ->
                    view.onCropChanged = null
                    view.onCornerSelected = null
                    view.setImage(null)
                    view.tag = null
                },
                update = { view ->
                    if (view.tag !== state.original) { view.setImage(state.original); view.tag = state.original }
                    view.crop = state.crop
                    view.selectedCorner = state.corner
                    view.isEnabled = !state.busy && state.exit == EditorState.Exit.NONE
                    view.onCropChanged = model::setCrop
                    view.onCornerSelected = model::selectCorner
                },
            )
        } else {
            val bitmap = if (state.showOriginal) state.original else state.preview
            if (bitmap != null) Image(remember(bitmap) { bitmap.asImageBitmap() },
                stringResource(if (state.showOriginal) R.string.scan_source_preview else R.string.scan_preview),
                modifier = Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit)
        }
        if (state.busy || state.previewLoading) CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun EditorToolPanel(model: EditorViewModel, state: EditorState, modifier: Modifier) {
    val enabled = !state.busy && state.original != null && state.exit == EditorState.Exit.NONE
    Surface(modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text(stringResource(when {
                    state.saving -> R.string.scan_saving
                    state.loading -> R.string.scan_processing
                    state.error != null -> state.error ?: R.string.scan_load_failed
                    state.previewLoading -> R.string.scan_preview_processing
                    state.manual -> R.string.scan_manual_crop
                    else -> R.string.scan_detected_crop
                }), style = MaterialTheme.typography.bodySmall,
                    color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
                if (state.error != null && !state.busy) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = model::retry) { Text(stringResource(R.string.scan_retry)) }
                        if (model.pageId == null && state.original == null) {
                            TextButton(onClick = model::skip) { Text(stringResource(R.string.scan_skip)) }
                        }
                    }
                }
                TabRow(selectedTabIndex = if (state.cropMode) 0 else 1) {
                    Tab(selected = state.cropMode, onClick = { model.setCropMode(true) }, enabled = enabled,
                        text = { Text(stringResource(R.string.scan_crop)) })
                    Tab(selected = !state.cropMode, onClick = { model.setCropMode(false) }, enabled = enabled,
                        text = { Text(stringResource(R.string.scan_filters)) })
                }
                if (state.cropMode) FineCropControls(model, state, enabled)
                else FilterControls(model, state, enabled)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = model::rotate, enabled = enabled) {
                        Icon(Icons.Outlined.Rotate90DegreesCw, null, Modifier.size(20.dp))
                        Text(stringResource(R.string.scan_rotate), Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = { model.setCropMode(true); model.setCrop(ScanCrop.FULL) }, enabled = enabled) {
                        Icon(Icons.Outlined.RestartAlt, null, Modifier.size(20.dp))
                        Text(stringResource(R.string.scan_reset), Modifier.padding(start = 8.dp))
                    }
                    Text(stringResource(R.string.scan_rotation_state, state.rotation.degrees),
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
            Button(onClick = model::confirm, enabled = enabled && !state.previewLoading,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 52.dp)) {
                Text(stringResource(if (model.pageId == null) R.string.scan_use_page else R.string.scan_save_changes))
            }
        }
    }
}

@Composable
private fun FineCropControls(model: EditorViewModel, state: EditorState, enabled: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var cornersOpen by remember { mutableStateOf(false) }
    val expandedDescription = stringResource(if (expanded) R.string.scan_adjust_expanded else R.string.scan_adjust_collapsed)
    TextButton(onClick = { expanded = !expanded }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = expandedDescription }) {
        Text(stringResource(R.string.scan_fine_adjust), Modifier.weight(1f))
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
    }
    if (expanded) {
        Text(stringResource(R.string.scan_crop_accessibility), style = MaterialTheme.typography.bodySmall)
        val corners = listOf(R.string.scan_corner_top_left, R.string.scan_corner_top_right,
            R.string.scan_corner_bottom_right, R.string.scan_corner_bottom_left)
        Box {
            TextButton(onClick = { cornersOpen = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(corners[state.corner]))
                Icon(Icons.Outlined.ExpandMore, null)
            }
            DropdownMenu(expanded = cornersOpen, onDismissRequest = { cornersOpen = false }) {
                corners.forEachIndexed { index, label ->
                    DropdownMenuItem(text = { Text(stringResource(label)) }, enabled = enabled,
                        onClick = { model.selectCorner(index); cornersOpen = false },
                        trailingIcon = { if (state.corner == index) Icon(Icons.Outlined.Check, null) },
                        modifier = Modifier.semantics { selected = state.corner == index })
                }
            }
        }
        val point = state.crop.points[state.corner]
        Text(stringResource(R.string.scan_corner_position, (point.x * 100).roundToInt(), (point.y * 100).roundToInt()),
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { model.moveCorner(-0.01f, 0f) }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.scan_move_left))
            }
            IconButton(onClick = { model.moveCorner(0f, -0.01f) }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.scan_move_up))
            }
            IconButton(onClick = { model.moveCorner(0f, 0.01f) }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.scan_move_down))
            }
            IconButton(onClick = { model.moveCorner(0.01f, 0f) }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.scan_move_right))
            }
        }
    }
}

@Composable
private fun FilterControls(model: EditorViewModel, state: EditorState, enabled: Boolean) {
    val labels = listOf(R.string.scan_original, R.string.scan_color, R.string.scan_grayscale, R.string.scan_black_white)
    LazyRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(ScanFilter.entries) { index, filter ->
            val active = state.filter == filter
            Surface(onClick = { model.setFilter(filter) }, enabled = enabled, shape = RoundedCornerShape(12.dp),
                color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.width(92.dp).semantics { selected = active }) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.TopEnd) {
                        val bitmap = state.filterThumbnails.getOrNull(index)
                        if (bitmap != null) Image(remember(bitmap) { bitmap.asImageBitmap() }, null,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        if (active) Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary) {
                            Icon(Icons.Outlined.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(stringResource(labels[index]), style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = state.showOriginal, enabled = enabled, role = Role.Checkbox, onValueChange = model::showOriginal),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = state.showOriginal, onCheckedChange = null, enabled = enabled)
        Text(stringResource(R.string.scan_show_original), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp))
    }
}
