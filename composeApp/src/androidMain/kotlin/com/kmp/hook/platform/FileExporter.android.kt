package com.kmp.hook.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Bridge populated by MainActivity (which owns the Activity context and the
 * ActivityResult launcher needed for sharing and document picking).
 */
object AndroidFileBridge {
    var share: ((fileName: String, mimeType: String, content: String) -> Unit)? = null
    var pick: ((onResult: (String?) -> Unit) -> Unit)? = null
}

actual class FileExporter actual constructor() {

    actual suspend fun shareText(fileName: String, mimeType: String, content: String) {
        AndroidFileBridge.share?.invoke(fileName, mimeType, content)
    }

    actual suspend fun pickTextFile(): String? = suspendCancellableCoroutine { cont ->
        val pick = AndroidFileBridge.pick
        if (pick == null) {
            if (cont.isActive) cont.resume(null)
        } else {
            pick { result -> if (cont.isActive) cont.resume(result) }
        }
    }
}
