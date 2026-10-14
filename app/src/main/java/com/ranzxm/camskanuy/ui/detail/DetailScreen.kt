package com.ranzxm.camskanuy.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.data.DocumentWithPages
import com.ranzxm.camskanuy.ui.common.AsyncDocumentImage
import com.ranzxm.camskanuy.ui.common.DocumentUiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    model: DocumentUiViewModel,
    documentId: String,
    onBack: () -> Unit,
    onActions: (DocumentWithPages) -> Unit,
    onEdit: () -> Unit,
    onExport: (Boolean) -> Unit,
) {
    val documents by model.documents.collectAsStateWithLifecycle()
    val pageIndex by model.pageIndex.collectAsStateWithLifecycle()
    val working by model.working.collectAsStateWithLifecycle()
    val pendingExport by model.pendingExport.collectAsStateWithLifecycle()
    val document = documents?.find { it.document.id == documentId }
    val pages = document?.sortedPages.orEmpty()
    val index = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val currentPage = pages.getOrNull(index)
    val enabled = !working && pendingExport == null
    val thumbnailState = rememberLazyListState()
    LaunchedEffect(documentId, index, pages.size) {
        if (document != null && pageIndex != index) model.setPageIndex(index)
        if (pages.isNotEmpty()) thumbnailState.animateScrollToItem(index)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(document?.document?.name ?: stringResource(R.string.compose_preview_title),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (document != null) Text(stringResource(R.string.doc_page_count, pages.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = enabled) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.doc_back))
                    }
                },
                actions = {
                    if (document != null) {
                        IconButton(onClick = { onActions(document) }, enabled = enabled) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.doc_document_options, document.document.name))
                        }
                    }
                },
            )
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                if (currentPage != null) AsyncDocumentImage(
                    currentPage.processedPath,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                     description = stringResource(R.string.doc_preview, index + 1),
                     zoomable = true,
                ) else if (documents != null) Text(
                    stringResource(if (document == null) R.string.doc_missing else R.string.doc_empty_pages),
                    modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (currentPage != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { model.setPageIndex(index - 1) }, enabled = enabled && index > 0) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.doc_previous))
                    }
                    Text(stringResource(R.string.doc_page_indicator, index + 1, pages.size),
                        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { model.setPageIndex(index + 1) }, enabled = enabled && index < pages.lastIndex) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.doc_next))
                    }
                }
                if (pages.size > 1) LazyRow(
                    state = thumbnailState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(pages, key = { _, page -> page.id }) { position, page ->
                        val label = stringResource(R.string.doc_page_number, position + 1)
                        Surface(
                            onClick = { model.setPageIndex(position) }, enabled = enabled,
                            modifier = Modifier.size(52.dp, 68.dp).semantics {
                                selected = position == index
                                contentDescription = label
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (position == index) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (position == index) androidx.compose.foundation.BorderStroke(
                                2.dp, MaterialTheme.colorScheme.primary,
                            ) else null,
                        ) {
                            AsyncDocumentImage(page.thumbnailPath, Modifier.padding(5.dp), maxSize = 200)
                        }
                    }
                }
                TextButton(
                    onClick = onEdit, enabled = enabled,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.doc_edit_pages))
                }
                Surface(tonalElevation = 2.dp) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(
                            onClick = { onExport(false) }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp),
                        ) {
                            Icon(Icons.Outlined.FileDownload, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.doc_export))
                        }
                        TextButton(onClick = { onExport(true) }, enabled = enabled) {
                            Icon(Icons.Outlined.Share, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.doc_share))
                        }
                    }
                }
            }
        }
    }
}
