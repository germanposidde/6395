package com.kmp.hook.platform

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual class FileExporter actual constructor() {

    actual suspend fun shareText(fileName: String, mimeType: String, content: String) {
        val controller = UIActivityViewController(
            activityItems = listOf(content),
            applicationActivities = null,
        )
        val app = UIApplication.sharedApplication
        val root = app.keyWindow?.rootViewController
            ?: app.windows.firstOrNull()?.let { (it as? platform.UIKit.UIWindow)?.rootViewController }
        root?.presentViewController(controller, animated = true, completion = null)
    }

    // Document picking on iOS requires a delegate + presentation plumbing; degrade gracefully.
    actual suspend fun pickTextFile(): String? = null
}
