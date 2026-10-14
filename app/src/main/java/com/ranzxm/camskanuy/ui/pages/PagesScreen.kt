package com.ranzxm.camskanuy.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.data.PageEntity
import com.ranzxm.camskanuy.ui.common.AsyncDocumentImage
import com.ranzxm.camskanuy.ui.common.DocumentUiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesScreen(
    model: DocumentUiViewModel,
    documentId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    onPageActions: (PageEntity) -> Unit,
) {
    val documents by model.documents.collectAsStateWithLifecycle()
    val working by model.working.collectAsStateWithLifecycle()
    val pendingExport by model.pendingExport.collectAsStateWithLifecycle()
    val document = documents?.find { it.document.id == documentId }
    val pages = document?.sortedPages.orEmpty()
    val enabled = !working && pendingExport == null
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doc_pages_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = enabled) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.doc_back))
                    }
                },
            )
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (document != null) item(key = "heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(document.document.name, style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.compose_pages_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (pages.isNotEmpty()) Text(stringResource(R.string.compose_pages_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (pages.isEmpty() && documents != null) item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(if (document == null) R.string.doc_missing else R.string.compose_pages_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        if (document != null) Text(stringResource(R.string.doc_empty_pages),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }
                }
                itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                    Card(
                        onClick = { onEdit(page.id) }, enabled = enabled,
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            AsyncDocumentImage(
                                page.thumbnailPath,
                                Modifier.size(width = 56.dp, height = 74.dp).clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                maxSize = 240,
                            )
                            Text(
                                stringResource(R.string.doc_page_number, index + 1),
                                Modifier.weight(1f).padding(horizontal = 12.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            IconButton(onClick = { onEdit(page.id) }, enabled = enabled) {
                                Icon(Icons.Outlined.Edit, stringResource(R.string.doc_edit))
                            }
                            IconButton(onClick = { onPageActions(page) }, enabled = enabled) {
                                Icon(Icons.Outlined.MoreVert, stringResource(R.string.doc_page_options, index + 1))
                            }
                        }
                    }
                }
            }
            if (document != null) Surface(tonalElevation = 2.dp) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (pages.isNotEmpty()) Text(
                        stringResource(R.string.doc_page_count, pages.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pages.isNotEmpty()) Button(
                        onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Icon(Icons.Outlined.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.doc_save_document))
                    }
                    if (pages.isEmpty()) Button(
                        onClick = onAdd, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.doc_add_page))
                    } else TextButton(onClick = onAdd, enabled = enabled) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.doc_add_page))
                    }
                }
            }
        }
    }
}
