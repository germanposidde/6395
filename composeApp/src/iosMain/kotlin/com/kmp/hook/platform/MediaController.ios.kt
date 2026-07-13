package com.kmp.hook.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSItemProvider
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_SEC
import platform.darwin.NSObject
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.posix.memcpy

@Composable
actual fun rememberMediaController(
    onImagePicked: (ByteArray) -> Unit,
    onCameraDenied: () -> Unit,
): MediaController {
    // Delegates must outlive the presentation (UIKit holds them weakly).
    val galleryDelegate = remember { GalleryPickerDelegate(onImagePicked) }
    val cameraDelegate = remember { CameraPickerDelegate(onImagePicked) }
    // Capture the app's main window NOW, before any transient chooser-dialog window exists, and
    // always present from it. This avoids presenting from the dialog's window while it tears down.
    val hostWindow = remember { currentAppWindow() }

    return remember {
        object : MediaController {
            override fun pickFromGallery() = presentGallery(galleryDelegate, hostWindow)
            override fun captureFromCamera() = requestCamera(cameraDelegate, onCameraDenied, hostWindow)
        }
    }
}

// ---- Gallery (PHPickerViewController — no permission prompt) ----

private class GalleryPickerDelegate(
    private val onImagePicked: (ByteArray) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        val provider: NSItemProvider = result.itemProvider
        val typeId = (provider.registeredTypeIdentifiers.firstOrNull() as? String) ?: "public.image"
        provider.loadDataRepresentationForTypeIdentifier(typeId) { data, _ ->
            val bytes = data?.let { normalizeToJpeg(it) } ?: return@loadDataRepresentationForTypeIdentifier
            dispatch_async(dispatch_get_main_queue()) { onImagePicked(bytes) }
        }
    }
}

private fun presentGallery(delegate: PHPickerViewControllerDelegateProtocol, host: UIWindow?) {
    val config = PHPickerConfiguration()
    config.selectionLimit = 1
    config.filter = PHPickerFilter.imagesFilter()
    val picker = PHPickerViewController(configuration = config)
    picker.delegate = delegate
    present(picker, host)
}

// ---- Camera (UIImagePickerController gated by AVCaptureDevice authorization) ----

private class CameraPickerDelegate(
    private val onImagePicked: (ByteArray) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
        val data = UIImageJPEGRepresentation(image, 0.88) ?: return
        val bytes = data.toByteArray()
        dispatch_async(dispatch_get_main_queue()) { onImagePicked(bytes) }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

private fun requestCamera(delegate: CameraPickerDelegate, onDenied: () -> Unit, host: UIWindow?) {
    when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> openCamera(delegate, onDenied, host)
        AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            dispatch_async(dispatch_get_main_queue()) {
                if (granted) openCamera(delegate, onDenied, host) else onDenied()
            }
        }
        else -> onDenied()
    }
}

private fun openCamera(delegate: CameraPickerDelegate, onDenied: () -> Unit, host: UIWindow?) {
    // Simulator (and camera-less devices) have no camera source — show the dialog instead of crashing.
    if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
        onDenied()
        return
    }
    val picker = UIImagePickerController()
    picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
    picker.delegate = delegate
    present(picker, host)
}

// ---- Presentation (see the Compose-Dialog-on-iOS trap) ----
//
// The chooser AlertDialog is hosted in its own transient UIWindow. Tapping "Take photo" dismisses
// it and immediately asks us to present a UIKit picker. Resolving `keyWindow` at that moment often
// returns the dialog's window while it's tearing down, so the picker is presented from a window
// that's being removed and never appears — the classic "camera works exactly once", which
// resurfaces after a delete/replace cycle. Instead we always present from the app's MAIN window,
// captured up-front in rememberMediaController (before any dialog window exists).

private fun currentAppWindow(): UIWindow? {
    val app = UIApplication.sharedApplication
    @Suppress("DEPRECATION")
    return app.keyWindow ?: app.windows.firstOrNull() as? UIWindow
}

private fun present(vc: UIViewController, host: UIWindow?) {
    // Defer one runloop so the chooser dialog has begun dismissing; target the captured main window.
    dispatch_async(dispatch_get_main_queue()) {
        val root = (host ?: currentAppWindow())?.rootViewController ?: return@dispatch_async
        var top = root
        while (true) {
            val presented = top.presentedViewController
            if (presented != null && presented.isBeingDismissed() == false) top = presented else break
        }
        if (top.isBeingDismissed() || top.presentedViewController != null) {
            // Presenter momentarily busy — retry shortly.
            val delayNs = (0.15 * NSEC_PER_SEC.toDouble()).toLong()
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delayNs), dispatch_get_main_queue()) {
                present(vc, host)
            }
            return@dispatch_async
        }
        top.presentViewController(vc, animated = true, completion = null)
    }
}

// ---- Decoding & byte conversions ----

actual fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

/** Normalize picked library data (HEIC/PNG/JPEG) into decodable JPEG bytes. */
private fun normalizeToJpeg(data: NSData): ByteArray = runCatching {
    val image = UIImage(data = data)
    val jpeg = UIImageJPEGRepresentation(image, 0.88) ?: return@runCatching data.toByteArray()
    jpeg.toByteArray()
}.getOrElse { data.toByteArray() }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return result
}
