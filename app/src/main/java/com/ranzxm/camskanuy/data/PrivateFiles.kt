package com.ranzxm.camskanuy.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

/** Shared by repository instances and exporters, including file reads during export. */
internal object DocumentStorageLock {
    val mutex = Mutex()
}

internal class PrivateFiles(context: Context) {
    private val context = context.applicationContext

    private fun documentsRoot(): File = safeChild(context.filesDir.canonicalFile, "documents")

    fun documentDirectory(documentId: String): File {
        require(UUID.fromString(documentId).toString() == documentId) { "Invalid document ID" }
        return safeChild(documentsRoot(), documentId)
    }

    fun newDocumentFile(documentId: String): File {
        val directory = documentDirectory(documentId)
        makeDirectory(directory)
        return safeChild(directory, "${UUID.randomUUID()}.jpg").also {
            if (!it.createNewFile()) throw IOException("Private file already exists")
        }
    }

    fun documentFile(documentId: String, path: String): File {
        val directory = documentDirectory(documentId)
        val file = File(path)
        require(file.isAbsolute && file.parentFile == directory && file.canonicalFile == file) {
            "Invalid private file reference"
        }
        require(file.name.endsWith(".jpg")) { "Invalid private file type" }
        require(UUID.fromString(file.name.removeSuffix(".jpg")).toString() == file.name.removeSuffix(".jpg")) {
            "Invalid private file name"
        }
        return file
    }

    fun sharedFile(path: String): File {
        val directory = safeChild(context.cacheDir.canonicalFile, "shared")
        val file = File(path)
        require(file.isAbsolute && file.parentFile == directory && file.canonicalFile == file) {
            "Invalid export file reference"
        }
        require(file.extension in setOf("jpg", "pdf")) { "Invalid export type" }
        require(UUID.fromString(file.nameWithoutExtension).toString() == file.nameWithoutExtension) {
            "Invalid export file name"
        }
        return file
    }

    fun newExport(extension: String): File {
        require(extension == "jpg" || extension == "pdf")
        val directory = safeChild(context.cacheDir.canonicalFile, "shared")
        makeDirectory(directory)
        return safeChild(directory, "${UUID.randomUUID()}.$extension")
    }

    fun cleanupExpiredExports() {
        val directory = safeChild(context.cacheDir.canonicalFile, "shared")
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() in 1L until cutoff) {
                cleanupExport(file)
            }
        }
    }

    fun cleanupUnreferencedDocumentFiles(referenced: Set<String>) {
        documentsRoot().listFiles()?.forEach { directory ->
            bestEffortCleanup {
                val validated = documentDirectory(directory.name)
                require(directory == validated && directory.isDirectory) { "Invalid document directory" }
                val unreferenced = directory.listFiles().orEmpty()
                    .filter { it.absolutePath !in referenced }
                    .map { it.absolutePath }
                cleanupDocumentFiles(directory.name, unreferenced)
            }
        }
    }

    suspend fun copy(source: File, destination: File) {
        require(source.isFile && source.length() > 0) { "Missing or empty page input" }
        check(destination.isFile && destination.length() == 0L) { "Destination is not an empty reserved file" }
        source.inputStream().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        if (destination.length() == 0L) throw IOException("Empty page input")
    }

    /** Only exact, validated paths are deleted. Cleanup failure never hides a committed edit. */
    fun cleanupDocumentFiles(documentId: String, paths: Collection<String>) {
        paths.distinct().forEach { path ->
            bestEffortCleanup { deleteFile(documentFile(documentId, path)) }
        }
        bestEffortCleanup {
            val directory = documentDirectory(documentId)
            if (directory.isDirectory && directory.list()?.isEmpty() == true && !directory.delete()) {
                throw IOException("Unable to remove empty document directory")
            }
        }
    }

    fun cleanupExport(file: File) {
        bestEffortCleanup { deleteFile(sharedFile(file.absolutePath)) }
    }

    private fun deleteFile(file: File) {
        if (file.exists() && (!file.isFile || !file.delete())) {
            throw IOException("Unable to remove private file")
        }
    }

    private fun bestEffortCleanup(action: () -> Unit) {
        try {
            action()
        } catch (_: Exception) {
            // Do not log exception messages: filesystem exceptions can contain document paths.
            Log.w("CamskanuyStorage", "Private-file cleanup failed; an unreferenced file may remain")
        }
    }

    private fun safeChild(parent: File, name: String): File {
        val file = File(parent, name)
        require(file.canonicalFile == file && file.parentFile == parent) { "Unsafe private directory" }
        return file
    }

    private fun makeDirectory(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create private directory")
    }
}
