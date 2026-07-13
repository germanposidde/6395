package com.kmp.hook.elite.localnav

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessaging
import com.kmp.hook.elite.localdata.SingleMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder

fun whenInit(intent: Intent) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val trId = intent.getStringExtra("trackingId")
            if (trId != null) {
                val fcmToken = withContext(Dispatchers.IO) {
                    FirebaseMessaging.getInstance().token.await()
                }
                val url = "https://${SingleMap.map["pos"]}"
                val client = OkHttpClient()

                val fullUrl = "$url?" + "${SingleMap.map["track"]}=$trId" + "&${SingleMap.map["newBack"]}=${URLEncoder.encode(fcmToken, "UTF-8")}"
                val request = Request.Builder().url(fullUrl).build()

                client.newCall(request).enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {}

                        override fun onResponse(call: Call, response: Response) {
                            response.close()
                        }
                    }
                )
            }
        } catch (_: Exception) {
        }
    }
}
