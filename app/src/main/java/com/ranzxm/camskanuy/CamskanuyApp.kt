package com.ranzxm.camskanuy

import android.app.Application
import android.util.Log
import com.ranzxm.camskanuy.data.DocumentExporter
import com.ranzxm.camskanuy.data.DocumentRepository
import com.ranzxm.camskanuy.scanner.ImageProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CamskanuyApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository by lazy { DocumentRepository(this) }
    val exporter by lazy { DocumentExporter(this) }
    val imageProcessor by lazy { ImageProcessor(this) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            try {
                repository.reconcileFiles()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Log.w("CamskanuyStorage", "Startup storage reconciliation failed; retrying on next launch")
            }
        }
    }
}
