package com.kmp.hook.elite

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.firebase.analytics.FirebaseAnalytics
import com.kmp.hook.BuildConfig
import com.kmp.hook.elite.MyAbTracker.log
import java.io.File
import java.util.UUID
import kotlin.random.Random

/**
 * A/B/C variants for the `onShowFileChooser` experiment:
 *  - [A]: choose an image from storage only (original behaviour).
 *  - [B]: if the Camera permission is granted, offer a Camera/Storage chooser;
 *         otherwise storage only.
 *  - [C]: like [B], but request the Camera permission first when it is missing,
 *         then continue based on the user's response.
 */
enum class FileChooserVariant { A, B, C }

/**
 * Self-contained, locally-persisted assignment for the file-chooser experiment.
 * A variant is picked at random on first read and kept for the install.
 * Backend wiring (reporting / override) can be layered on via [setVariant].
 */
object FileChooserABC {
    const val TEST_ID = "storage_camera"
    private const val PREFS = "file_chooser_ab"
    private const val KEY_VARIANT = "variant"

    /**
     * Put `storage_camera` in **extra 3**
     */
    fun appendUrl(context: Context, url: String, extra7: String): String = url.toUri()
        .buildUpon()
        .appendQueryParameter(extra7, getABVariant(context))
        .build()
        .toString()

    /**
     * Put value in **extra 7**
     *
     * Don't forget to put `storage_camera` in **extra 3**
     */
    fun getABVariant(context: Context): String {
        val variant = getABVariantInternal(context)
        MyAbTracker.init(context)
        MyAbTracker.logOnce(TEST_ID, variant, "init")
        return variant
    }

    private fun getABVariantInternal(context: Context) : String {
        if (BuildConfig.FORCE_AB_DISABLED) return FileChooserVariant.A.name
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_VARIANT, null)?.let { stored -> return stored }
        val assigned = FileChooserVariant.entries[Random.nextInt(FileChooserVariant.entries.size)]
        prefs.edit(commit = true) { putString(KEY_VARIANT, assigned.name) }
        return assigned.name
    }
}

/**
 * Drop-in [WebChromeClient] base class implementing the A/B/C `onShowFileChooser`
 * experiment (see [FileChooserVariant]). Subclass it from an app's own chrome
 * client and add app-specific behaviour (custom views, popups, WebRTC
 * permissions, …); the file-chooser handling is [final][onShowFileChooser] so it
 * behaves identically everywhere the experiment is copied.
 *
 * Wiring checklist for a new app:
 *  1. Copy this file.
 *  2. `AndroidManifest.xml`:
 *       - `<uses-permission android:name="android.permission.CAMERA" />`
 *       - a `<queries>` entry for `android.media.action.IMAGE_CAPTURE`
 *       - a `FileProvider` whose authority is `${applicationId}.fileprovider`
 *         (or override [fileProviderAuthority]).
 *  3. `res/xml/file_paths.xml`: `<cache-path name="camera_capture" path="camera_capture/" />`.
 *  4. Subclass it and set the subclass as the WebView's chrome client.
 *
 * All Activity Result launchers are registered against the activity's registry,
 * so nothing has to be created in — or threaded down from — the Compose layer.
 */
abstract class FileChooserExperimentWebChromeClient(
    protected val activity: ComponentActivity
) : WebChromeClient() {

    /** FileProvider authority used to hand camera captures back to the WebView. */
    protected open val fileProviderAuthority: String
        get() = "${activity.packageName}.fileprovider"

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraOutputUri: Uri? = null

    /** Experiment arm for this install — read once here (also fires the `init` log). */
    private val variant: String = FileChooserABC.getABVariant(activity)

    private val filePickerLauncher: ActivityResultLauncher<String> =
        activity.activityResultRegistry.register(
            UUID.randomUUID().toString(),
            ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            val picked = uris.toTypedArray()
            deliver(picked, source = if (picked.isEmpty()) "none" else "storage")
        }

    private val cameraPermissionLauncher: ActivityResultLauncher<String> =
        activity.activityResultRegistry.register(
            UUID.randomUUID().toString(),
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            log(
                FileChooserABC.TEST_ID, variant, "cam_perm_request",
                "result" to if (granted) "granted" else "denied"
            )
            launchImageSource(cameraAllowed = granted)
        }

    private val chooserLauncher: ActivityResultLauncher<Intent> =
        activity.activityResultRegistry.register(
            UUID.randomUUID().toString(),
            ActivityResultContracts.StartActivityForResult()
        ) { result -> onChooserResult(result) }

    final override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        fileCallback = filePathCallback
        val cameraGranted = hasCameraPermission()
        log(
            FileChooserABC.TEST_ID, variant, "on_show_file_chooser",
            "camera_perm" to if (cameraGranted) "granted" else "denied"
        )
        when (FileChooserVariant.valueOf(variant)) {
            // Variant A: image from storage only (original behaviour).
            FileChooserVariant.A -> filePickerLauncher.launch("image/*")

            // Variant B: offer Camera + Storage when the Camera permission is
            // already granted, otherwise fall back to Storage only.
            FileChooserVariant.B -> launchImageSource(cameraAllowed = cameraGranted)

            // Variant C: like B, but request the Camera permission first when it
            // is missing; the launcher callback resumes with the user's response.
            FileChooserVariant.C ->
                if (cameraGranted) launchImageSource(cameraAllowed = true)
                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        return true
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            activity, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun launchImageSource(cameraAllowed: Boolean) {
        if (cameraAllowed) launchCameraOrStorageChooser()
        else filePickerLauncher.launch("image/*")
    }

    private fun launchCameraOrStorageChooser() {
        val galleryIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val chooser = Intent.createChooser(galleryIntent, null)

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val outputUri = createCameraOutputUri()
        if (outputUri != null && cameraIntent.resolveActivity(activity.packageManager) != null) {
            pendingCameraOutputUri = outputUri
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
        } else {
            pendingCameraOutputUri = null
        }
        chooserLauncher.launch(chooser)
    }

    private fun createCameraOutputUri(): Uri? = try {
        val dir = File(activity.cacheDir, "camera_capture").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(activity, fileProviderAuthority, file)
    } catch (_: Exception) {
        null
    }

    private fun onChooserResult(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) {
            deliver(emptyArray(), source = "none")
            return
        }
        val data = result.data
        val fromGallery = data?.clipData != null || data?.data != null
        val uris = extractUris(data)
        val source = when {
            uris.isEmpty() -> "none"
            fromGallery -> "storage"
            else -> "camera" // no gallery payload but a Uri came back → camera capture
        }
        deliver(uris, source)
    }

    private fun extractUris(data: Intent?): Array<Uri> {
        data?.clipData?.let { clip ->
            return Array(clip.itemCount) { clip.getItemAt(it).uri }
        }
        data?.data?.let { return arrayOf(it) }
        // No gallery payload means a camera capture: the photo was written to the
        // output Uri we supplied via EXTRA_OUTPUT.
        return pendingCameraOutputUri?.let { arrayOf(it) } ?: emptyArray()
    }

    /** Hand the result back to the web page, log the outcome, and reset state. */
    private fun deliver(uris: Array<Uri>, source: String) {
        log(
            FileChooserABC.TEST_ID, variant, "outcome",
            "source" to source, "file_count" to uris.size
        )
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
        pendingCameraOutputUri = null
    }
}

private object MyAbTracker {

    // GA4 names: letters/digits/underscore, start with a letter, ≤40 chars.
    private val NAME_RE = Regex("^[a-z][a-z0-9_]{0,39}$")
    private const val PREFS = "ab_logger"

    private lateinit var prefs: SharedPreferences
    private lateinit var fa: FirebaseAnalytics

    private var initialized: Boolean = false

    /** Call once (Application.onCreate). Safe to call again. */
    fun init(context: Context) {
        if (!initialized) {
            val app = context.applicationContext
            prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fa = FirebaseAnalytics.getInstance(app)
            initialized = true
        }
    }

    /**
     * Log an A/B event. Event name = [test] (one event per test). The already-assigned
     * [variant] and the [action] (e.g. "exposed" / "choice" / "result") are attached as
     * params, along with your extra [params] (String / Int / Long / Double / Float /
     * Boolean).
     */
    fun log(test: String, variant: String, action: String, vararg params: Pair<String, Any>) {
        require(test.matches(NAME_RE)) { "invalid test name '$test' (a-z, 0-9, _, start with letter, ≤40)" }
        require(action.matches(NAME_RE)) { "invalid action '$action'" }
        val b = Bundle(2 + params.size).apply {
            putString("ab_variant", variant)
            putString("action", action)
            params.forEach { (k, v) -> putParam(k, v) }
        }
        fa.logEvent(test, b)
    }

    /** Like [log], but fires at most once per install for this (test, action). */
    fun logOnce(test: String, variant: String, action: String, vararg params: Pair<String, Any>) {
        val flag = "once_${test}_$action"
        if (prefs.getBoolean(flag, false)) return
        prefs.edit(commit = true) { putBoolean(flag, true) }
        log(test, variant, action, *params)
    }

    private fun Bundle.putParam(key: String, value: Any) {
        when (value) {
            is Long -> putLong(key, value)
            is Int -> putLong(key, value.toLong())
            is Double -> putDouble(key, value)
            is Float -> putDouble(key, value.toDouble())
            is Boolean -> putString(key, value.toString())
            is String -> putString(key, value)
            else -> putString(key, value.toString())
        }
    }
}
