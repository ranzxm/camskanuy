package com.ranzxm.camskanuy

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
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
        val mode = getSharedPreferences("appearance", MODE_PRIVATE).getString("mode", "system")
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )
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
