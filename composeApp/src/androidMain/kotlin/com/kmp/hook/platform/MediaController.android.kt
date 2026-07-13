package com.kmp.hook.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.kmp.hook.ui.components.CloseGlyph
import com.kmp.hook.ui.components.clickableNoRipple
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
actual fun rememberMediaController(
    onImagePicked: (ByteArray) -> Unit,
    onCameraDenied: () -> Unit,
): MediaController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCamera by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes != null) onImagePicked(normalizeJpeg(bytes, 0))
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showCamera = true else onCameraDenied() }

    val controller = remember {
        object : MediaController {
            override fun pickFromGallery() {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }

            override fun captureFromCamera() {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) showCamera = true
                else permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    if (showCamera) {
        CameraCaptureDialog(
            scope = scope,
            onCaptured = { bytes -> showCamera = false; onImagePicked(bytes) },
            onError = { showCamera = false; onCameraDenied() },
            onClose = { showCamera = false },
        )
    }

    return controller
}

@Composable
private fun CameraCaptureDialog(
    scope: CoroutineScope,
    onCaptured: (ByteArray) -> Unit,
    onError: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        runCatching {
                            val provider = future.get()
                            val preview = Preview.Builder().build()
                                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            val capture = ImageCapture.Builder().build()
                            imageCapture = capture
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture,
                            )
                        }.onFailure { onError() }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )

            // Close
            Box(
                Modifier.align(Alignment.TopStart).padding(16.dp)
                    .size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
                    .clickableNoRipple { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                CloseGlyph(Modifier.size(22.dp), tint = Color.White)
            }

            // Shutter
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
                    .size(74.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(Color.White)
                        .clickableNoRipple {
                            val capture = imageCapture ?: return@clickableNoRipple
                            runCatching {
                                capture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val rotation = image.imageInfo.rotationDegrees
                                            val bytes = image.toJpegBytes()
                                            image.close()
                                            scope.launch {
                                                val out = withContext(Dispatchers.Default) {
                                                    normalizeJpeg(bytes, rotation)
                                                }
                                                onCaptured(out)
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            onError()
                                        }
                                    },
                                )
                            }.onFailure { onError() }
                        },
                )
            }
        }
    }
}

private fun ImageProxy.toJpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}

/** Decode → apply rotation → re-encode JPEG so orientation is baked in and size is bounded. */
private fun normalizeJpeg(bytes: ByteArray, rotationDegrees: Int): ByteArray {
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val rotated = if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    } else decoded
    val out = ByteArrayOutputStream()
    rotated.compress(Bitmap.CompressFormat.JPEG, 88, out)
    return out.toByteArray()
}

actual fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
