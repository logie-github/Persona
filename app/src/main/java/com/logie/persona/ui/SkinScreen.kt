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
    val latest = rememberUpdatedState(json)
    val view = remember { arrayOfNulls<WebView>(1) }
    // The page keeps what it is sent in window.__pending and draws it once it has loaded; the state is sent whenever it changes, when the page finishes loading and
    // when the page says it is ready (a push made before the page exists goes to the blank page that was there first, and is lost).
    fun push() { view[0]?.evaluateJavascript("window.__pending=${latest.value};if(window.Skin)window.Skin.setState(window.__pending);", null) }
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
                    override fun onPageFinished(view: WebView, url: String?) { push() }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface fun select(i: Int) { post { selectNow.value(i) } }
                    @JavascriptInterface fun activate(i: Int) { post { activateNow.value(i) } }
                    @JavascriptInterface fun ready(i: Int) { post { push() } }
                }, "Android")
                view[0] = this
                loadUrl("https://$DOMAIN/assets/$skin/index.html")
            }
        },
        update = { push() },
        onRelease = { view[0] = null; it.destroy() },
    )
}

private const val DOMAIN = "appassets.androidplatform.net"
