package com.kmp.hook.elite.util

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.messaging.FirebaseMessaging
import com.kmp.hook.elite.localdata.pnaftk
import com.kmp.hook.elite.localdata.pnagk
import com.kmp.hook.elite.localdata.pnau
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
import java.util.Locale

fun tokenToSave() {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val fcmToken = withContext(Dispatchers.IO) {
                FirebaseMessaging.getInstance().token.await()
            }
            val locale = Locale.getDefault().toLanguageTag()
            val url = "https://${pnau}"
            val client = OkHttpClient()

            val fullUrl = "$url?" +
                    "${pnagk}=${Firebase.analytics.appInstanceId.await()}" +
                    "&${pnaftk}=${
                        URLEncoder.encode(fcmToken, "UTF-8")
                    }"

            val request = Request.Builder().url(fullUrl)
                .addHeader("Accept-Language", locale)
                .get().build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        } catch (exc: Exception) {
        }
    }
}
