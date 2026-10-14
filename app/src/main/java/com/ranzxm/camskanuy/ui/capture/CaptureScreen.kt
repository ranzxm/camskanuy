package com.ranzxm.camskanuy.ui.capture

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ranzxm.camskanuy.R

@Composable
fun CaptureScreen(
    model: CaptureViewModel,
    onBack: () -> Unit,
    onEditor: (String?, ArrayList<String>?) -> Unit,
    onPages: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentOnEditor by rememberUpdatedState(onEditor)
    val preferences = remember(context) { context.getSharedPreferences("scanner-permissions", Context.MODE_PRIVATE) }
    fun cameraGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(cameraGranted()) }
    var permissionAsked by remember { mutableStateOf(preferences.getBoolean("cameraAsked", false)) }
    var cameraWanted by remember(model) { mutableStateOf(model.cameraWanted) }
    var flashOn by remember(model) { mutableStateOf(model.flashOn) }
    var bindAttempt by remember { mutableIntStateOf(0) }
    var bindingCamera by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val previewView = remember(context) {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        if (!allowed) model.error(R.string.scan_camera_permission)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) {
            val selected = uris.take(20)
            selected.forEach { uri ->
                try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                catch (_: SecurityException) {
                    // Transient providers still permit immediate import; the editor exposes Retry/Skip on expiry.
                }
            }
            model.selectImages(ArrayList(selected.map(Uri::toString)))
        }
    }
    val openGallery: () -> Unit = {
        if (!model.state.value.busy) {
            try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            catch (_: ActivityNotFoundException) { model.error(R.string.scan_picker_failed) }
        }
    }
    val activity = remember(context) { context.captureActivity() }
    val permanentlyDenied = !granted && permissionAsked && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    val enableCamera: () -> Unit = {
        if (!model.state.value.busy) {
            cameraWanted = true
            model.cameraWanted = true
            model.error(null)
            when {
                cameraGranted() -> { granted = true; bindAttempt++ }
                permanentlyDenied -> {
                    try {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")))
                    } catch (_: ActivityNotFoundException) { model.error(R.string.scan_camera_permission) }
                }
                else -> {
                    permissionAsked = true
                    preferences.edit().putBoolean("cameraAsked", true).apply()
                    permission.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }

    DisposableEffect(owner, context, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = cameraGranted()
                model.refreshPages()
            }
        }
        owner.lifecycle.addObserver(observer)
        model.refreshPages()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(owner, previewView, granted, cameraWanted, bindAttempt) {
        // Each effect instance is one binding generation; a late future cannot bind after disposal.
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var ownedPreview: Preview? = null
        var ownedCapture: ImageCapture? = null
        camera = null
        imageCapture = null
        bindingCamera = granted && cameraWanted
        if (granted && cameraWanted) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed && owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                    try {
                        val available = future.get()
                        provider = available
                        check(available.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0).build()
                        ownedPreview = preview
                        ownedCapture = capture
                        val bound = available.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                        camera = bound
                        imageCapture = capture
                        if (!bound.cameraInfo.hasFlashUnit()) { flashOn = false; model.flashOn = false }
                        capture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        model.error(null)
                    } catch (_: Exception) {
                        ownedPreview?.let { provider?.unbind(it) }
                        ownedCapture?.let { provider?.unbind(it) }
                        camera = null
                        imageCapture = null
                        model.error(R.string.scan_camera_unavailable)
                    }
                    bindingCamera = false
                }
            }, executor)
        }
        onDispose {
            disposed = true
            ownedPreview?.let { provider?.unbind(it) }
            ownedCapture?.let { provider?.unbind(it) }
            camera = null
            imageCapture = null
            bindingCamera = false
        }
    }
    LaunchedEffect(model) {
        if (!model.initialHandled && model.state.value.sourcePath == null && model.state.value.inputUris == null) {
            model.initialHandled = true
            if (model.galleryInitially) openGallery() else enableCamera()
        }
    }
    LaunchedEffect(state.sourcePath, state.inputUris) {
        if (state.sourcePath != null || state.inputUris != null) {
            val path = state.sourcePath
            val uris = state.inputUris
            model.consumedNavigation()
            currentOnEditor(path, uris)
        }
    }
    BackHandler { if (!model.state.value.busy) onBack() }

    val ready = granted && camera != null && imageCapture != null
    val flashAvailable = camera?.cameraInfo?.hasFlashUnit() == true
    val shutterLabel = stringResource(R.string.scan_shutter)
    val previewColor = MaterialTheme.colorScheme.scrim
    Box(Modifier.fillMaxSize().background(previewColor)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().background(previewColor.copy(alpha = 0.72f)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (!model.state.value.busy) onBack() }, enabled = !state.busy) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.scan_back), tint = Color.White)
                }
                Text(stringResource(R.string.scan_capture_title), color = Color.White,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                IconButton(onClick = {
                    flashOn = !flashOn
                    model.flashOn = flashOn
                    imageCapture?.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                }, enabled = ready && flashAvailable && !state.busy) {
                    Icon(if (flashOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        stringResource(when { !flashAvailable -> R.string.scan_flash_unavailable
                            flashOn -> R.string.scan_flash_on; else -> R.string.scan_flash_off }),
                        tint = Color.White.copy(alpha = if (flashAvailable) 1f else 0.45f))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (ready) CameraFramingGuide(Modifier.fillMaxSize().padding(32.dp))
                if (!ready && !bindingCamera) {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(state.error ?: R.string.scan_camera_permission),
                                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            Button(onClick = enableCamera, enabled = !state.busy) {
                                Text(stringResource(if (permanentlyDenied) R.string.scan_open_settings else R.string.scan_allow_camera))
                            }
                        }
                    }
                }
                if (bindingCamera || state.busy) CircularProgressIndicator(color = Color.White)
            }
            Column(Modifier.fillMaxWidth().background(previewColor.copy(alpha = 0.8f)).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (ready || state.busy) Text(stringResource(when {
                    state.busy -> R.string.scan_processing
                    state.error != null -> state.error ?: R.string.scan_capture_failed
                    else -> R.string.scan_camera_guide
                }), color = Color.White, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = openGallery, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.PhotoLibrary, null, tint = Color.White)
                            Text(stringResource(R.string.scan_gallery), color = Color.White, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(onClick = {
                        imageCapture?.let { capture ->
                            capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                            model.capture(capture, executor)
                        }
                    }, enabled = ready && !state.busy, color = Color.White, shape = CircleShape,
                        modifier = Modifier.size(76.dp).border(4.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .semantics { contentDescription = shutterLabel; role = Role.Button }) {
                        Box(Modifier.padding(6.dp).border(2.dp, previewColor, CircleShape))
                    }
                    Spacer(Modifier.width(8.dp))
                    if (state.hasPages) TextButton(onClick = { if (!model.state.value.busy) onPages() },
                        enabled = !state.busy, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Collections, null, tint = Color.White)
                            Text(stringResource(R.string.scan_pages), color = Color.White, textAlign = TextAlign.Center)
                        }
                    } else Spacer(Modifier.weight(1f).heightIn(min = 48.dp))
                }
            }
        }
    }
}

@Composable
private fun CameraFramingGuide(modifier: Modifier) {
    Canvas(modifier) {
        val left = size.width * 0.08f
        val right = size.width * 0.92f
        val top = size.height * 0.1f
        val bottom = size.height * 0.9f
        val arm = 24.dp.toPx()
        val stroke = 2.dp.toPx()
        listOf(Offset(left, top), Offset(right, top), Offset(right, bottom), Offset(left, bottom)).forEachIndexed { index, corner ->
            val dx = if (index == 0 || index == 3) arm else -arm
            val dy = if (index < 2) arm else -arm
            drawLine(Color.White.copy(alpha = 0.65f), corner, corner + Offset(dx, 0f), stroke)
            drawLine(Color.White.copy(alpha = 0.65f), corner, corner + Offset(0f, dy), stroke)
        }
    }
}

private tailrec fun Context.captureActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.captureActivity()
    else -> null
}
