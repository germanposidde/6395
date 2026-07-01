package com.kmp.hook.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Single shared entry point for camera capture and gallery picking.
 * The camera/gallery screens return raw JPEG [ByteArray]s (storable & cross-platform);
 * the caller decides what to do with them.
 */
interface MediaController {
    /** Open the system photo picker (no runtime permission required). */
    fun pickFromGallery()

    /** Capture a photo. Permission is resolved internally; denial routes to `onCameraDenied`. */
    fun captureFromCamera()
}

/**
 * Wires up the platform camera + gallery flows. All UI lives in commonMain; the platform
 * actuals only present the picker/preview and hand back JPEG bytes.
 *
 * @param onImagePicked receives raw JPEG bytes of a captured or picked image.
 * @param onCameraDenied invoked when the camera cannot be used (denied/unavailable);
 *   the caller shows the Close-only dialog.
 */
@Composable
expect fun rememberMediaController(
    onImagePicked: (ByteArray) -> Unit,
    onCameraDenied: () -> Unit,
): MediaController

/** Decode JPEG/PNG bytes into a Compose [ImageBitmap] for display, or null if undecodable. */
expect fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap?

@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.toBase64(): String = Base64.encode(this)

@OptIn(ExperimentalEncodingApi::class)
fun String.decodeBase64OrNull(): ByteArray? = runCatching { Base64.decode(this) }.getOrNull()
