package com.kmp.hook

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad

private const val WEBKIT = "605.1.15"
private const val SAFARI_MOBILE_BUILD = "604.1"
private const val KERNEL = "15E148"

/**
 * Builds a Safari-like user agent string for use in WKWebView.
 *
 * @param desktopMode When true, mimics Safari's "Request Desktop Website" UA (macOS Safari).
 */
@OptIn(ExperimentalForeignApi::class)
internal fun buildSafariUserAgent(desktopMode: Boolean = false): String {
    val device = UIDevice.currentDevice
    // systemVersion is a plain String like "17.0.1" — no struct interop needed
    val major = device.systemVersion.split(".").firstOrNull()?.toIntOrNull() ?: 17

    if (desktopMode) return desktopUA(major)

    val model = device.model.split(" ").first()
    val platform = if (device.userInterfaceIdiom == UIUserInterfaceIdiomPad) "OS" else "iPhone OS"
    val cpuInfo = "$model; CPU $platform ${osVersion(major)} like Mac OS X"

    return "Mozilla/5.0 ($cpuInfo) AppleWebKit/$WEBKIT (KHTML, like Gecko) Version/${
        safariVersion(
            major
        )
    } Mobile/$KERNEL Safari/$SAFARI_MOBILE_BUILD"
}

// Desktop UAs match what iOS Safari sends in "Request Desktop Website" mode.
private fun desktopUA(major: Int): String {
    val (macOs, version) = when (major) {
        15 -> "10_15_6" to "15.5"
        16 -> "10_15_7" to "16.6"
        17 -> "10_15_7" to "17.0"
        18 -> "10_15_7" to "18.0"
        else -> "10_15_7" to "$major.0"
    }
    return "Mozilla/5.0 (Macintosh; Intel Mac OS X $macOs) AppleWebKit/$WEBKIT (KHTML, like Gecko) Version/$version Safari/$WEBKIT"
}

// Frozen OS version strings used in the mobile UA (iOS Safari behaviour).
private fun osVersion(major: Int) = when (major) {
    15 -> "15_5"
    16 -> "16_6"
    17 -> "17_0_1"
    18 -> "18_0"
    else -> "${major}_0"
}

// Version/XX.X part — tracks Safari's build number for each iOS release.
private fun safariVersion(major: Int) = when (major) {
    15 -> "15.5"
    16 -> "16.6"
    17 -> "17.0"
    18 -> "18.0"
    else -> "$major.0"
}
