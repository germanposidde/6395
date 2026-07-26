package com.kmp.hook

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSUserDefaults
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithName
import platform.SystemConfiguration.SCNetworkReachabilityFlagsVar
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable
import platform.UIKit.UIApplication
import platform.UIKit.registerForRemoteNotifications
import platform.UIKit.unregisterForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.WebKit.WKFrameInfo
import platform.WebKit.WKMediaCaptureType
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationResponse
import platform.WebKit.WKNavigationResponsePolicy
import platform.WebKit.WKPermissionDecision
import platform.WebKit.WKPreferences
import platform.WebKit.WKSecurityOrigin
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.random.Random

private const val APP_DOMAIN = "hookarafury.monster"
private const val APP_ROUTE = "topFishes6395"

// ── Cookie persistence bridge ────────────────────────────────────────────────
// Uses nonPersistentDataStore to avoid ITP restrictions, with manual save/load
// to NSUserDefaults and a WKUserScript to cover the first-document-load race.

private object CookiePersistenceManager {
    private const val KEY = "my_wkwebview_cookies"

    fun save(cookies: List<NSHTTPCookie>) {
        val serializable = cookies.mapNotNull { it.properties }
        NSUserDefaults.standardUserDefaults.setObject(serializable, KEY)

    }

    fun save(webView: WKWebView) {
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { rawCookies ->
            val cookies = rawCookies?.filterIsInstance<NSHTTPCookie>() ?: emptyList()
            save(cookies)
        }
    }

    fun load(): List<NSHTTPCookie> {
        val array = NSUserDefaults.standardUserDefaults.arrayForKey(KEY) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return array.mapNotNull { item ->
            NSHTTPCookie.cookieWithProperties(item as? Map<Any?, *> ?: return@mapNotNull null)
        }
    }

    fun buildInjectionScript(cookies: List<NSHTTPCookie>): String =
        cookies.joinToString("\n") { cookie ->
            val name = cookie.name.replace("\\", "\\\\").replace("\"", "\\\"")
            val value = cookie.value.replace("\\", "\\\\").replace("\"", "\\\"")
            "document.cookie = \"${name}=${value}; domain=${cookie.domain}; path=${cookie.path}\";"
        }
}

// ── Launch outcome persistence ───────────────────────────────────────────────

private object LaunchOutcomeManager {
    private const val KEY = "my_launch_outcome"
    private const val WHITE = "white"

    fun isAppDomain(url: String) =
        url.startsWith("https://$APP_DOMAIN") || url.startsWith("http://$APP_DOMAIN")

    fun savedWhite(): Boolean =
        NSUserDefaults.standardUserDefaults.stringForKey(KEY) == WHITE

    fun savedUrl(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(KEY)?.takeIf { it != WHITE }

    fun saveWhite() = NSUserDefaults.standardUserDefaults.setObject(WHITE, KEY)
    fun saveUrl(url: String) = NSUserDefaults.standardUserDefaults.setObject(url, KEY)
}

// ── Push notifications ───────────────────────────────────────────────────────

/**
 * Requests notification permission then triggers APNS registration.
 * Awaits the token delivered by [PushTokenBridge] from the Swift AppDelegate.
 * Returns the hex APNS token, or null if permission was denied or timed out.
 *
 * Must be called on the Main thread (UNUserNotificationCenter requirement).
 */
private suspend fun acquirePushToken(): String? {
    // Check current authorization status before requesting
    val currentStatus = suspendCancellableCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                cont.resume(settings?.authorizationStatus)
            }
    }


    val granted = suspendCancellableCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(
                options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
                completionHandler = { granted, error ->

                    cont.resume(granted)
                }
            )
    }
    if (!granted) {

        return null
    }


    repeat(3) { attempt ->
        val deferred = PushTokenBridge.newDeferred()
        UIApplication.sharedApplication.registerForRemoteNotifications()

        val token = withTimeoutOrNull(20_000) { deferred.await() }
        if (token != null) {

            return token
        }

    }

    return null
}

/** Deregisters from APNS. Safe to call from any thread. */
private fun deletePushToken() {
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.unregisterForRemoteNotifications()
    }
}

/** Synchronous connectivity check — returns true if at least one host is reachable. */
@OptIn(ExperimentalForeignApi::class)
private fun isNetworkAvailable(): Boolean =
    listOf("apple.com", "google.com", "cloudflare.com").any { host ->
        memScoped {
            val ref = SCNetworkReachabilityCreateWithName(null, host) ?: return@any false
            val flags = alloc<SCNetworkReachabilityFlagsVar>()
            if (!SCNetworkReachabilityGetFlags(ref, flags.ptr)) return@any false
            (flags.value and kSCNetworkReachabilityFlagsReachable) != 0u
        }
    }

// ── WebView configuration ────────────────────────────────────────────────────

// Strips audio from getUserMedia so websites never trigger a mic permission prompt.
// Must survive removeAllUserScripts() — re-added after cookie scripts are cleared.
private val stripAudioScript = WKUserScript(
    source = """
        (function() {
            var orig = MediaDevices.prototype.getUserMedia;
            MediaDevices.prototype.getUserMedia = function(c) {
                if (c) delete c.audio;
                return orig.call(this, c);
            };
        })();
    """.trimIndent(),
    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
    forMainFrameOnly = false
)

private fun buildWebViewConfiguration(): WKWebViewConfiguration {
    val savedCookies = CookiePersistenceManager.load()
    val store = WKWebsiteDataStore.nonPersistentDataStore()

    return WKWebViewConfiguration().apply {
        preferences = WKPreferences().apply {
            javaScriptCanOpenWindowsAutomatically = true
        }
        websiteDataStore = store
        // Keep camera/video streams inline so the page JS controls them.
        // Without this WKWebView hijacks the stream into a native AVPlayer fullscreen.
        allowsInlineMediaPlayback = true
        // Don't gate media playback behind a user gesture — lets the camera feed start immediately.
        mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone

        userContentController.addUserScript(stripAudioScript)


        // Restore into httpCookieStore for XHR/fetch requests
        savedCookies.forEach { cookie -> store.httpCookieStore.setCookie(cookie) {} }
        // Inject via WKUserScript so the first document sees them before any JS runs
        if (savedCookies.isNotEmpty()) {
            userContentController.addUserScript(
                WKUserScript(
                    source = CookiePersistenceManager.buildInjectionScript(savedCookies),
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = false
                )
            )
        }
    }
}

// ── Composable ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun Gray(
    loading: @Composable (() -> Unit),
    noInternet: @Composable ((onRetry: () -> Unit) -> Unit),
    white: @Composable (() -> Unit),
    transitionSpec: AnimatedContentTransitionScope<Int>.() -> ContentTransform
) = Box(Modifier.fillMaxSize().background(Color.Black)) {
    var state by remember { mutableStateOf(0) }
    AnimatedContent(
        targetState = state,
        transitionSpec = transitionSpec
    ) {
        when (it) {
            0 -> loading()
            1 -> noInternet({ state = 0 })
            2 -> white()
        }
    }

    LaunchedEffect(state) {
        OrientationBridge.setWebViewActive(state == 3)
    }

    val coroutineScope = rememberCoroutineScope()

    fun navigateToNoInternet() = coroutineScope.launch {

        delay(Random.nextLong(2000, 5000))
        state = 1
    }

    var url: String? by remember { mutableStateOf(null) }
    var webView: WKWebView? by remember { mutableStateOf(null) }
    var webViewReady by remember { mutableStateOf(false) }
    var isFirstDiscovery by remember { mutableStateOf(false) }
    var openUrlError: String? by remember { mutableStateOf(null) }
    var webViewCloseRequest: WKWebView? by remember { mutableStateOf(null) }
    val popupWebViews = remember { mutableStateListOf<WKWebView>() }

    LifecycleResumeEffect(webViewReady) {

        onPauseOrDispose {

            if (webViewReady) {

                webView?.let { CookiePersistenceManager.save(it) }
                popupWebViews.forEach { CookiePersistenceManager.save(it) }
            }
        }
    }

    val navDelegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationAction: WKNavigationAction,
                decisionHandler: (WKNavigationActionPolicy) -> Unit
            ) {
                val requestUrl = decidePolicyForNavigationAction.request.URL
                val scheme = requestUrl?.scheme?.lowercase()

                // about: in main frame → cancel (nothing to render).
                // about: in subframe → allow (Turnstile and other challenge widgets use about:blank iframes).
                if (scheme == "about") {
                    val isMainFrame =
                        decidePolicyForNavigationAction.targetFrame?.mainFrame != false
                    decisionHandler(
                        if (isMainFrame) WKNavigationActionPolicy.WKNavigationActionPolicyCancel
                        else WKNavigationActionPolicy.WKNavigationActionPolicyAllow
                    )
                    return
                }

                if (scheme != null && scheme !in listOf("http", "https", "blob", "data")) {

                    UIApplication.sharedApplication.openURL(
                        requestUrl,
                        emptyMap<Any?, Any?>()
                    ) { success ->
                        if (!success) {

                            openUrlError = "Application not found!"
                        }
                    }
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                } else {
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                }
            }

            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationResponse: WKNavigationResponse,
                decisionHandler: (WKNavigationResponsePolicy) -> Unit
            ) {
                val responseUrl =
                    (decidePolicyForNavigationResponse.response as? NSHTTPURLResponse)?.URL

                decisionHandler(WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow)
            }

            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                val finalUrl = webView.URL?.absoluteString ?: ""


                // After the first successful load, drop the cookie-injection user scripts
                // so they don't re-inject stale cookies on subsequent navigations
                webView.configuration.userContentController.removeAllUserScripts()
                webView.configuration.userContentController.addUserScript(stripAudioScript)

                if (isFirstDiscovery) {
                    isFirstDiscovery = false
                    if (LaunchOutcomeManager.isAppDomain(finalUrl)) {

                        deletePushToken()
                        LaunchOutcomeManager.saveWhite()
                        state = 2
                    } else {
                        state = 3

                        LaunchOutcomeManager.saveUrl(finalUrl)
                        webViewReady = true
                    }
                } else {
                    state = 3
                    webViewReady = true
                }

            }

        }
    }

    val uiDelegate = remember {
        object : NSObject(), WKUIDelegateProtocol {
            override fun webView(
                webView: WKWebView,
                requestMediaCapturePermissionForOrigin: WKSecurityOrigin,
                initiatedByFrame: WKFrameInfo,
                type: WKMediaCaptureType,
                decisionHandler: (WKPermissionDecision) -> Unit
            ) {
                val cameraOk =
                    AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized
                // Audio is stripped from getUserMedia by our injected script, so only
                // camera-only requests should arrive here. Deny anything involving mic
                // to avoid a crash (no NSMicrophoneUsageDescription in Info.plist).
                val decision = when (type) {
                    WKMediaCaptureType.WKMediaCaptureTypeCamera ->
                        if (cameraOk) WKPermissionDecision.WKPermissionDecisionGrant
                        else WKPermissionDecision.WKPermissionDecisionPrompt

                    else -> WKPermissionDecision.WKPermissionDecisionDeny
                }

                decisionHandler(decision)
            }

            override fun webView(
                webView: WKWebView,
                createWebViewWithConfiguration: WKWebViewConfiguration,
                forNavigationAction: WKNavigationAction,
                windowFeatures: WKWindowFeatures
            ): WKWebView {

                val uiDelegate = this
                return WKWebView(
                    frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                    configuration = createWebViewWithConfiguration
                ).apply {
                    customUserAgent = buildSafariUserAgent()
                    allowsBackForwardNavigationGestures = true
                    navigationDelegate = navDelegate
                    UIDelegate = uiDelegate
                }.also { popupWebViews.add(it) }
            }

            override fun webViewDidClose(webView: WKWebView) {

                webViewCloseRequest = webView
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
    ) {
        // Main WebView
        if (url != null) {
            Box(Modifier.fillMaxSize(if (webViewReady) 1f else 0f)) {
                UIKitView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {

                        WKWebView(
                            frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                            configuration = buildWebViewConfiguration()
                        ).apply {
                            customUserAgent = buildSafariUserAgent()
                            allowsBackForwardNavigationGestures = true
                            navigationDelegate = navDelegate
                            UIDelegate = uiDelegate
                        }.also { webView = it }
                    },
                    onRelease = { webView = null },
                    update = { webView ->

                        NSURL.URLWithString(url!!)?.let { nsUrl ->
                            webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
                        }
                    }
                )
            }
        }

        // Popup WebView stack (window.open targets) — iOS sheet-style animation + back button
        key(popupWebViews.lastOrNull()) {
            if (popupWebViews.isNotEmpty()) {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }

                // Triggered by JS window.close() — plays the normal exit animation
                LaunchedEffect(webViewCloseRequest) {
                    if (webViewCloseRequest != null && webViewCloseRequest == popupWebViews.lastOrNull()) {
                        webViewCloseRequest = null
                        visible = false
                    }
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(380, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(250)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(200))
                ) {
                    Box(Modifier.fillMaxSize().zIndex(2f)) {
                        UIKitView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { popupWebViews.last() }
                        )

                        // iOS-style "< Back" button
                        Text(
                            text = "\u276E  Back",
                            color = Color(0xFF007AFF),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .zIndex(3f)
                                .padding(start = 8.dp, top = 8.dp)
                                .background(
                                    Color.White.copy(alpha = 0.85f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { visible = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Remove from stack after the vertical exit animation (window.close() / back button)
                LaunchedEffect(visible) {
                    if (!visible) {
                        delay(320)
                        if (popupWebViews.isNotEmpty()) popupWebViews.removeAt(popupWebViews.size - 1)
                    }
                }
            }
        }

        // Toast: external app open failure
        // Keep the last message alive so the exit animation has text to render.
        var toastText by remember { mutableStateOf("") }
        if (openUrlError != null) toastText = openUrlError!!
        AnimatedVisibility(
            visible = openUrlError != null,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(4f)
        ) {
            Text(
                text = toastText,
                color = Color.White,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp, start = 24.dp, end = 24.dp)
                    .background(Color(0xCC1A1A1A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        LaunchedEffect(openUrlError) {
            if (openUrlError != null) {
                delay(3_000)
                openUrlError = null
            }
        }
    }

    // Resolve what to show on launch
    LaunchedEffect(state == 0) {


        // Short-circuit if outcome already decided on a previous launch
        if (LaunchOutcomeManager.savedWhite()) {

            state = 2
            return@LaunchedEffect
        }

        // Check connectivity before anything that requires network.
        if (!isNetworkAvailable()) {

            navigateToNoInternet()
            return@LaunchedEffect
        }

        val savedUrl = LaunchOutcomeManager.savedUrl()
        if (savedUrl != null) {

            if (url == savedUrl) {
                // Retry after "No Internet"
                webView?.let { wv ->
                    NSURL.URLWithString(savedUrl)?.let { nsUrl ->
                        wv.loadRequest(NSURLRequest.requestWithURL(nsUrl))
                    }
                }
            } else {
                url = savedUrl
            }
            return@LaunchedEffect
        }

        // First discovery: request push permission and get APNS token, then launch link.
        isFirstDiscovery = true

        val fcmToken = acquirePushToken()

        url = buildString {
            append("https://$APP_DOMAIN/$APP_ROUTE/")
            if (fcmToken != null) append("?fcm_token=$fcmToken")
        }
    }
}
