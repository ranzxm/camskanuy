package com.ranzxm.camskanuy.ui.capture

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.databinding.FragmentCaptureBinding
import kotlinx.coroutines.launch

class CaptureFragment : Fragment() {
    private var binding: FragmentCaptureBinding? = null
    private val model: CaptureViewModel by viewModels()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var bindingCamera = false
    private var cameraGeneration = 0

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else {
            model.error(R.string.scan_camera_permission)
            render()
        }
    }

    private val picker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) {
            val resolver = requireContext().contentResolver
            val selected = uris.take(20)
            selected.forEach { uri ->
                try { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                catch (_: SecurityException) {
                    // Some system/fallback providers grant transient access only. Immediate import
                    // still works; a later expired grant is surfaced by the editor with Retry/Skip.
                }
            }
            model.selectImages(ArrayList(selected.map(Uri::toString)))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentCaptureBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = requireNotNull(binding)
        ui.toolbar.setTitle(R.string.scan_capture_title)
        ui.toolbar.navigationIcon = AppCompatResources.getDrawable(requireContext(), androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        ui.toolbar.setNavigationContentDescription(R.string.scan_back)
        ui.toolbar.setNavigationOnClickListener { back() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { back() }
        ui.gallery.setOnClickListener { openGallery() }
        ui.enableCamera.setOnClickListener { enableCamera() }
        ui.flash.setOnClickListener {
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                model.flashOn = !model.flashOn
                imageCapture?.flashMode = if (model.flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                render()
            }
        }
        ui.shutter.setOnClickListener {
            imageCapture?.let { capture ->
                capture.targetRotation = ui.cameraPreview.display?.rotation ?: android.view.Surface.ROTATION_0
                model.capture(capture, ContextCompat.getMainExecutor(requireContext()))
            }
        }
        ui.pages.setOnClickListener { openPages() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    render()
                    if ((state.sourcePath != null || state.inputUris != null) &&
                        findNavController().currentDestination?.id == R.id.captureFragment) {
                        val args = bundleOf("documentId" to model.documentId,
                            "sourcePath" to state.sourcePath, "inputUris" to state.inputUris)
                        model.consumedNavigation()
                        findNavController().navigate(R.id.editorFragment, args)
                    }
                }
            }
        }
        if (!model.initialHandled) {
            model.initialHandled = true
            if (model.galleryInitially) openGallery() else enableCamera()
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        model.refreshPages()
        if (model.cameraWanted && cameraGranted() && !model.state.value.busy) startCamera()
        render()
    }

    private fun cameraGranted(): Boolean = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun permissionWasAsked(): Boolean = requireContext().getSharedPreferences("scanner-permissions", android.content.Context.MODE_PRIVATE)
        .getBoolean("cameraAsked", false)

    private fun permanentlyDenied(): Boolean = !cameraGranted() && permissionWasAsked() &&
        !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

    private fun enableCamera() {
        model.cameraWanted = true
        model.error(null)
        when {
            cameraGranted() -> startCamera()
            permanentlyDenied() -> {
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
                } catch (_: ActivityNotFoundException) { model.error(R.string.scan_camera_permission) }
            }
            else -> {
                requireContext().getSharedPreferences("scanner-permissions", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("cameraAsked", true).apply()
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
        render()
    }

    private fun openGallery() {
        if (model.state.value.busy) return
        try {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) { model.error(R.string.scan_picker_failed) }
    }

    private fun startCamera() {
        val ui = binding ?: return
        if (!cameraGranted() || bindingCamera || camera != null) return
        bindingCamera = true
        val generation = ++cameraGeneration
        val owner = viewLifecycleOwner
        val context = requireContext()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (generation != cameraGeneration || binding !== ui || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@addListener
            bindingCamera = false
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    model.error(R.string.scan_camera_unavailable)
                    render()
                    return@addListener
                }
                val cameraPreview = Preview.Builder().build().also { it.setSurfaceProvider(ui.cameraPreview.surfaceProvider) }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(ui.cameraPreview.display?.rotation ?: android.view.Surface.ROTATION_0).build()
                val activeCamera = cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, capture)
                preview = cameraPreview
                imageCapture = capture
                camera = activeCamera
                if (!activeCamera.cameraInfo.hasFlashUnit()) model.flashOn = false
                capture.flashMode = if (model.flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                model.error(null)
            } catch (_: Exception) {
                camera = null
                imageCapture = null
                model.error(R.string.scan_camera_unavailable)
            }
            render()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun render() {
        val ui = binding ?: return
        val state = model.state.value
        val ready = camera != null && cameraGranted()
        ui.status.setText(when {
            state.busy -> R.string.scan_processing
            state.error != null -> state.error
            ready -> R.string.scan_camera_guide
            else -> R.string.scan_camera_permission
        })
        ui.progress.isVisible = state.busy || bindingCamera
        ui.enableCamera.isVisible = !ready
        ui.enableCamera.isEnabled = !state.busy && !bindingCamera
        ui.enableCamera.setText(if (permanentlyDenied()) R.string.scan_open_settings else R.string.scan_allow_camera)
        ui.shutter.isEnabled = ready && !state.busy
        ui.gallery.isEnabled = !state.busy
        ui.pages.isVisible = state.hasPages
        ui.pages.isEnabled = !state.busy
        val flash = camera?.cameraInfo?.hasFlashUnit() == true
        ui.flash.isEnabled = ready && flash && !state.busy
        ui.flash.setText(when {
            !flash -> R.string.scan_flash_unavailable
            model.flashOn -> R.string.scan_flash_on
            else -> R.string.scan_flash_off
        })
    }

    private fun back() {
        if (model.state.value.busy) return
        // Persisted draft pages belong to the repository, not to this camera screen.
        if (!findNavController().popBackStack()) {
            findNavController().navigate(R.id.documentsFragment)
        }
    }

    private fun openPages() {
        findNavController().navigate(R.id.pagesFragment, bundleOf("documentId" to model.documentId),
            navOptions { popUpTo(R.id.documentsFragment) { inclusive = false } })
    }

    override fun onDestroyView() {
        cameraGeneration++
        bindingCamera = false
        preview?.let { provider?.unbind(it) }
        imageCapture?.let { provider?.unbind(it) }
        preview = null
        imageCapture = null
        camera = null
        provider = null
        binding = null
        super.onDestroyView()
    }
}
