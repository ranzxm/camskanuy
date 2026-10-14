package com.ranzxm.camskanuy.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(
        entity = FolderEntity::class,
        parentColumns = ["id"], childColumns = ["folderId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("folderId"), Index("updatedAt")],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val folderId: String? = null,
    val isDraft: Boolean = true,
)

@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"], childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["documentId", "position"])],
)
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val position: Int,
    val originalPath: String,
    val processedPath: String,
    val thumbnailPath: String,
    val crop: String,
    val rotation: Int,
    val filter: String,
)

data class DocumentWithPages(
    @Embedded val document: DocumentEntity,
    @Relation(parentColumn = "id", entityColumn = "documentId")
    val pages: List<PageEntity>,
) {
    val sortedPages: List<PageEntity> get() = pages.sortedBy { it.position }
}
