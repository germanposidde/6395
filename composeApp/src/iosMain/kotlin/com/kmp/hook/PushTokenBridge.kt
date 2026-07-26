package com.kmp.hook

import com.kmp.hook.PushTokenBridge.newDeferred
import com.kmp.hook.PushTokenBridge.onFailed
import com.kmp.hook.PushTokenBridge.onToken
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridge between the Swift UIApplicationDelegate APNS callbacks and Kotlin coroutines.
 * Swift calls [onToken] / [onFailed] from the AppDelegate; Kotlin awaits via [newDeferred].
 *
 * Lives on the main thread on both sides, so no synchronisation needed.
 */
object PushTokenBridge {
    private var pending: CompletableDeferred<String?>? = null

    fun newDeferred(): CompletableDeferred<String?> {
        val d = CompletableDeferred<String?>()
        pending = d
        return d
    }

    fun onToken(hexToken: String) {
        pending?.complete(hexToken)
        pending = null
    }

    fun onFailed() {
        pending?.complete(null)
        pending = null
    }
}
