package com.ranzxm.camskanuy.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Transaction
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC, createdAt DESC, id ASC")
    fun observeDocuments(): Flow<List<DocumentWithPages>>

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE, id")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocument(id: String): DocumentWithPages?

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPage(id: String): PageEntity?

    @Query("SELECT * FROM pages")
    suspend fun getAllPages(): List<PageEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolder(id: String): FolderEntity?

    @Insert
    suspend fun insertDocument(document: DocumentEntity)

    @Insert
    suspend fun insertPage(page: PageEntity)

    @Insert
    suspend fun insertFolder(folder: FolderEntity)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePage(id: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("UPDATE documents SET folderId = NULL, updatedAt = MAX(updatedAt + 1, :now) WHERE folderId = :folderId")
    suspend fun unfileDocuments(folderId: String, now: Long)
}

@Database(
    entities = [DocumentEntity::class, PageEntity::class, FolderEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "camskanuy.db",
            ).build().also { instance = it }
        }
    }
}
