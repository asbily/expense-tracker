package com.asbily.expensetracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private var pendingSaveBytes: ByteArray? = null
    private var pendingSaveFilename: String = "asbily-backup.json"
    private var pendingSaveMimeType: String = "application/json"

    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var createDocumentLauncher: ActivityResultLauncher<Intent>
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>

    companion object {
        private const val ASSET_ORIGIN = "appassets.androidplatform.net"
        private const val ENTRY_URL = "https://appassets.androidplatform.net/assets/www/index.html"
        private const val NOTIF_CHANNEL_ID = "asbily_alerts"
        private const val NOTIF_CHANNEL_NAME = "Asbily Reminders & Alerts"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()
        registerActivityLaunchers()

        webView = WebView(this)
        setContentView(webView)

        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(ASSET_ORIGIN)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.addJavascriptInterface(AndroidBridge(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url ?: return false
                val host = uri.host ?: ""
                if (host == ASSET_ORIGIN) {
                    return false
                }
                openExternalUri(uri)
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                    fileChooserLauncher.launch(intent)
                    true
                } catch (_: Exception) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(function(){try{if(typeof handleBackStep==='function'){handleBackStep();}else{window.Android&&window.Android.exitApp();}}catch(e){window.Android&&window.Android.exitApp();}})();",
                    null
                )
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(ENTRY_URL)
        }

        maybeRequestNotificationPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    private fun registerActivityLaunchers() {
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileChooserCallback?.onReceiveValue(uris)
            fileChooserCallback = null
        }

        createDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val bytes = pendingSaveBytes
            pendingSaveBytes = null
            val uri = result.data?.data
            if (result.resultCode != RESULT_OK || uri == null || bytes == null) {
                notifyDocumentSaved(false, "Save cancelled.")
                return@registerForActivityResult
            }
            try {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(bytes)
                    stream.flush()
                } ?: throw IllegalStateException("Unable to open output stream")
                notifyDocumentSaved(true, "")
            } catch (e: Exception) {
                notifyDocumentSaved(false, e.message ?: "Unable to save file.")
            }
        }

        openDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) {
                notifyDocumentOpened(null, false)
                return@registerForActivityResult
            }
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    notifyDocumentOpened(null, false)
                    return@registerForActivityResult
                }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                notifyDocumentOpened(b64, true)
            } catch (_: Exception) {
                notifyDocumentOpened(null, false)
            }
        }

        requestNotificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Budget, recurring schedule, and loan reminders"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun openExternalUri(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun notifyDocumentSaved(success: Boolean, message: String) {
        val msgJson = JSONObject.quote(message)
        runOnUiThread {
            webView.evaluateJavascript(
                "if(typeof window.onAndroidDocumentSaved==='function'){window.onAndroidDocumentSaved(${if (success) "true" else "false"},$msgJson);}",
                null
            )
        }
    }

    private fun notifyDocumentOpened(content: String?, isBase64: Boolean) {
        val contentArg = if (content == null) "null" else JSONObject.quote(content)
        runOnUiThread {
            webView.evaluateJavascript(
                "if(typeof window.onAndroidDocumentOpened==='function'){window.onAndroidDocumentOpened($contentArg,${if (isBase64) "true" else "false"});}",
                null
            )
        }
    }

    private fun launchCreateDocument(filename: String, mimeType: String, bytes: ByteArray) {
        pendingSaveFilename = filename.ifBlank { "asbily-export" }
        pendingSaveMimeType = mimeType.ifBlank { "application/octet-stream" }
        pendingSaveBytes = bytes

        runOnUiThread {
            try {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = pendingSaveMimeType
                    putExtra(Intent.EXTRA_TITLE, pendingSaveFilename)
                }
                createDocumentLauncher.launch(intent)
            } catch (e: Exception) {
                pendingSaveBytes = null
                notifyDocumentSaved(false, e.message ?: "Unable to open file picker.")
            }
        }
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun showNotification(title: String?, body: String?, view: String?, action: String?) {
            val safeTitle = title?.takeIf { it.isNotBlank() } ?: "Asbily Expense Tracker"
            val safeBody = body ?: ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val tapIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getActivity(this@MainActivity, 0, tapIntent, flags)

            val notification = NotificationCompat.Builder(this@MainActivity, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(safeTitle)
                .setContentText(safeBody)
                .setStyle(NotificationCompat.BigTextStyle().bigText(safeBody))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notifId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
            NotificationManagerCompat.from(this@MainActivity).notify(notifId, notification)
        }

        @JavascriptInterface
        fun exitApp() {
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun shareApp(title: String?, text: String?, url: String?) {
            val shareTitle = title?.takeIf { it.isNotBlank() } ?: "Asbily Expense Tracker"
            val parts = listOfNotNull(
                text?.takeIf { it.isNotBlank() },
                url?.takeIf { it.isNotBlank() }
            ).joinToString("\n")
            val body = parts.ifBlank { shareTitle }
            runOnUiThread {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                startActivity(Intent.createChooser(intent, shareTitle))
            }
        }

        @JavascriptInterface
        fun shareText(fullText: String?) {
            val body = fullText?.takeIf { it.isNotBlank() } ?: return
            runOnUiThread {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                startActivity(Intent.createChooser(intent, "Share Asbily Expense Tracker"))
            }
        }

        @JavascriptInterface
        fun createDocument(filename: String?, mimeType: String?, content: String?) {
            val bytes = (content ?: "").toByteArray(StandardCharsets.UTF_8)
            launchCreateDocument(
                filename ?: "asbily-export.txt",
                mimeType ?: "text/plain",
                bytes
            )
        }

        @JavascriptInterface
        fun saveFile(filename: String?, mimeType: String?, content: String?) {
            createDocument(filename, mimeType, content)
        }

        @JavascriptInterface
        fun createDocumentBase64(filename: String?, mimeType: String?, base64Content: String?) {
            val cleaned = (base64Content ?: "").trim()
            val bytes = try {
                Base64.decode(cleaned, Base64.DEFAULT)
            } catch (_: Exception) {
                ByteArray(0)
            }
            launchCreateDocument(
                filename ?: "asbily-export.bin",
                mimeType ?: "application/octet-stream",
                bytes
            )
        }

        @JavascriptInterface
        fun openDocument(mimeType: String?) {
            val type = mimeType?.takeIf { it.isNotBlank() } ?: "*/*"
            runOnUiThread {
                try {
                    openDocumentLauncher.launch(arrayOf(type, "*/*"))
                } catch (_: Exception) {
                    notifyDocumentOpened(null, false)
                }
            }
        }

        @JavascriptInterface
        fun openBackup() {
            openDocument("application/json")
        }
    }
}
