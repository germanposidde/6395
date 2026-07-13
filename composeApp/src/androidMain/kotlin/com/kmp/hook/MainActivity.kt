package com.kmp.hook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kmp.hook.elite.StartCache
import com.kmp.hook.elite.isPushClicked
import com.kmp.hook.elite.localnav.LocalNavObj
import com.kmp.hook.elite.localnav.ScreenManager
import com.kmp.hook.platform.AndroidFileBridge
import java.io.File

class MainActivity : ComponentActivity() {

    private var pickCallback: ((String?) -> Unit)? = null
    private lateinit var openDocLauncher: ActivityResultLauncher<Array<String>>

    fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        openDocLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            val text = uri?.let { runCatching { readTextFromUri(it) }.getOrNull() }
            pickCallback?.invoke(text)
            pickCallback = null
        }

        AndroidFileBridge.share = { fileName, mimeType, content ->
            runCatching { shareDocument(fileName, mimeType, content) }
        }
        AndroidFileBridge.pick = { onResult ->
            pickCallback = onResult
            runCatching {
                openDocLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            }.onFailure { onResult(null); pickCallback = null }
        }
        isPushClicked(intent, this)
        val startCache = StartCache(this, intent)
        setContent {
            val screen by LocalNavObj.screen.collectAsState()
            when (screen) {
                ScreenManager.Welcome -> LoadingScreenA(this@MainActivity, startCache)
                ScreenManager.MenuPoint -> App()
                ScreenManager.InternetProblem -> NoInternetScreenA()
                else -> {}
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        isPushClicked(intent, this)
    }

    override fun onDestroy() {
        AndroidFileBridge.share = null
        AndroidFileBridge.pick = null
        super.onDestroy()
    }

    private fun readTextFromUri(uri: Uri): String =
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""

    private fun shareDocument(fileName: String, mimeType: String, content: String) {
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, content)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share $fileName"))
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
