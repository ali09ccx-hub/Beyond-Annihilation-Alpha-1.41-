package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
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
private const val GAME_ASSET_FILE = "after-extinction-alpha-1.54_d8db5a3f.html"
private const val THREE_ASSET_FILE = "three.min_9274bbce.js"

private fun buildInjectedHtmlHeader(reduceLag: Boolean): ByteArray =
  """
  <!DOCTYPE html>
  <html lang="ar" dir="rtl">
  <head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover">
  <style>
  @font-face{font-family:'Tajawal';font-style:normal;font-weight:400 800;font-display:swap;src:url('./fonts/tajawal.ttf') format('truetype');}
  @font-face{font-family:'Reem Kufi';font-style:normal;font-weight:400 700;font-display:swap;src:url('./fonts/reem_kufi.ttf') format('truetype');}
  </style>
  <script>
  (function(){
    try{
      var LS=window.localStorage,want=${if (reduceLag) "true" else "false"},prev=LS.getItem('afterx_launcher_lag_v1');
      if(want){
        if(prev!=='1'||!LS.getItem('afterx_gfx')){
          LS.setItem('afterx_gfx',JSON.stringify({q:1,sh:false,fps:0,auto:1,man:0,vd:0.7,cd:0.6,gr:0.25,res:0.7,ef:0.3,cap:45,lod:1,nl:1,vg:120,mm:1.2}));
          LS.setItem('afterx_launcher_lag_v1','1');
        }
      }else if(prev==='1'){
        LS.removeItem('afterx_gfx');
        LS.setItem('afterx_launcher_lag_v1','0');
      }
    }catch(e){}
  })();
  </script>
  <script src="./net-bridge.js"></script>
  """
    .trimIndent()
    .toByteArray(Charsets.UTF_8)

private val INJECTED_HTML_FOOTER = "\n</body></html>".toByteArray(Charsets.UTF_8)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GameWebView(
  modifier: Modifier = Modifier,
  reduceLag: Boolean = true,
  onPageReady: () -> Unit,
  onWebViewCreated: (WebView) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val webView =
    remember(context, reduceLag) {
      createConfiguredGameWebView(
        context = context,
        reduceLag = reduceLag,
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
  reduceLag: Boolean,
  onPageReady: () -> Unit,
): WebView {
  val assetManager = context.assets
  val resources = context.resources
  val headerBytes = buildInjectedHtmlHeader(reduceLag)

  return WebView(context).apply {
    layoutParams =
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    setBackgroundColor(AndroidColor.parseColor("#0A0F0D"))
    // Native Chromium WebGL compositing without an extra Android offscreen texture copy.
    setLayerType(View.LAYER_TYPE_NONE, null)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
    }
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
      cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
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
          val host = url.host ?: return null
          val rawPath = url.path ?: "/"
          val cleanPath = rawPath.trimStart('/')

          // Intercept external Google Fonts so offline launches never stall or wait on DNS/TLS.
          if (host == "fonts.googleapis.com" || host == "fonts.gstatic.com") {
            return WebResourceResponse(
              "text/css",
              "UTF-8",
              200,
              "OK",
              mapOf("Cache-Control" to "max-age=86400"),
              ByteArrayInputStream(ByteArray(0)),
            )
          }

          // Fallback safety net: if three.min.js is requested from CDN, serve the local bundle.
          if (host == "cdnjs.cloudflare.com" && cleanPath.endsWith("three.min.js")) {
            return try {
              WebResourceResponse(
                "application/javascript",
                "UTF-8",
                200,
                "OK",
                mapOf("Cache-Control" to "max-age=86400"),
                assetManager.open(THREE_ASSET_FILE),
              )
            } catch (_: IOException) {
              null
            }
          }

          if (url.scheme != "https" || host != "localhost") {
            return null
          }

          if (cleanPath.isEmpty() || cleanPath == "index.html") {
            return try {
              val gameStream = assetManager.open(GAME_ASSET_FILE)
              val combined =
                SequenceInputStream(
                  Collections.enumeration(
                    listOf(
                      ByteArrayInputStream(headerBytes),
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
            } catch (_: IOException) {
              notFoundResponse("text/html")
            }
          }

          if (cleanPath == "vendor/three.min.js") {
            return try {
              WebResourceResponse(
                "application/javascript",
                "UTF-8",
                200,
                "OK",
                mapOf("Cache-Control" to "max-age=86400"),
                assetManager.open(THREE_ASSET_FILE),
              )
            } catch (_: IOException) {
              notFoundResponse("application/javascript")
            }
          }

          if (cleanPath == "fonts/tajawal.ttf") {
            return WebResourceResponse(
              "font/ttf",
              null,
              200,
              "OK",
              mapOf("Cache-Control" to "max-age=86400"),
              resources.openRawResource(R.font.tajawal),
            )
          }

          if (cleanPath == "fonts/reem_kufi.ttf") {
            return WebResourceResponse(
              "font/ttf",
              null,
              200,
              "OK",
              mapOf("Cache-Control" to "max-age=86400"),
              resources.openRawResource(R.font.reem_kufi),
            )
          }

          if (cleanPath == "favicon.ico") {
            return notFoundResponse("image/x-icon")
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
    path.endsWith(".ttf", ignoreCase = true) -> "font/ttf"
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
