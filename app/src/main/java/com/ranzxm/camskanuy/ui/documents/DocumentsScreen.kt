package com.ranzxm.camskanuy.ui.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.data.DocumentWithPages
import com.ranzxm.camskanuy.ui.common.AsyncDocumentImage
import com.ranzxm.camskanuy.ui.common.DocumentUiViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun DocumentsScreen(
    model: DocumentUiViewModel,
    onOpen: (DocumentWithPages) -> Unit,
    onActions: (DocumentWithPages) -> Unit,
    onFolders: () -> Unit,
    onAppearance: () -> Unit,
    onInfo: () -> Unit,
) {
    val documents by model.documents.collectAsStateWithLifecycle()
    val folders by model.folders.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val folder by model.folder.collectAsStateWithLifecycle()
    val working by model.working.collectAsStateWithLifecycle()
    val pendingExport by model.pendingExport.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val enabled = !working && pendingExport == null
    val visible = remember(documents, query, folder) {
        documents.orEmpty().filter { doc ->
            (!doc.document.isDraft || doc.pages.isNotEmpty()) &&
                doc.document.name.contains(query.trim(), ignoreCase = true) &&
                when (folder) {
                    "all" -> true
                    "unfiled" -> doc.document.folderId == null
                    else -> doc.document.folderId == folder
                }
        }.sortedWith(compareByDescending<DocumentWithPages> { it.document.isDraft }
            .thenByDescending { it.document.updatedAt })
    }
    val filtering = query.isNotBlank() || folder != "all"
    Scaffold(
        floatingActionButton = {
            if (visible.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = { if (enabled) model.createDraft(false) },
                modifier = Modifier.semantics { if (!enabled) disabled() },
                icon = { Icon(Icons.Outlined.CameraAlt, null) },
                text = { Text(stringResource(R.string.doc_scan)) },
                containerColor = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "heading") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.app_name),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(onClick = onAppearance, enabled = enabled) {
                            Icon(Icons.Outlined.Palette, stringResource(R.string.doc_appearance))
                        }
                        IconButton(onClick = onInfo) {
                            Icon(Icons.Outlined.Info, stringResource(R.string.doc_info))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Outlined.Shield, null, Modifier.size(14.dp))
                            Text(stringResource(R.string.compose_offline), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        stringResource(R.string.compose_documents_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.compose_documents_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = model::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.doc_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { model.setQuery("") }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.doc_clear_search))
                        } }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
            item(key = "folders") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = folder == "all", onClick = { model.setFolder("all") },
                            label = { Text(stringResource(R.string.compose_all)) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = folder == "unfiled", onClick = { model.setFolder("unfiled") },
                            label = { Text(stringResource(R.string.doc_unfiled)) },
                        )
                    }
                    items(folders, key = { it.id }) { entry ->
                        FilterChip(
                            selected = folder == entry.id, onClick = { model.setFolder(entry.id) },
                            label = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                    item {
                        TextButton(onClick = onFolders, enabled = enabled) {
                            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.doc_manage_folder))
                        }
                    }
                }
            }
            if (documents == null) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        if (!error) CircularProgressIndicator() else Text(
                            stringResource(R.string.doc_load_error), textAlign = TextAlign.Center,
                        )
                    }
                }
            } else if (visible.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        EmptyDocumentsIllustration()
                        Text(
                            stringResource(if (filtering) R.string.doc_no_results else R.string.compose_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(if (filtering) R.string.doc_no_results_body else R.string.compose_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (filtering) TextButton(onClick = { model.setQuery(""); model.setFolder("all") }) {
                            Text(stringResource(R.string.compose_reset_filters))
                        }
                        Button(
                            onClick = { model.createDraft(false) }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Icon(Icons.Outlined.CameraAlt, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.compose_scan))
                        }
                        TextButton(onClick = { model.createDraft(true) }, enabled = enabled) {
                            Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.doc_import))
                        }
                    }
                }
            } else {
                item(key = "list_heading") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.doc_title), Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { model.createDraft(true) }, enabled = enabled) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.compose_import_short))
                        }
                    }
                }
                items(visible, key = { it.document.id }) { doc ->
                    DocumentCard(doc, enabled, { onOpen(doc) }, { onActions(doc) })
                }
            }
        }
    }
}

@Composable
private fun DocumentCard(document: DocumentWithPages, enabled: Boolean, open: () -> Unit, actions: () -> Unit) {
    val draft = document.document.isDraft
    val date = remember(document.document.updatedAt) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(document.document.updatedAt))
    }
    Card(
        onClick = open, enabled = enabled,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (draft) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AsyncDocumentImage(
                document.sortedPages.firstOrNull()?.thumbnailPath,
                Modifier.size(width = 58.dp, height = 76.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                maxSize = 240,
            )
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (draft) Text(
                    stringResource(R.string.compose_resume_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(document.document.name, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (draft) stringResource(R.string.compose_resume_body, document.pages.size)
                    else stringResource(R.string.doc_metadata, document.pages.size, date),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (draft) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (draft) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.doc_resume), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp))
                }
            }
            IconButton(onClick = actions, enabled = enabled) {
                Icon(Icons.Outlined.MoreVert, stringResource(R.string.doc_document_options, document.document.name))
            }
        }
    }
}

@Composable
private fun EmptyDocumentsIllustration() {
    Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(82.dp, 100.dp).rotate(-12f).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )
        Surface(
            modifier = Modifier.size(82.dp, 100.dp).rotate(7f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Description, null, Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
