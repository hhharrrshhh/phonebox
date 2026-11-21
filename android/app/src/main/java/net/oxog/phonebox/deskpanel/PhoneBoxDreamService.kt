package net.oxog.phonebox.deskpanel

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/**
 * Native Android screensaver host for the PhoneBox panel.
 *
 * This first milestone intentionally hosts only the bundled UI. The follow-up milestone will
 * share the app's paired AgentLink and AgentStream with the dream without duplicating secrets
 * or weakening the existing certificate-pinning and request-validation rules.
 *
 * A dream is not an Activity and must not dismiss or bypass the device's keyguard.
 */
class PhoneBoxDreamService : DreamService() {

    private var panel: WebView? = null

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // Screensaver content is intentionally non-interactive. The system owns dismissal
        // and presents the normal keyguard after the dream ends.
        isInteractive = false
        isFullscreen = true
        isScreenBright = true

        panel = WebView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                textZoom = 100
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.e("PhoneBoxDream", "${message.sourceId()}:${message.lineNumber()} ${message.message()}")
                    }
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val response = assetLoader.shouldInterceptRequest(request.url)
                    if (response != null && request.url.path?.endsWith(".js") == true) {
                        response.mimeType = "text/javascript"
                    }
                    return response
                }
            }

            loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
        }

        setContentView(panel)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        panel?.onResume()
    }

    override fun onDreamingStopped() {
        panel?.apply {
            stopLoading()
            onPause()
            removeAllViews()
            destroy()
        }
        panel = null
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        if (panel != null) onDreamingStopped()
        super.onDetachedFromWindow()
    }
}
