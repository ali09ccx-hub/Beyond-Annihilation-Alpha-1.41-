package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.SequenceInputStream
import java.util.Collections

private const val LOCAL_GAME_URL = "https://localhost/index.html"
private const val GAME_ASSET_FILE = "after-extinction-alpha-1.42_97483156.html"

private val INJECTED_HTML_HEADER =
  """
  <!DOCTYPE html>
  <html lang="ar" dir="rtl">
  <head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover">
  <script src="https://unpkg.com/peerjs@1.5.4/dist/peerjs.min.js"></script>
  <script>if(!window.Peer)document.write('<script src="https://cdn.jsdelivr.net/npm/peerjs@1.5.4/dist/peerjs.min.js"><\/script>')</script>
  <script src="./net-bridge.js"></script>
  """
    .trimIndent()
    .toByteArray(Charsets.UTF_8)

private val INJECTED_HTML_FOOTER = "\n</body></html>".toByteArray(Charsets.UTF_8)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GameWebView(
  modifier: Modifier = Modifier,
  onPageReady: () -> Unit,
  onWebViewCreated: (WebView) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val webView =
    remember(context) {
      createConfiguredGameWebView(
        context = context,
        onPageReady = onPageReady,
      ).also(onWebViewCreated)
    }

  DisposableEffect(lifecycleOwner, webView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          webView.onResume()
          webView.resumeTimers()
        }
        Lifecycle.Event.ON_PAUSE -> {
          webView.onPause()
          webView.pauseTimers()
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      try {
        webView.evaluateJavascript(
          "if(typeof window.sendNetworkData==='function')window.sendNetworkData('{\"c\":\"leave\"}');",
          null,
        )
      } catch (_: Throwable) {}
      (webView.parent as? ViewGroup)?.removeView(webView)
      webView.stopLoading()
      webView.destroy()
    }
  }

  AndroidView(
    factory = { webView },
    modifier = modifier.testTag("game_webview"),
  )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredGameWebView(
  context: Context,
  onPageReady: () -> Unit,
): WebView {
  val assetManager = context.assets

  return WebView(context).apply {
    layoutParams =
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    setBackgroundColor(AndroidColor.parseColor("#0A0F0D"))
    setLayerType(View.LAYER_TYPE_HARDWARE, null)
    isHorizontalScrollBarEnabled = false
    isVerticalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER

    settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      databaseEnabled = true
      allowFileAccess = true
      mediaPlaybackRequiresUserGesture = false
      useWideViewPort = true
      loadWithOverviewMode = true
      setSupportZoom(false)
      builtInZoomControls = false
      displayZoomControls = false
      textZoom = 100
      cacheMode = WebSettings.LOAD_DEFAULT
      mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }

    webChromeClient =
      object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
          if (newProgress >= 95) {
            onPageReady()
          }
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
          // Game does not require camera or microphone permissions; deny cleanly.
          request?.deny()
        }
      }

    webViewClient =
      object : WebViewClient() {
        override fun shouldInterceptRequest(
          view: WebView?,
          request: WebResourceRequest?,
        ): WebResourceResponse? {
          val url = request?.url ?: return null
          if (url.scheme != "https" || url.host != "localhost") {
            return null
          }

          val rawPath = url.path ?: "/"
          val cleanPath = rawPath.trimStart('/')
          if (cleanPath.isEmpty() || cleanPath == "index.html") {
            return try {
              val gameStream = assetManager.open(GAME_ASSET_FILE)
              val combined =
                SequenceInputStream(
                  Collections.enumeration(
                    listOf(
                      ByteArrayInputStream(INJECTED_HTML_HEADER),
                      gameStream,
                      ByteArrayInputStream(INJECTED_HTML_FOOTER),
                    )
                  )
                )
              WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                mapOf("Cache-Control" to "no-store"),
                combined,
              )
            } catch (e: IOException) {
              notFoundResponse("text/html")
            }
          }

          val assetSubPath = "www/$cleanPath"
          return try {
            val stream = assetManager.open(assetSubPath)
            WebResourceResponse(
              mimeTypeFor(cleanPath),
              "UTF-8",
              200,
              "OK",
              mapOf("Cache-Control" to "no-store"),
              stream,
            )
          } catch (_: IOException) {
            notFoundResponse(mimeTypeFor(cleanPath))
          }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
          super.onPageFinished(view, url)
          onPageReady()
        }
      }

    loadUrl(LOCAL_GAME_URL)
  }
}

private fun mimeTypeFor(path: String): String =
  when {
    path.endsWith(".js", ignoreCase = true) -> "application/javascript"
    path.endsWith(".html", ignoreCase = true) -> "text/html"
    path.endsWith(".css", ignoreCase = true) -> "text/css"
    path.endsWith(".json", ignoreCase = true) -> "application/json"
    path.endsWith(".png", ignoreCase = true) -> "image/png"
    path.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
    else -> "application/octet-stream"
  }

private fun notFoundResponse(mimeType: String): WebResourceResponse =
  WebResourceResponse(
    mimeType,
    "UTF-8",
    404,
    "Not Found",
    emptyMap(),
    ByteArrayInputStream(ByteArray(0)),
  )
