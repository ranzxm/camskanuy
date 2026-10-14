package com.ranzxm.camskanuy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ranzxm.camskanuy.data.DocumentExporter
import com.ranzxm.camskanuy.data.DocumentRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentStorageTest {
    private lateinit var context: Context
    private lateinit var repository: DocumentRepository
    private lateinit var source: File
    private val documents = mutableListOf<String>()

    @Before
    fun prepareImage() {
        context = ApplicationProvider.getApplicationContext()
        repository = DocumentRepository(context)
        source = File.createTempFile("storage-test-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    @After
    fun cleanUp() = runBlocking {
        documents.forEach { id ->
            if (repository.getDocument(id) != null) repository.deleteDocument(id)
        }
        if (::source.isInitialized) source.delete()
    }

    @Test
    fun persistedPagesReorderExportAndBecomeAnEmptyDraft() = runBlocking {
        val id = repository.createDraft().also(documents::add)
        val first = addPage(id)
        val second = addPage(id)
        repository.saveDocument(id, "  Test document  ")
        repository.reorderPages(id, listOf(second, first))

        val reopened = requireNotNull(DocumentRepository(context).getDocument(id))
        assertEquals("Test document", reopened.document.name)
        assertFalse(reopened.document.isDraft)
        assertEquals(listOf(second, first), reopened.sortedPages.map { it.id })
        assertEquals(listOf(0, 1), reopened.sortedPages.map { it.position })

        val exporter = DocumentExporter(context)
        val pdf = exporter.createPdf(reopened)
        val jpg = exporter.createJpg(reopened.sortedPages.first())
        try {
            val header = ByteArray(5)
            pdf.inputStream().use { assertEquals(5, it.read(header)) }
            assertEquals("%PDF-", header.toString(Charsets.US_ASCII))
            assertTrue(jpg.length() > 0)
        } finally {
            pdf.delete()
            jpg.delete()
        }

        repository.deletePage(first)
        assertEquals(0, requireNotNull(repository.getDocument(id)).sortedPages.single().position)
        repository.deletePage(second)
        val empty = requireNotNull(repository.getDocument(id))
        assertTrue(empty.document.isDraft)
        assertTrue(empty.pages.isEmpty())
        assertTrue(source.isFile)
    }

    @Test
    fun deletingFolderUnfilesDocumentsWithoutDeletingTheirPages() = runBlocking {
        val id = repository.createDraft().also(documents::add)
        val page = addPage(id)
        val folder = repository.createFolder("Storage test folder")
        try {
            repository.moveDocument(id, folder)
            assertEquals(folder, requireNotNull(repository.getDocument(id)).document.folderId)
        } finally {
            repository.deleteFolder(folder)
        }
        assertNull(requireNotNull(repository.getDocument(id)).document.folderId)
        assertNotNull(repository.getPage(page))
    }

    @Test
    fun reconciliationDeletesOnlyUnreferencedImages() = runBlocking {
        val id = repository.createDraft().also(documents::add)
        val page = requireNotNull(repository.getPage(addPage(id)))
        val orphan = File(File(page.originalPath).parentFile, "${UUID.randomUUID()}.jpg")
        source.copyTo(orphan)

        repository.reconcileFiles()

        assertFalse(orphan.exists())
        assertTrue(File(page.originalPath).isFile)
        assertTrue(File(page.processedPath).isFile)
        assertTrue(File(page.thumbnailPath).isFile)
        assertNotNull(repository.getPage(page.id))
    }

    @Test
    fun invalidReplacementPreservesSavedPageAndRejectsBadOrder() = runBlocking {
        val id = repository.createDraft().also(documents::add)
        val page = requireNotNull(repository.getPage(addPage(id)))
        val invalid = File.createTempFile("invalid-image-", ".jpg", context.cacheDir)
        try {
            invalid.writeText("not an image")
            var replacementRejected = false
            try {
                repository.updatePage(page.id, invalid, source, page.crop, 0, "ORIGINAL")
            } catch (_: java.io.IOException) {
                replacementRejected = true
            }
            assertTrue(replacementRejected)
            assertEquals(page, repository.getPage(page.id))
            assertTrue(File(page.originalPath).isFile)
            assertTrue(File(page.processedPath).isFile)

            var orderRejected = false
            try {
                repository.reorderPages(id, listOf(page.id, page.id))
            } catch (_: IllegalArgumentException) {
                orderRejected = true
            }
            assertTrue(orderRejected)
            assertEquals(listOf(page.id), requireNotNull(repository.getDocument(id)).sortedPages.map { it.id })
        } finally {
            invalid.delete()
        }
    }

    private suspend fun addPage(documentId: String): String = repository.addPage(
        documentId = documentId,
        source = source,
        rendered = source,
        thumbnail = source,
        crop = "0,0;1,0;1,1;0,1",
        rotation = 0,
        filter = "ORIGINAL",
    )
}
