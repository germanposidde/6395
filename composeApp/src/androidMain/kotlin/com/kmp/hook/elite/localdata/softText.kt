package com.kmp.hook.elite.localdata

import android.content.Context
import android.provider.Settings
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume


fun checkToSend(context: Context): String {
    return Settings.Global.getString(context.contentResolver, Settings.Global.ADB_ENABLED) ?: "0"
//     return "0"
}

suspend fun softText(context: Context): String =
    suspendCancellableCoroutine { continuation ->
        val client = InstallReferrerClient.newBuilder(context).build()
        val isResumed = AtomicBoolean(false)

        continuation.invokeOnCancellation {
            client.endConnection()
        }

        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                try {
                    if (!isResumed.compareAndSet(false, true)) return

                    val result =
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            client.installReferrer.installReferrer
                        } else {
                            "null"
                        }

                continuation.resume(result)
//                    continuation.resume("cmpgn=aaaa_TEST-Deeplink_bbbb_cccc_dddd")
//                continuation.resume("cmpgn=aaaa_MA-TEST_bbbb_cccc_dddd")

                } catch (_: Exception) {
                    if (isResumed.compareAndSet(false, true)) {
                        continuation.resume("null")
                    }
                } finally {
                    client.endConnection()
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                if (isResumed.compareAndSet(false, true)) {
                    continuation.resume("null")
                }
            }
        })
    }

