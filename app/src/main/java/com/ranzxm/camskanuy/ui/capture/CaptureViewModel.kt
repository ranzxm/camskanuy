package com.ranzxm.camskanuy.ui.capture

import android.app.Application
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ranzxm.camskanuy.CamskanuyApp
import com.ranzxm.camskanuy.R
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaptureState(
    val busy: Boolean = false,
    val error: Int? = null,
    val sourcePath: String? = null,
    val inputUris: ArrayList<String>? = null,
    val hasPages: Boolean = false,
)

class CaptureViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as CamskanuyApp
    val documentId: String = saved.get<String>("documentId").orEmpty()
    val galleryInitially: Boolean = saved.get<Boolean>("openGallery") ?: false
    var cameraWanted: Boolean
        get() = saved.get<Boolean>("cameraWanted") ?: !galleryInitially
        set(value) { saved["cameraWanted"] = value }
    var initialHandled: Boolean
        get() = saved.get<Boolean>("initialHandled") ?: false
        set(value) { saved["initialHandled"] = value }
    var flashOn: Boolean
        get() = saved.get<Boolean>("flashOn") ?: false
        set(value) { saved["flashOn"] = value }
    private val mutableState = MutableStateFlow(CaptureState(
        sourcePath = saved["capturedPath"], inputUris = saved["selectedUris"]))
    val state = mutableState.asStateFlow()

    fun refreshPages() {
        viewModelScope.launch {
            try {
                val document = app.repository.getDocument(documentId)
                mutableState.update { it.copy(hasPages = document?.sortedPages?.isNotEmpty() == true,
                    error = if (document == null) R.string.scan_missing_document else it.error) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error(R.string.scan_missing_document) }
        }
    }

    fun selectImages(uris: ArrayList<String>) {
        if (uris.isEmpty()) return
        saved["selectedUris"] = uris
        mutableState.update { it.copy(inputUris = uris, error = null) }
    }

    fun consumedNavigation() {
        saved.remove<String>("capturedPath")
        saved.remove<ArrayList<String>>("selectedUris")
        mutableState.update { it.copy(sourcePath = null, inputUris = null) }
    }

    fun error(message: Int?) { mutableState.update { it.copy(error = message) } }

    fun capture(capture: ImageCapture, executor: Executor) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            var file: java.io.File? = null
            try {
                val output = app.imageProcessor.newCaptureFile()
                file = output
                capture.takePicture(ImageCapture.OutputFileOptions.Builder(output).build(), executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            saved["capturedPath"] = output.absolutePath
                            mutableState.update { it.copy(busy = false, sourcePath = output.absolutePath) }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            mutableState.update { it.copy(busy = false, error = R.string.scan_capture_failed) }
                            viewModelScope.launch { cleanup(output) }
                        }
                    })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                cleanup(file)
                mutableState.update { it.copy(busy = false, error = R.string.scan_capture_failed) }
            }
        }
    }

    private suspend fun cleanup(file: java.io.File?) {
        try { app.imageProcessor.deleteTemporary(file) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error(R.string.scan_cleanup_failed) }
    }
}
