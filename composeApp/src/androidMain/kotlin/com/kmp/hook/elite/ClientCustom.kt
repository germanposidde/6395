package com.kmp.hook.elite

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClientCustom(
    private val activity: ComponentActivity,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        if (uri.scheme == "about") return false
        val intent = when (uri.scheme) {
            "intent" -> runCatching {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            }.onFailure {
            }.getOrNull()

            "mailto" -> Intent(Intent.ACTION_SENDTO, uri)
            "tel" -> Intent(Intent.ACTION_DIAL, uri)
            "http", "https", "blob", "data" -> null
            else -> Intent(Intent.ACTION_VIEW, uri)
        }
        intent?.let {
            try {
                activity.startActivity(it)
                return true
            } catch (_: Throwable) {
            }
            val packageName = intent.`package`
                ?: intent.component?.packageName
            if (packageName == null) {
                Toast.makeText(
                    activity,
                    "No application found!",
                    Toast.LENGTH_LONG
                ).show()
                return true
            }
            try {
                val googlePlayIntent = Intent(
                    Intent.ACTION_VIEW,
                    "market://details?id=$packageName".toUri()
                )
                googlePlayIntent.setPackage("com.android.vending")
                activity.startActivity(googlePlayIntent)
                return true
            } catch (_: Throwable) {
            }
            try {
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    "market://details?id=$packageName".toUri()
                )
                activity.startActivity(marketIntent)
                return true
            } catch (_: Throwable) {
            }
            try {
                val playStoreIntent = Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$packageName".toUri()
                )
                activity.startActivity(playStoreIntent)
            } catch (_: Throwable) {
                Toast.makeText(activity, "No application found!", Toast.LENGTH_LONG).show()
            }
            return true
        }
        return false
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        CoroutineScope(Dispatchers.IO).launch {
            CookieManager.getInstance().flush()
        }

        CoroutineScope(Dispatchers.Main).launch {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            (view as? ViewCustom)?.showOneWebView()
            PushDialogAb.onPageFinished()
        }
    }
}

@SuppressLint("ViewConstructor")
class ViewCustom(
    private val activity: ComponentActivity,
    private val viewClient: ClientCustom,
) : WebView(activity) {
    private val contentRoot: FrameLayout = FrameLayout(activity)
    val popupContainer: FrameLayout = FrameLayout(activity).apply {
        isVisible = false
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }
    val fullscreenContainer: FrameLayout = FrameLayout(activity).apply {
        isVisible = false
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }
    val content: ViewGroup = activity.findViewById(android.R.id.content)
    private var first = true
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (popupContainer.isNotEmpty()) {
                val top = popupContainer.getChildAt(popupContainer.childCount - 1) as WebView
                if (top.canGoBack()) {
                    top.goBack()
                } else {
                    top.stopLoading()
                    (top.parent as? ViewGroup)?.removeView(top)
                    top.destroy()
                    popupContainer.isVisible = popupContainer.isNotEmpty()
                }
                return
            }
            if (canGoBack()) {
                goBack()
            }
        }
    }

    private val chromeClient =
        ChromeCustom(activity, this, viewClient)

    init {
        Log.d("KKKKK", "CustomWebView init")

        content.addView(contentRoot)
        contentRoot.addView(
            this,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        contentRoot.addView(popupContainer)
        contentRoot.addView(fullscreenContainer)
        contentRoot.isVisible = false
        activity.onBackPressedDispatcher.addCallback(activity, backPressedCallback)

        settingsIfW(this, viewClient, chromeClient)
    }

    fun showOneWebView() {
        if (first) {
            first = false
            for (i in content.childCount - 1 downTo 0) {
                val child = content.getChildAt(i)
                if (child != contentRoot) child.isVisible = false
            }

            if (contentRoot.parent == null) {
                activity.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                content.addView(contentRoot)
            }
        }

        if (!contentRoot.isVisible)
            contentRoot.isVisible = true

        Log.d("KKKKK", "showWebView: ${contentRoot.isVisible}")
    }

    fun getW(): WebView {
        return this
    }
}
