package com.akash.agni.ui.workout

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Plays YouTube inside the app: a full-screen sheet with YouTube's mobile site, opened on a search
 * for the exercise. Tap any result to play it here; links that would open the YouTube app are blocked
 * so you never leave Agni. The workout timer keeps running underneath.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoDialog(url: String, onClose: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler {
            val wv = webView
            if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
        }
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close video", tint = Color.White) }
                    Text("Back to workout", color = Color.White)
                }
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { ctx ->
                        val container = FrameLayout(ctx)
                        val wv = WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            webViewClient = object : WebViewClient() {
                                // Keep normal web pages inside; block intent:// and vnd.youtube: app hand-offs.
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val scheme = request.url.scheme
                                    return scheme != "https" && scheme != "http"
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                private var fullscreen: View? = null
                                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                    fullscreen = view
                                    container.addView(
                                        view,
                                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                                    )
                                }
                                override fun onHideCustomView() {
                                    fullscreen?.let { container.removeView(it) }
                                    fullscreen = null
                                }
                            }
                            loadUrl(url)
                        }
                        container.addView(
                            wv,
                            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                        )
                        webView = wv
                        container
                    },
                    onRelease = {
                        webView?.destroy()
                        webView = null
                    },
                )
            }
        }
    }
}
