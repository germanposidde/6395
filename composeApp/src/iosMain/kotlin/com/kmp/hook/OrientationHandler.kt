package com.kmp.hook

/**
 * Bridge for orientation control between Kotlin and Swift.
 *
 * Swift AppDelegate implements [OrientationHandler] and sets it via [setHandler].
 * Kotlin calls [setWebViewActive] when the WebView becomes active/inactive.
 */
interface OrientationHandler {
    fun apply(allOrientations: Boolean)
}

object OrientationBridge {
    var allOrientationsEnabled: Boolean = false
        private set

    private var handler: OrientationHandler? = null

    fun setHandler(handler: OrientationHandler) {
        this.handler = handler
    }

    fun setWebViewActive(active: Boolean) {
        allOrientationsEnabled = active
        handler?.apply(active)
    }
}