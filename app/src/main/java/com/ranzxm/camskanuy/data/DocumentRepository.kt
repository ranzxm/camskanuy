package com.ranzxm.camskanuy.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.withTransaction
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DocumentRepository(context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val dao = database.documentDao()
    private val files = PrivateFiles(context)

    fun observeDocuments(): Flow<List<DocumentWithPages>> = dao.observeDocuments()

    fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

    suspend fun getDocument(id: String): DocumentWithPages? = serialized { dao.getDocument(id) }

    suspend fun getPage(id: String): PageEntity? = serialized { dao.getPage(id) }

    /** Recover from process death between a file write/delete and its database transaction. */
    suspend fun reconcileFiles(): Unit = serialized {
        val referenced = dao.getAllPages().flatMap { it.paths() }.toSet()
        files.cleanupUnreferencedDocumentFiles(referenced)
        files.cleanupExpiredExports()
    }

    suspend fun createDraft(): String = serialized {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val name = "Scan " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm"))
        database.withTransaction {
            dao.insertDocument(DocumentEntity(id, name, now, now))
        }
        id
    }

    suspend fun addPage(
        documentId: String,
        source: File,
        rendered: File,
        thumbnail: File,
        crop: String,
        rotation: Int,
        filter: String,
    ): String = serialized {
        validateParameters(crop, rotation, filter)
        val document = requireDocument(documentId)
        val pageId = UUID.randomUUID().toString()
        val newPaths = mutableListOf<String>()
        var committed = false
        try {
            val original = copyImage(documentId, source, newPaths)
            val processed = copyImage(documentId, rendered, newPaths)
            val small = copyImage(documentId, thumbnail, newPaths)
            val page = PageEntity(
                id = pageId,
                documentId = documentId,
                position = document.pages.size,
                originalPath = original,
                processedPath = processed,
                thumbnailPath = small,
                crop = crop,
                rotation = Math.floorMod(rotation, 360),
                filter = filter,
            )
            currentCoroutineContext().ensureActive()
            // Cancellation cannot separate a committed DB change from its file-ownership decision.
            withContext(NonCancellable) {
                database.withTransaction {
                    dao.insertPage(page)
                    dao.updateDocument(document.document.touched())
                }
                committed = true
            }
            pageId
        } finally {
            if (!committed) files.cleanupDocumentFiles(documentId, newPaths)
        }
    }

    suspend fun updatePage(
        pageId: String,
        rendered: File,
        thumbnail: File,
        crop: String,
        rotation: Int,
        filter: String,
    ): Unit = serialized {
        validateParameters(crop, rotation, filter)
        val page = requirePage(pageId)
        val document = requireDocument(page.documentId).document
        val newPaths = mutableListOf<String>()
        var committed = false
        try {
            val processed = copyImage(page.documentId, rendered, newPaths)
            val small = copyImage(page.documentId, thumbnail, newPaths)
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                database.withTransaction {
                    dao.updatePage(page.copy(
                        processedPath = processed,
                        thumbnailPath = small,
                        crop = crop,
                        rotation = Math.floorMod(rotation, 360),
                        filter = filter,
                    ))
                    dao.updateDocument(document.touched())
                }
                committed = true
                files.cleanupDocumentFiles(page.documentId, listOf(page.processedPath, page.thumbnailPath))
            }
        } finally {
            if (!committed) files.cleanupDocumentFiles(page.documentId, newPaths)
        }
    }

    suspend fun renameDocument(id: String, name: String): Unit = serialized {
        val trimmed = validatedName(name)
        database.withTransaction {
            dao.updateDocument(requireDocument(id).document.touched().copy(name = trimmed))
        }
    }

    suspend fun saveDocument(id: String, name: String): Unit = serialized {
        val trimmed = validatedName(name)
        database.withTransaction {
            val document = requireDocument(id)
            require(document.pages.isNotEmpty()) { "A saved document must contain a page" }
            dao.updateDocument(document.document.touched().copy(name = trimmed, isDraft = false))
        }
    }

    suspend fun deleteDocument(id: String): Unit = serialized {
        val document = requireDocument(id)
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            database.withTransaction { dao.deleteDocument(id) }
            files.cleanupDocumentFiles(id, document.pages.flatMap { it.paths() })
        }
    }

    suspend fun deletePage(pageId: String): Unit = serialized {
        val page = requirePage(pageId)
        val document = requireDocument(page.documentId)
        val remaining = document.sortedPages.filterNot { it.id == pageId }
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            database.withTransaction {
                dao.deletePage(pageId)
                remaining.forEachIndexed { index, item -> dao.updatePage(item.copy(position = index)) }
                dao.updateDocument(document.document.touched().copy(
                    isDraft = document.document.isDraft || remaining.isEmpty(),
                ))
            }
            files.cleanupDocumentFiles(page.documentId, page.paths())
        }
    }

    suspend fun reorderPages(documentId: String, pageIds: List<String>): Unit = serialized {
        database.withTransaction {
            val document = requireDocument(documentId)
            val pages = document.pages.associateBy { it.id }
            require(pageIds.size == pages.size && pageIds.toSet() == pages.keys) {
                "Page order must contain every document page exactly once"
            }
            pageIds.forEachIndexed { index, id ->
                dao.updatePage(pages.getValue(id).copy(position = index))
            }
            dao.updateDocument(document.document.touched())
        }
    }

    /** Duplicate display names are allowed; stable UUIDs identify folders. */
    suspend fun createFolder(name: String): String = serialized {
        val folder = FolderEntity(UUID.randomUUID().toString(), validatedName(name))
        database.withTransaction { dao.insertFolder(folder) }
        folder.id
    }

    suspend fun renameFolder(id: String, name: String): Unit = serialized {
        val trimmed = validatedName(name)
        database.withTransaction {
            val folder = requireNotNull(dao.getFolder(id)) { "Folder not found" }
            dao.updateFolder(folder.copy(name = trimmed))
        }
    }

    suspend fun deleteFolder(id: String): Unit = serialized {
        database.withTransaction {
            requireNotNull(dao.getFolder(id)) { "Folder not found" }
            dao.unfileDocuments(id, System.currentTimeMillis())
            dao.deleteFolder(id)
        }
    }

    suspend fun moveDocument(id: String, folderId: String?): Unit = serialized {
        database.withTransaction {
            if (folderId != null) requireNotNull(dao.getFolder(folderId)) { "Folder not found" }
            dao.updateDocument(requireDocument(id).document.touched().copy(folderId = folderId))
        }
    }

    private suspend fun copyImage(documentId: String, input: File, newPaths: MutableList<String>): String {
        val output = files.newDocumentFile(documentId)
        newPaths.add(output.absolutePath)
        files.copy(input, output)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(output.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth > 65_535 || bounds.outHeight > 65_535 ||
            bounds.outWidth.toLong() * bounds.outHeight > 200_000_000L
        ) {
            throw IOException("Unsupported or corrupt page image")
        }
        var sample = 1
        while ((maxOf(bounds.outWidth, bounds.outHeight) + sample - 1) / sample > 512) sample *= 2
        val preview = BitmapFactory.decodeFile(output.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
        }) ?: throw IOException("Cannot decode page image")
        preview.recycle()
        return output.absolutePath
    }

    private suspend fun requireDocument(id: String): DocumentWithPages =
        requireNotNull(dao.getDocument(id)) { "Document not found" }

    private suspend fun requirePage(id: String): PageEntity =
        requireNotNull(dao.getPage(id)) { "Page not found" }

    private fun validatedName(name: String): String = name.trim().also {
        require(it.isNotEmpty() && it.length <= 120) { "Name must contain 1 to 120 characters" }
    }

    private fun validateParameters(crop: String, rotation: Int, filter: String) {
        require(crop.isNotBlank() && crop.length <= 16_384) { "Invalid crop parameters" }
        require(rotation % 90 == 0) { "Rotation must be a multiple of 90 degrees" }
        require(filter.isNotBlank() && filter.length <= 120) { "Invalid filter" }
    }

    private fun DocumentEntity.touched(): DocumentEntity = copy(
        updatedAt = maxOf(System.currentTimeMillis(), updatedAt + 1),
    )

    private fun PageEntity.paths(): List<String> = listOf(originalPath, processedPath, thumbnailPath)

    private suspend fun <T> serialized(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        DocumentStorageLock.mutex.withLock { block() }
    }
}
