package com.logie.persona.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.logie.persona.backend.catalog.Game
import org.json.JSONArray
import org.json.JSONObject

/** What the skin draws: the title block, the menu words (greyed ones can't be activated), which one is chosen, and a gauge while something installs. */
class SkinState(val title: String, val lines: List<String>?, val version: String, val rows: List<MenuRow>, val selected: Int, val gauge: Gauge?) {
    fun json(): String = JSONObject().apply {
        put("game", JSONObject().put("title", title).put("version", version).apply { lines?.let { put("lines", JSONArray(it)) } })
        put("selected", selected)
        put("entries", JSONArray(rows.map { JSONObject().put("label", it.label).put("enabled", it.usable) }))
        put("progress", gauge?.let { JSONObject().put("text", it.text).put("fraction", it.fraction.toDouble()) } ?: JSONObject.NULL)
    }.toString()
}

fun skinStateOf(game: Game, versionLabel: String, rows: List<MenuRow>, selected: Int, gauge: Gauge?) =
    SkinState(game.title, game.lines.takeIf { it.isNotEmpty() }, versionLabel, rows, selected, gauge)

/**
 * A game's screen drawn by its skin (`skins/<name>`, a web page in the app's assets: WebGL for the animated art, SVG for the lettering). The page tells the app
 * which word was chosen ([onSelect]) and which was tapped again ([onActivate]); the app tells it what to show with [SkinState]. The page is served from
 * the app's own assets over an https address that exists only inside the app; it can reach nothing else.
 */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
fun SkinScreen(skin: String, state: SkinState, onSelect: (Int) -> Unit, onActivate: (Int) -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    val selectNow = rememberUpdatedState(onSelect)
    val activateNow = rememberUpdatedState(onActivate)
    val json = state.json()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val loader = WebViewAssetLoader.Builder().setDomain(DOMAIN).addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
            WebView(context).apply {
                setBackgroundColor(Color.parseColor("#C2A9DA"))
                overScrollMode = View.OVER_SCROLL_NEVER
                isHapticFeedbackEnabled = false; isLongClickable = false; setOnLongClickListener { true }
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false; settings.allowContentAccess = false
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
                    // Nothing outside the app's own page is ever opened in it.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.host != DOMAIN
                }
                addJavascriptInterface(object {
                    @JavascriptInterface fun select(i: Int) { post { selectNow.value(i) } }
                    @JavascriptInterface fun activate(i: Int) { post { activateNow.value(i) } }
                }, "Android")
                loadUrl("https://$DOMAIN/assets/$skin/index.html")
            }
        },
        update = { view ->
            // set now if the page is up, and kept for the page to take when it finishes loading (it loads fonts and the girl's layers first)
            view.evaluateJavascript("window.__pending=$json;if(window.Skin)window.Skin.setState(window.__pending);", null)
        },
        onRelease = { it.destroy() },
    )
}

private const val DOMAIN = "appassets.androidplatform.net"
