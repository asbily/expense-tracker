package com.asbily.expensetracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.webkit.WebViewAssetLoader
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var splashOverlay: FrameLayout
    private val mainHandler = Handler(Looper.getMainLooper())
    private var splashDismissed = false
    private val splashTimeoutRunnable = Runnable { dismissSplashOverlay(immediate = false) }
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
        private const val NOTIF_CHANNEL_ID = "asbily_alerts_v2"
        private const val NOTIF_CHANNEL_NAME = "Asbily Reminders & Alerts"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applySystemBarColors()
        createNotificationChannel()
        registerActivityLaunchers()

        val rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F8F9FB"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#F8F9FB"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        splashOverlay = createSplashOverlay()

        rootContainer.addView(webView)
        rootContainer.addView(splashOverlay)
        setContentView(rootContainer)

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

            override fun onPageCommitVisible(view: WebView, url: String?) {
                super.onPageCommitVisible(view, url)
                mainHandler.postDelayed({ dismissSplashOverlay(immediate = false) }, 120L)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                mainHandler.postDelayed({ dismissSplashOverlay(immediate = false) }, 120L)
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
            dismissSplashOverlay(immediate = true)
        } else {
            mainHandler.postDelayed(splashTimeoutRunnable, 3200L)
            webView.loadUrl(ENTRY_URL)
        }

        maybeRequestNotificationPermission()
        initFirebaseMessaging()
    }

    override fun onResume() {
        super.onResume()
        applySystemBarColors()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun createSplashOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F8F9FB"))
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val logoView = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = "Asbily Logo"
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))
        }

        val titleView = TextView(this).apply {
            text = "Asbily"
            setTextColor(Color.parseColor("#111315"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(Typeface.DEFAULT_BOLD)
            letterSpacing = -0.02f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            }
        }

        val subtitleView = TextView(this).apply {
            text = "Expense Tracker"
            setTextColor(Color.parseColor("#5F6368"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
            }
        }

        val spinner = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(Color.parseColor("#111315"))
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                topMargin = dp(22)
            }
        }

        container.addView(logoView)
        container.addView(titleView)
        container.addView(subtitleView)
        container.addView(spinner)
        overlay.addView(container)
        return overlay
    }

    private fun dismissSplashOverlay(immediate: Boolean) {
        if (splashDismissed) return
        splashDismissed = true
        mainHandler.removeCallbacks(splashTimeoutRunnable)
        if (immediate) {
            splashOverlay.visibility = View.GONE
            return
        }
        splashOverlay.animate()
            .alpha(0f)
            .setDuration(220L)
            .withEndAction {
                splashOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun applySystemBarColors() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
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

    private fun initFirebaseMessaging() {
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    getSharedPreferences(AsbilyFirebaseMessagingService.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(AsbilyFirebaseMessagingService.KEY_FCM_TOKEN, token)
                        .apply()
                }
            }
            FirebaseMessaging.getInstance().subscribeToTopic("all")
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.deleteNotificationChannel(AsbilyFirebaseMessagingService.LEGACY_CHANNEL_ID)

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Asbily Expense Tracker announcements, updates & reminders"
                setSound(soundUri, audioAttrs)
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 180L, 90L, 180L)
            }
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

        @Suppress("UNUSED_PARAMETER")
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
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notification = NotificationCompat.Builder(this@MainActivity, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif_asbily)
                .setContentTitle(safeTitle)
                .setContentText(safeBody)
                .setStyle(NotificationCompat.BigTextStyle().bigText(safeBody))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(soundUri)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notifId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
            NotificationManagerCompat.from(this@MainActivity).notify(notifId, notification)
        }

        @JavascriptInterface
        fun getFcmToken(): String {
            return getSharedPreferences(AsbilyFirebaseMessagingService.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(AsbilyFirebaseMessagingService.KEY_FCM_TOKEN, "") ?: ""
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
