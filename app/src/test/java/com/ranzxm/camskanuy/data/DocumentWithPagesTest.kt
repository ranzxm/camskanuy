package com.ranzxm.camskanuy.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentWithPagesTest {
    private val document = DocumentEntity("document", "Scan", 1L, 1L)

    @Test
    fun pagesAreReturnedInExportOrderWithoutMutatingInput() {
        val input = listOf(page("third", 2), page("first", 0), page("second", 1))
        val record = DocumentWithPages(document, input)

        assertEquals(listOf("first", "second", "third"), record.sortedPages.map { it.id })
        assertEquals(listOf("third", "first", "second"), input.map { it.id })
    }

    @Test
    fun emptyDraftHasNoPages() {
        assertEquals(emptyList<PageEntity>(), DocumentWithPages(document, emptyList()).sortedPages)
    }

    private fun page(id: String, position: Int) = PageEntity(
        id = id,
        documentId = document.id,
        position = position,
        originalPath = "source",
        processedPath = "rendered",
        thumbnailPath = "thumbnail",
        crop = "0,0;1,0;1,1;0,1",
        rotation = 0,
        filter = "ORIGINAL",
    )
}
