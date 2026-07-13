package com.kmp.hook.elite

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

fun getTime(context: Context): String {
    val packageInfo: PackageInfo =
        context.packageManager.getPackageInfo(context.packageName, 0)
    val time = packageInfo.firstInstallTime.toString()
    return time
}

fun getDev(): String {
    val device =
        Build.BRAND.replaceFirstChar { it.titlecase(Locale.getDefault()) } + " " + Build.MODEL
    val encoded2 = URLEncoder.encode(device, StandardCharsets.UTF_8.toString())
    return encoded2
}

suspend fun runAfter(context: Context): String = withContext(Dispatchers.IO) {
    try {
        val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
        if (!info.isLimitAdTrackingEnabled) {
            info.id ?: "00000000-0000-0000-0000-000000000000"
        } else {
            "00000000-0000-0000-0000-000000000000"
        }
    } catch (_: Exception) {
        "00000000-0000-0000-0000-000000000000"
    }
}
