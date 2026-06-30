package com.thuongtin.moviecast

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteButton
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import org.json.JSONArray
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

private const val DEFAULT_START_URL = "about:blank"
private const val POLL_INTERVAL_MS = 1000L
private const val LOG_TAG = "MovieCast"
private const val SUBTITLE_AUTO = "__auto__"
private const val SUBTITLE_OFF = "__off__"

data class CastUiState(
    val available: Boolean = false,
    val connected: Boolean = false,
    val deviceName: String = "Chưa chọn TV",
    val playerState: String = "IDLE",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val activeTitle: String = "",
    val message: String = "Chọn TV bằng nút Cast."
)

private val CastUiState.hasActiveMedia: Boolean
    get() = connected && (
        activeTitle.isNotBlank() ||
            durationMs > 0L ||
            playerState == "PLAYING" ||
            playerState == "PAUSED" ||
            playerState == "BUFFERING"
        )

data class AppUiState(
    val address: String = DEFAULT_START_URL,
    val pageTitle: String = "",
    val loading: Boolean = false,
    val scanning: Boolean = false,
    val browserChromeHidden: Boolean = false,
    val appMuted: Boolean = false,
    val status: String = "Mở một trang phim, app sẽ tự nhận link video.",
    val candidates: Map<String, MediaCandidate> = emptyMap(),
    val selectedCandidateUrl: String? = null,
    val subtitles: Map<String, SubtitleCandidate> = emptyMap(),
    val selectedSubtitleUrl: String = SUBTITLE_AUTO,
    val history: List<HistoryEntry> = emptyList(),
    val cast: CastUiState = CastUiState()
)

data class MediaPreviewInfo(
    val url: String,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val live: Boolean = false
)

class MainActivity : FragmentActivity() {
    private lateinit var historyStore: HistoryStore
    private var webView: WebView? = null
    private var castContext: CastContext? = null
    private var sessionListener: SessionManagerListener<CastSession>? = null
    @Volatile private var currentPageUrl: String = DEFAULT_START_URL
    @Volatile private var currentPageTitle: String = ""
    @Volatile private var appMutedForWebView: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private val probedHlsManifests = mutableSetOf<String>()
    private var uiState by mutableStateOf(AppUiState())

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshCastState()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyStore = HistoryStore(this)
        val startUrl = historyStore.lastPageUrl().ifBlank { DEFAULT_START_URL }
        appMutedForWebView = historyStore.appMuted()
        uiState = uiState.copy(
            address = startUrl,
            history = historyStore.history(),
            appMuted = appMutedForWebView
        )
        initializeCast()

        setContent {
            MovieCastTheme {
                MovieCastScreen(
                    uiState = uiState,
                    onWebViewReady = { configureWebView(it) },
                    onNavigate = { loadPage(it) },
                    onBack = { webView?.takeIf { view -> view.canGoBack() }?.goBack() },
                    onForward = { webView?.takeIf { view -> view.canGoForward() }?.goForward() },
                    onReload = { webView?.reload() },
                    onAppMuteToggle = { toggleAppMute() },
                    onRescan = { rescanMedia(reloadPage = true) },
                    onSelectCandidate = { selectCandidate(it) },
                    onSubtitleSelected = { selectSubtitle(it) },
                    onClearCandidates = { clearCandidates() },
                    onManualCandidate = { addManualCandidate(it) },
                    onCastSelected = { castSelectedCandidate() },
                    onQueueSelected = { queueSelectedCandidate() },
                    onPlayPause = { togglePlayback() },
                    onStop = { stopPlayback() },
                    onDisconnect = { disconnectCast() },
                    onSeek = { seekTo(it) },
                    onVolume = { setCastVolume(it) },
                    onMuteToggle = { toggleMute() },
                    onHistoryOpen = { loadPage(it) },
                    onHistoryClear = { clearHistory() },
                    setupCastButton = { setupCastRouteButton(it) }
                )
            }
        }

        handler.post(pollRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        sessionListener?.let { listener ->
            castContext?.sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
        }
        SubtitleProxyServer.stop()
        webView?.destroy()
        super.onDestroy()
    }

    private fun initializeCast() {
        try {
            val context = CastContext.getSharedInstance(this)
            castContext = context
            val listener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarting(session: CastSession) {
                    updateCastMessage("Đang kết nối TV.")
                }

                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    refreshCastState("Đã kết nối TV.")
                }

                override fun onSessionStartFailed(session: CastSession, error: Int) {
                    refreshCastState("Không kết nối được TV.")
                }

                override fun onSessionEnding(session: CastSession) {
                    refreshCastState("Đang ngắt kết nối TV.")
                }

                override fun onSessionEnded(session: CastSession, error: Int) {
                    refreshCastState("Đã ngắt kết nối TV.")
                }

                override fun onSessionResuming(session: CastSession, sessionId: String) {
                    refreshCastState("Đang nối lại phiên Cast.")
                }

                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    refreshCastState("Đã nối lại phiên Cast.")
                }

                override fun onSessionResumeFailed(session: CastSession, error: Int) {
                    refreshCastState("Không nối lại được phiên Cast.")
                }

                override fun onSessionSuspended(session: CastSession, reason: Int) {
                    refreshCastState("Phiên Cast tạm ngắt.")
                }
            }
            sessionListener = listener
            context.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
            refreshCastState("Cast SDK sẵn sàng.")
        } catch (error: Throwable) {
            uiState = uiState.copy(
                cast = uiState.cast.copy(
                    available = false,
                    connected = false,
                    message = "Cast SDK chưa sẵn sàng trên thiết bị này."
                ),
                status = error.message ?: "Không khởi tạo được Cast SDK."
            )
        }
    }

    private fun setupCastRouteButton(button: MediaRouteButton) {
        button.layoutParams = ViewGroup.LayoutParams(44.dpValue(this), 44.dpValue(this))
        button.setBackgroundColor(AndroidColor.TRANSPARENT)
        try {
            CastButtonFactory.setUpMediaRouteButton(applicationContext, button)
            button.isEnabled = uiState.cast.available
        } catch (_: Throwable) {
            button.isEnabled = false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(view: WebView) {
        if (webView === view) return
        webView = view
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        view.setBackgroundColor(AndroidColor.TRANSPARENT)
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.mediaPlaybackRequiresUserGesture = false
        view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        view.settings.loadsImagesAutomatically = true
        val chromeScrollThreshold = 12.dpValue(this)
        view.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val delta = scrollY - oldScrollY
            when {
                scrollY <= chromeScrollThreshold -> setBrowserChromeHidden(false)
                delta > chromeScrollThreshold -> setBrowserChromeHidden(true)
                delta < -chromeScrollThreshold -> setBrowserChromeHidden(false)
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                val pageTitle = title.orEmpty()
                currentPageTitle = pageTitle
                uiState = uiState.copy(pageTitle = pageTitle)
                view?.url?.let { rememberPage(it, pageTitle) }
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                currentPageUrl = url.orEmpty()
                currentPageTitle = ""
                probedHlsManifests.clear()
                uiState = uiState.copy(
                    loading = true,
                    scanning = true,
                    browserChromeHidden = false,
                    address = url.orEmpty(),
                    candidates = emptyMap(),
                    selectedCandidateUrl = null,
                    subtitles = emptyMap(),
                    selectedSubtitleUrl = SUBTITLE_AUTO,
                    status = "Đang tải trang và chờ stream."
                )
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val currentUrl = url.orEmpty()
                currentPageUrl = currentUrl
                currentPageTitle = view?.title.orEmpty()
                uiState = uiState.copy(loading = false, address = currentUrl)
                rememberPage(currentUrl, currentPageTitle)
                applyAppMuteToWebView()
                scanPageForMedia()
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val requestUrl = request?.url?.toString().orEmpty()
                val candidate = MediaDetector.detect(
                    rawUrl = requestUrl,
                    source = "Network",
                    pageUrl = currentPageUrl,
                    title = currentPageTitle,
                    score = 38
                )
                if (candidate != null) {
                    runOnUiThread { addCandidate(candidate) }
                }
                val subtitle = MediaDetector.detectSubtitle(
                    rawUrl = requestUrl,
                    source = "Network",
                    pageUrl = currentPageUrl
                )
                if (subtitle != null) {
                    runOnUiThread { addSubtitle(subtitle) }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        view.loadUrl(uiState.address)
        applyAppMuteToWebView()
    }

    private fun loadPage(rawUrl: String) {
        val url = MediaDetector.normalizeNavigationUrl(rawUrl)
        if (url.isBlank()) return
        uiState = uiState.copy(address = url, browserChromeHidden = false, status = "Đang mở trang.")
        webView?.loadUrl(url)
        handler.postDelayed({ applyAppMuteToWebView() }, 250)
    }

    private fun setBrowserChromeHidden(hidden: Boolean) {
        if (uiState.browserChromeHidden != hidden) {
            uiState = uiState.copy(browserChromeHidden = hidden)
        }
    }

    private fun toggleAppMute() {
        val muted = !uiState.appMuted
        appMutedForWebView = muted
        historyStore.setAppMuted(muted)
        uiState = uiState.copy(
            appMuted = muted,
            status = if (muted) "Đã tắt âm thanh app." else "Đã bật âm thanh app."
        )
        applyAppMuteToWebView(muted)
    }

    private fun applyAppMuteToWebView(muted: Boolean = appMutedForWebView) {
        val view = webView ?: return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
            WebViewCompat.setAudioMuted(view, muted)
            return
        }
        view.evaluateJavascript(appMuteJavascript(muted), null)
    }

    private fun appMuteJavascript(muted: Boolean): String {
        val mutedValue = if (muted) "true" else "false"
        return """
            (() => {
              if (!window.__movieCastMuteBridgeInstalled) {
                window.__movieCastMuteBridgeInstalled = true;
                window.__movieCastApplyAppMute = () => {
                  document.querySelectorAll("video,audio").forEach((media) => {
                    if (window.__movieCastAppMuted) {
                      if (typeof media.__movieCastPreviousVolume !== "number") {
                        media.__movieCastPreviousVolume = media.volume;
                      }
                      media.muted = true;
                      media.volume = 0;
                    } else {
                      media.muted = false;
                      if (typeof media.__movieCastPreviousVolume === "number") {
                        media.volume = media.__movieCastPreviousVolume;
                        delete media.__movieCastPreviousVolume;
                      }
                    }
                  });
                };
                window.__movieCastPropagateAppMute = () => {
                  document.querySelectorAll("iframe").forEach((frame) => {
                    try {
                      frame.contentWindow.postMessage({
                        source: "movie-cast",
                        type: "app-muted",
                        muted: window.__movieCastAppMuted
                      }, "*");
                    } catch (error) {}
                  });
                };
                window.__movieCastSetAppMuted = (muted) => {
                  window.__movieCastAppMuted = Boolean(muted);
                  window.__movieCastApplyAppMute();
                  window.__movieCastPropagateAppMute();
                };
                window.addEventListener("message", (event) => {
                  const data = event.data || {};
                  if (data.source === "movie-cast" && data.type === "app-muted") {
                    window.__movieCastSetAppMuted(data.muted);
                  }
                });
              }
              window.__movieCastEnsureMuteObserver = () => {
                if (window.__movieCastMuteObserver) return;
                const root = document.documentElement || document.body;
                if (!root) return;
                window.__movieCastMuteObserver = new MutationObserver(() => {
                  window.__movieCastApplyAppMute();
                  window.__movieCastPropagateAppMute();
                });
                window.__movieCastMuteObserver.observe(root, {
                  childList: true,
                  subtree: true
                });
              };
              window.__movieCastSetAppMuted($mutedValue);
              window.__movieCastEnsureMuteObserver();
              if (document.readyState === "loading") {
                document.addEventListener("DOMContentLoaded", () => {
                  window.__movieCastEnsureMuteObserver();
                  window.__movieCastSetAppMuted(window.__movieCastAppMuted);
                }, { once: true });
              }
            })();
        """.trimIndent()
    }

    private fun rememberPage(url: String, title: String) {
        if (!url.startsWith("http", true)) return
        val nextHistory = historyStore.rememberPage(url, title)
        uiState = uiState.copy(history = nextHistory, address = url, pageTitle = title)
    }

    private fun clearHistory() {
        uiState = uiState.copy(history = historyStore.clear(), status = "Đã xóa lịch sử.")
    }

    private fun addManualCandidate(rawUrl: String) {
        val url = MediaDetector.normalizeNavigationUrl(rawUrl)
        val candidate = MediaDetector.detect(
            rawUrl = url,
            source = "Manual",
            pageUrl = uiState.address,
            title = "Link nhập thủ công",
            score = 100
        )
        if (candidate == null) {
            uiState = uiState.copy(status = "Link này chưa giống media URL trực tiếp.")
            return
        }
        addCandidate(candidate)
        uiState = uiState.copy(selectedCandidateUrl = candidate.url, status = "Đã thêm link thủ công.")
    }

    private fun addCandidate(candidate: MediaCandidate) {
        val next = uiState.candidates.toMutableMap()
        next[candidate.url] = MediaDetector.mergeCandidates(next[candidate.url], candidate)
        val sorted = next.values
            .sortedWith(compareByDescending<MediaCandidate> { MediaDetector.rank(it) }.thenByDescending { it.seenAt })
            .take(40)
            .associateBy { it.url }
        val visible = sorted.values.filter(MediaDetector::isVisibleCandidate)
        val selected = when {
            uiState.selectedCandidateUrl != null && visible.any { it.url == uiState.selectedCandidateUrl } -> uiState.selectedCandidateUrl
            visible.isNotEmpty() -> visible.first().url
            else -> null
        }
        uiState = uiState.copy(
            candidates = sorted,
            selectedCandidateUrl = selected,
            scanning = false,
            status = if (visible.isNotEmpty()) "Đã tìm thấy link phim." else uiState.status
        )
        probeHlsSubtitles(candidate)
    }

    private fun probeHlsSubtitles(candidate: MediaCandidate) {
        val isHls = candidate.contentType.contains("mpegurl", ignoreCase = true) || candidate.url.contains(".m3u8", ignoreCase = true)
        if (!isHls || !probedHlsManifests.add(candidate.url)) return
        Thread {
            val subtitles = runCatching {
                val connection = URL(candidate.url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 7000
                    connection.readTimeout = 9000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "MovieCast/1.0")
                    if (connection.responseCode !in 200..299) return@runCatching emptyList<SubtitleCandidate>()
                    val text = connection.inputStream.bufferedReader().use { it.readText().take(300000) }
                    MediaDetector.hlsSubtitleCandidates(text, candidate.url)
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(emptyList())
            if (subtitles.isNotEmpty()) {
                runOnUiThread { subtitles.forEach(::addSubtitle) }
            }
        }.start()
    }

    private fun addSubtitle(subtitle: SubtitleCandidate) {
        val next = uiState.subtitles.toMutableMap()
        val existing = next[subtitle.url]
        next[subtitle.url] = if (existing == null || subtitle.score >= existing.score) subtitle else existing
        val sorted = next.values
            .filter { it.castSupported }
            .sortedWith(compareByDescending<SubtitleCandidate> { it.isDefault }.thenByDescending { it.score }.thenByDescending { it.seenAt })
            .take(24)
            .associateBy { it.url }
        val selected = when {
            uiState.selectedSubtitleUrl == SUBTITLE_OFF -> SUBTITLE_OFF
            uiState.selectedSubtitleUrl == SUBTITLE_AUTO -> SUBTITLE_AUTO
            sorted.containsKey(uiState.selectedSubtitleUrl) -> uiState.selectedSubtitleUrl
            else -> SUBTITLE_AUTO
        }
        uiState = uiState.copy(
            subtitles = sorted,
            selectedSubtitleUrl = selected,
            status = if (sorted.isNotEmpty() && uiState.candidates.isEmpty()) "Đã tìm thấy phụ đề, đang chờ link phim." else uiState.status
        )
    }

    private fun selectCandidate(url: String) {
        uiState = uiState.copy(selectedCandidateUrl = url)
    }

    private fun selectSubtitle(url: String) {
        uiState = uiState.copy(selectedSubtitleUrl = url)
    }

    private fun clearCandidates() {
        probedHlsManifests.clear()
        uiState = uiState.copy(
            candidates = emptyMap(),
            selectedCandidateUrl = null,
            subtitles = emptyMap(),
            selectedSubtitleUrl = SUBTITLE_AUTO,
            status = "Đã xóa danh sách link."
        )
    }

    private fun rescanMedia(reloadPage: Boolean) {
        probedHlsManifests.clear()
        uiState = uiState.copy(
            candidates = emptyMap(),
            selectedCandidateUrl = null,
            subtitles = emptyMap(),
            selectedSubtitleUrl = SUBTITLE_AUTO,
            scanning = true,
            status = "Đang quét lại link phim."
        )
        scanPageForMedia()
        if (reloadPage) {
            webView?.reload()
        }
        handler.postDelayed({
            if (uiState.scanning) {
                uiState = uiState.copy(scanning = false, status = "Chưa tìm thấy link mới.")
            }
        }, 4500)
    }

    private fun scanPageForMedia() {
        val view = webView ?: return
        val pageUrl = view.url.orEmpty()
        val pageTitle = view.title.orEmpty()
        view.evaluateJavascript(domScanJavascript()) { raw ->
            handleDomScanResult(raw, pageUrl, pageTitle)
        }
    }

    private fun handleDomScanResult(raw: String?, pageUrl: String, pageTitle: String) {
        if (raw.isNullOrBlank() || raw == "null") return
        val decoded = runCatching {
            val value = JSONTokener(raw).nextValue()
            if (value is String) value else raw
        }.getOrDefault(raw)
        val candidates = runCatching {
            val array = JSONArray(decoded)
            val pageData = StringBuilder()
            val result = mutableListOf<MediaCandidate>()
            val subtitles = mutableListOf<SubtitleCandidate>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (item.has("pageData")) {
                    pageData.append(item.optString("pageData")).append('\n')
                } else {
                    val url = item.optString("url")
                    val source = item.optString("source", "DOM")
                    val title = item.optString("title", pageTitle)
                    val poster = item.optString("poster")
                    val itemSubtitles = subtitlesFromJsonItem(item, pageUrl)
                    subtitles += itemSubtitles
                    MediaDetector.detect(
                        rawUrl = url,
                        source = source,
                        pageUrl = pageUrl,
                        title = title,
                        poster = poster,
                        subtitles = itemSubtitles
                    )?.let(result::add)
                }
            }
            val scriptText = pageData.toString()
            result += MediaDetector.structuredEpisodeCandidates(scriptText, pageUrl, pageTitle)
            result += MediaDetector.looseMediaCandidates(scriptText, pageUrl, pageTitle)
            subtitles += MediaDetector.looseSubtitleCandidates(scriptText, pageUrl)
            subtitles += MediaDetector.hlsSubtitleCandidates(scriptText, pageUrl)
            subtitles.forEach(::addSubtitle)
            result
        }.getOrDefault(emptyList())
        candidates.forEach(::addCandidate)
        if (candidates.isEmpty() && uiState.scanning) {
            uiState = uiState.copy(status = "Đang theo dõi request video.")
        }
    }

    private fun subtitlesFromJsonItem(item: org.json.JSONObject, pageUrl: String): List<SubtitleCandidate> {
        val array = item.optJSONArray("subtitles") ?: return emptyList()
        val result = mutableListOf<SubtitleCandidate>()
        for (index in 0 until array.length()) {
            val track = array.optJSONObject(index) ?: continue
            MediaDetector.detectSubtitle(
                rawUrl = track.optString("url"),
                source = item.optString("source", "DOM"),
                pageUrl = pageUrl,
                label = track.optString("label"),
                language = track.optString("language"),
                isDefault = track.optBoolean("isDefault", false)
            )?.let(result::add)
        }
        return result
    }

    private fun castSelectedCandidate() {
        val candidate = uiState.selectedCandidate ?: run {
            Log.d(LOG_TAG, "castSelectedCandidate ignored: no selected candidate")
            uiState = uiState.copy(status = "Chưa có link phim để cast.")
            return
        }
        Log.d(LOG_TAG, "castSelectedCandidate tapped url=${candidate.url} type=${candidate.contentType}")
        val session = currentCastSession()
        if (session == null) {
            Log.d(LOG_TAG, "castSelectedCandidate blocked: no connected Cast session")
            uiState = uiState.copy(status = "Hãy chọn TV bằng nút Cast trước.")
            return
        }
        Log.d(LOG_TAG, "castSelectedCandidate sending to device=${session.castDevice?.friendlyName}")
        prepareSubtitleForCast { preparedSubtitle ->
            loadCandidateOnCast(candidate, preparedSubtitle)
        }
    }

    private fun loadCandidateOnCast(candidate: MediaCandidate, preparedSubtitle: PreparedSubtitle?) {
        val session = currentCastSession() ?: run {
            uiState = uiState.copy(status = "Phiên Cast đã ngắt trước khi gửi media.")
            return
        }
        val mediaInfo = candidate.toMediaInfo(preparedSubtitle)
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .apply {
                if (preparedSubtitle != null) {
                    setActiveTrackIds(longArrayOf(preparedSubtitle.trackId))
                }
            }
            .build()
        uiState = uiState.copy(status = if (preparedSubtitle != null) "Đang gửi video và phụ đề lên TV." else "Đang gửi video lên TV.")
        session.remoteMediaClient?.load(request)?.setResultCallback { result ->
            runOnUiThread {
                Log.d(LOG_TAG, "castSelectedCandidate load result success=${result.status.isSuccess} status=${result.status.statusCode} message=${result.status.statusMessage}")
                uiState = uiState.copy(
                    status = if (result.status.isSuccess) {
                        if (preparedSubtitle != null) "Đã cast video kèm phụ đề lên TV." else "Đã cast video lên TV."
                    } else {
                        "TV từ chối media này."
                    }
                )
                refreshCastState()
            }
        } ?: run {
            Log.d(LOG_TAG, "castSelectedCandidate blocked: missing remote media client")
            uiState = uiState.copy(status = "Phiên Cast chưa có media client.")
        }
    }

    private fun queueSelectedCandidate() {
        val candidate = uiState.selectedCandidate ?: return
        val session = currentCastSession() ?: run {
            uiState = uiState.copy(status = "Hãy chọn TV trước khi thêm hàng đợi.")
            return
        }
        prepareSubtitleForCast { preparedSubtitle ->
            val itemBuilder = com.google.android.gms.cast.MediaQueueItem.Builder(candidate.toMediaInfo(preparedSubtitle))
                .setAutoplay(true)
            if (preparedSubtitle != null) {
                itemBuilder.setActiveTrackIds(longArrayOf(preparedSubtitle.trackId))
            }
            val item = itemBuilder.build()
            uiState = uiState.copy(status = "Đang thêm vào hàng đợi TV.")
            session.remoteMediaClient?.queueAppendItem(item, null)?.setResultCallback { result ->
                runOnUiThread {
                    uiState = uiState.copy(
                        status = if (result.status.isSuccess) "Đã thêm link vào hàng đợi." else "Không thêm được hàng đợi."
                    )
                    refreshCastState()
                }
            }
        }
    }

    private fun prepareSubtitleForCast(onReady: (PreparedSubtitle?) -> Unit) {
        val subtitle = uiState.activeSubtitle
        if (subtitle == null) {
            onReady(null)
            return
        }
        uiState = uiState.copy(status = "Đang chuẩn bị phụ đề ${MediaDetector.subtitleDisplayName(subtitle)}.")
        Thread {
            val result = runCatching { SubtitleProxyServer.prepare(subtitle) }
            runOnUiThread {
                result
                    .onSuccess(onReady)
                    .onFailure { error ->
                        val message = error.message ?: "Không chuẩn bị được phụ đề này."
                        uiState = uiState.copy(status = "$message Tắt phụ đề nếu chỉ muốn cast video.")
                    }
            }
        }.start()
    }

    private fun togglePlayback() {
        val client = currentCastSession()?.remoteMediaClient ?: return
        if (uiState.cast.playerState == "PLAYING") {
            client.pause()
        } else {
            client.play()
        }
        refreshCastState()
    }

    private fun stopPlayback() {
        currentCastSession()?.remoteMediaClient?.stop()
        refreshCastState("Đã dừng phát trên TV.")
    }

    private fun disconnectCast() {
        castContext?.sessionManager?.endCurrentSession(true)
        uiState = uiState.copy(
            cast = CastUiState(
                available = uiState.cast.available,
                message = "Đã ngắt kết nối TV."
            ),
            status = "Đã ngắt kết nối TV."
        )
    }

    private fun seekTo(positionMs: Long) {
        val options = MediaSeekOptions.Builder()
            .setPosition(positionMs)
            .build()
        currentCastSession()?.remoteMediaClient?.seek(options)
        uiState = uiState.copy(cast = uiState.cast.copy(positionMs = positionMs))
    }

    private fun setCastVolume(volume: Float) {
        currentCastSession()?.setVolume(volume.coerceIn(0f, 1f).toDouble())
        uiState = uiState.copy(cast = uiState.cast.copy(volume = volume.coerceIn(0f, 1f), muted = false))
    }

    private fun toggleMute() {
        val session = currentCastSession() ?: return
        val nextMuted = !uiState.cast.muted
        session.setMute(nextMuted)
        uiState = uiState.copy(cast = uiState.cast.copy(muted = nextMuted))
    }

    private fun currentCastSession(): CastSession? {
        return castContext?.sessionManager?.currentCastSession?.takeIf { it.isConnected }
    }

    private fun refreshCastState(message: String? = null) {
        val session = currentCastSession()
        val client = session?.remoteMediaClient
        val playerState = when (client?.playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> "PLAYING"
            MediaStatus.PLAYER_STATE_PAUSED -> "PAUSED"
            MediaStatus.PLAYER_STATE_BUFFERING -> "BUFFERING"
            MediaStatus.PLAYER_STATE_IDLE -> "IDLE"
            else -> uiState.cast.playerState
        }
        val metadataTitle = client?.mediaInfo?.metadata?.getString(MediaMetadata.KEY_TITLE).orEmpty()
        val idleMessage = when (client?.idleReason) {
            MediaStatus.IDLE_REASON_ERROR -> "TV báo lỗi phát media này."
            MediaStatus.IDLE_REASON_FINISHED -> "TV đã phát xong media."
            MediaStatus.IDLE_REASON_CANCELED -> "TV đã hủy media hiện tại."
            MediaStatus.IDLE_REASON_INTERRUPTED -> "Media trên TV bị thay thế."
            else -> null
        }
        val cast = uiState.cast.copy(
            available = castContext != null,
            connected = session != null,
            deviceName = session?.castDevice?.friendlyName ?: "Chưa chọn TV",
            playerState = if (session == null) "IDLE" else playerState,
            positionMs = client?.approximateStreamPosition ?: 0L,
            durationMs = client?.streamDuration ?: 0L,
            volume = session?.volume?.toFloat() ?: uiState.cast.volume,
            muted = session?.isMute ?: uiState.cast.muted,
            activeTitle = metadataTitle,
            message = message ?: idleMessage ?: if (session != null) "Đang kết nối ${session.castDevice?.friendlyName ?: "TV"}." else "Chọn TV bằng nút Cast."
        )
        uiState = uiState.copy(cast = cast)
    }

    private fun updateCastMessage(message: String) {
        uiState = uiState.copy(cast = uiState.cast.copy(message = message))
    }

    private fun MediaCandidate.toMediaInfo(preparedSubtitle: PreparedSubtitle? = null): MediaInfo {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, MediaDetector.safeTitle(this@toMediaInfo))
            if (poster.isNotBlank()) {
                addImage(com.google.android.gms.common.images.WebImage(Uri.parse(poster)))
            }
        }
        val builder = MediaInfo.Builder(url)
            .setContentType(contentType)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
        if (preparedSubtitle != null) {
            val trackBuilder = MediaTrack.Builder(preparedSubtitle.trackId, MediaTrack.TYPE_TEXT)
                .setContentId(preparedSubtitle.url)
                .setContentType(preparedSubtitle.contentType)
                .setName(preparedSubtitle.label)
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
            if (preparedSubtitle.language.isNotBlank()) {
                trackBuilder.setLanguage(preparedSubtitle.language)
            }
            builder.setMediaTracks(listOf(trackBuilder.build()))
        }
        return builder.build()
    }

    private fun domScanJavascript(): String {
        return """
            (function() {
              const out = [];
              const media = /\.(m3u8|mpd|mp4|m4v|webm|mov|mkv|avi|ogv)(\?|#|${'$'})|\/player\/master\//i;
              const subtitle = /\.(vtt|webvtt|srt|ttml|dfxp)(\?|#|${'$'})/i;
              const normalize = (url) => {
                if (!url) return;
                try {
                  return new URL(url, location.href).href;
                } catch (error) {}
              };
              const trackInfo = (track) => {
                const url = normalize(track.src || track.getAttribute("src"));
                if (!url || !subtitle.test(url)) return null;
                return {
                  url,
                  label: track.label || track.getAttribute("label") || "",
                  language: track.srclang || track.getAttribute("srclang") || "",
                  isDefault: Boolean(track.default),
                  kind: track.kind || track.getAttribute("kind") || "subtitles"
                };
              };
              const videoSubtitles = (video) => Array.from(video.querySelectorAll("track[src]")).map(trackInfo).filter(Boolean);
              const add = (url, source, title, poster, subtitles) => {
                const absolute = normalize(url);
                if (absolute && media.test(absolute)) out.push({ url: absolute, source, title: title || document.title || "", poster: poster || "", subtitles: subtitles || [] });
              };
              const addSubtitles = (subtitles) => {
                if (subtitles && subtitles.length) out.push({ url: "", source: "DOM", title: document.title || "", subtitles });
              };
              document.querySelectorAll("video").forEach((video) => {
                const subtitles = videoSubtitles(video);
                add(video.currentSrc, "DOM", document.title, video.poster, subtitles);
                add(video.src, "DOM", document.title, video.poster, subtitles);
                video.querySelectorAll("source[src]").forEach((source) => add(source.src, "DOM", document.title, video.poster, subtitles));
                addSubtitles(subtitles);
              });
              document.querySelectorAll("a[href], source[src]").forEach((element) => {
                add(element.href || element.src, "DOM", document.title, "");
              });
              const looseSubtitles = Array.from(document.querySelectorAll("track[src]")).map(trackInfo).filter(Boolean);
              addSubtitles(looseSubtitles);
              const pageData = Array.from(document.scripts).map((script) => script.textContent || "").filter(Boolean).join("\n").slice(0, 650000);
              if (pageData) out.push({ pageData });
              return JSON.stringify(out);
            })();
        """.trimIndent()
    }
}

private val AppUiState.visibleCandidates: List<MediaCandidate>
    get() = candidates.values.filter(MediaDetector::isVisibleCandidate)
        .sortedWith(compareByDescending<MediaCandidate> { MediaDetector.rank(it) }.thenByDescending { it.seenAt })

private val AppUiState.selectedCandidate: MediaCandidate?
    get() = selectedCandidateUrl?.let { candidates[it] } ?: visibleCandidates.firstOrNull()

private val AppUiState.visibleSubtitles: List<SubtitleCandidate>
    get() = (selectedCandidate?.subtitles.orEmpty() + subtitles.values)
        .filter { it.castSupported }
        .distinctBy { it.url }
        .sortedWith(compareByDescending<SubtitleCandidate> { it.isDefault }.thenByDescending { it.score }.thenByDescending { it.seenAt })

private val AppUiState.activeSubtitle: SubtitleCandidate?
    get() {
        val visible = visibleSubtitles
        return when (selectedSubtitleUrl) {
            SUBTITLE_OFF -> null
            SUBTITLE_AUTO -> visible.firstOrNull { it.isDefault } ?: visible.firstOrNull()
            else -> visible.firstOrNull { it.url == selectedSubtitleUrl }
        }
    }

private fun Int.dpValue(context: Context): Int = (this * context.resources.displayMetrics.density).roundToInt()

@Composable
fun MovieCastTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF27D4BA),
        onPrimary = Color(0xFF061310),
        secondary = Color(0xFFE8C45B),
        onSecondary = Color(0xFF1B1502),
        background = Color(0xFF0B1014),
        onBackground = Color(0xFFE8EEF5),
        surface = Color(0xFF121920),
        onSurface = Color(0xFFE8EEF5),
        surfaceVariant = Color(0xFF18222A),
        onSurfaceVariant = Color(0xFFADBAC8),
        outline = Color(0xFF33424E)
    )
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}

@Composable
fun MovieCastScreen(
    uiState: AppUiState,
    onWebViewReady: (WebView) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onAppMuteToggle: () -> Unit,
    onRescan: () -> Unit,
    onSelectCandidate: (String) -> Unit,
    onSubtitleSelected: (String) -> Unit,
    onClearCandidates: () -> Unit,
    onManualCandidate: (String) -> Unit,
    onCastSelected: () -> Unit,
    onQueueSelected: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onDisconnect: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onHistoryOpen: (String) -> Unit,
    onHistoryClear: () -> Unit,
    setupCastButton: (MediaRouteButton) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B1014), Color(0xFF10161C), Color(0xFF0B1014))
                    )
                )
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                BrowserSection(
                    uiState = uiState,
                    onWebViewReady = onWebViewReady,
                    onNavigate = onNavigate,
                    onBack = onBack,
                    onForward = onForward,
                    onReload = onReload,
                    onAppMuteToggle = onAppMuteToggle,
                    onAddressFocusChanged = {},
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (selectedTab == 0) 1f else 0f)
                        .zIndex(if (selectedTab == 0) 1f else 0f)
                )
                ControlPanel(
                    uiState = uiState,
                    onRescan = onRescan,
                    onSelectCandidate = onSelectCandidate,
                    onSubtitleSelected = onSubtitleSelected,
                    onClearCandidates = onClearCandidates,
                    onManualCandidate = onManualCandidate,
                    onCastSelected = onCastSelected,
                    onQueueSelected = onQueueSelected,
                    onPlayPause = onPlayPause,
                    onStop = onStop,
                    onDisconnect = onDisconnect,
                    onSeek = onSeek,
                    onVolume = onVolume,
                    onMuteToggle = onMuteToggle,
                    onHistoryOpen = onHistoryOpen,
                    onHistoryClear = onHistoryClear,
                    setupCastButton = setupCastButton,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (selectedTab == 1) 1f else 0f)
                        .zIndex(if (selectedTab == 1) 1f else 0f)
                )
            }
            BottomTabs(selectedTab = selectedTab, onSelect = { selectedTab = it })
        }
    }
}

@Composable
private fun BottomTabs(selectedTab: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Trình duyệt", "Cast")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        NavigationBar(
            containerColor = Color(0xFF101820),
            contentColor = MaterialTheme.colorScheme.primary,
            windowInsets = WindowInsets(0.dp),
            modifier = Modifier
                .width(184.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
        ) {
            labels.forEachIndexed { index, label ->
                NavigationBarItem(
                    selected = selectedTab == index,
                    onClick = { onSelect(index) },
                    modifier = Modifier.height(48.dp),
                    icon = {
                        Icon(
                            if (index == 0) Icons.Filled.Search else Icons.Filled.Tv,
                            contentDescription = label,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BrowserSection(
    uiState: AppUiState,
    onWebViewReady: (WebView) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onAppMuteToggle: () -> Unit,
    onAddressFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var addressDraft by remember {
        mutableStateOf(
            TextFieldValue(
                text = uiState.address,
                selection = TextRange(0, uiState.address.length)
            )
        )
    }
    var keepAddressSelected by remember { mutableStateOf(true) }
    fun selectAddress() {
        keepAddressSelected = true
        addressDraft = addressDraft.copy(selection = TextRange(0, addressDraft.text.length))
    }
    fun updateAddressDraft(nextValue: TextFieldValue) {
        val textChanged = nextValue.text != addressDraft.text
        addressDraft = if (keepAddressSelected && !textChanged) {
            nextValue.copy(selection = TextRange(0, nextValue.text.length))
        } else {
            nextValue
        }
        if (textChanged) {
            keepAddressSelected = false
        }
    }
    LaunchedEffect(uiState.address) {
        if (uiState.address.isNotBlank() && uiState.address != addressDraft.text) {
            keepAddressSelected = true
            addressDraft = TextFieldValue(
                text = uiState.address,
                selection = TextRange(0, uiState.address.length)
            )
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
            .background(Color(0xFF0F151B))
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).also(onWebViewReady)
            },
            modifier = Modifier.fillMaxSize()
        )
        AnimatedVisibility(
            visible = !uiState.browserChromeHidden,
            enter = slideInVertically(animationSpec = tween(160), initialOffsetY = { -it }) + fadeIn(animationSpec = tween(120)),
            exit = slideOutVertically(animationSpec = tween(160), targetOffsetY = { -it }) + fadeOut(animationSpec = tween(120)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(2f)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F151B).copy(alpha = 0.98f))
                    .padding(10.dp)
            ) {
                if (maxWidth < 620.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BrandMark(modifier = Modifier.weight(1f))
                            NavigationButtons(onBack, onForward, onReload)
                            AppMuteButton(uiState.appMuted, onAppMuteToggle)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AddressField(
                                value = addressDraft,
                                onValueChange = ::updateAddressDraft,
                                onSelectAll = ::selectAddress,
                                onFocusChange = onAddressFocusChanged,
                                modifier = Modifier.weight(1f)
                            )
                            OpenButton(onClick = { onNavigate(addressDraft.text) })
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BrandMark()
                        NavigationButtons(onBack, onForward, onReload)
                        AppMuteButton(uiState.appMuted, onAppMuteToggle)
                        AddressField(
                            value = addressDraft,
                            onValueChange = ::updateAddressDraft,
                            onSelectAll = ::selectAddress,
                            onFocusChange = onAddressFocusChanged,
                            modifier = Modifier.weight(1f)
                        )
                        OpenButton(onClick = { onNavigate(addressDraft.text) })
                    }
                }
            }
        }
        if (uiState.loading) {
            StatusPill(
                text = "Đang tải trang",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (uiState.browserChromeHidden) 14.dp else 88.dp)
                    .zIndex(3f)
            )
        }
    }
}

@Composable
private fun ControlPanel(
    uiState: AppUiState,
    onRescan: () -> Unit,
    onSelectCandidate: (String) -> Unit,
    onSubtitleSelected: (String) -> Unit,
    onClearCandidates: () -> Unit,
    onManualCandidate: (String) -> Unit,
    onCastSelected: () -> Unit,
    onQueueSelected: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onDisconnect: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onHistoryOpen: (String) -> Unit,
    onHistoryClear: () -> Unit,
    setupCastButton: (MediaRouteButton) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
            .background(Color(0xFF101820))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val hasActiveMedia = uiState.cast.hasActiveMedia
        SessionCard(uiState.cast, setupCastButton, onDisconnect)
        if (hasActiveMedia) {
            MediaControls(uiState.cast, onPlayPause, onStop, onSeek, onVolume, onMuteToggle)
        }
        LinkCard(
            uiState = uiState,
            showPreview = !hasActiveMedia,
            onRescan = onRescan,
            onSelectCandidate = onSelectCandidate,
            onSubtitleSelected = onSubtitleSelected,
            onClearCandidates = onClearCandidates,
            onCastSelected = onCastSelected,
            onQueueSelected = onQueueSelected
        )
        if (!hasActiveMedia) {
            ManualLinkCard(onManualCandidate)
            HistoryCard(uiState.history, onHistoryOpen, onHistoryClear)
        }
        Text(
            text = uiState.status,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun SessionCard(
    cast: CastUiState,
    setupCastButton: (MediaRouteButton) -> Unit,
    onDisconnect: () -> Unit
) {
    PanelCard {
        SectionHeader(title = "Phiên Cast", badge = if (cast.connected) "Đang nối TV" else "Chưa nối")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (cast.connected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color(0xFF17212A)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { context ->
                        MediaRouteButton(context).also(setupCastButton)
                    },
                    update = { it.isEnabled = cast.available },
                    modifier = Modifier.size(44.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cast.deviceName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = cast.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (cast.connected) {
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ngắt kết nối TV")
            }
        }
    }
}

@Composable
private fun MediaControls(
    cast: CastUiState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    onMuteToggle: () -> Unit
) {
    val durationMs = cast.durationMs.coerceAtLeast(0L)
    val maxSeekMs = durationMs.takeIf { it > 0L } ?: 1L
    val remotePositionMs = if (durationMs > 0L) cast.positionMs.coerceIn(0L, durationMs) else 0L
    var seekValue by remember { mutableStateOf(remotePositionMs.toFloat()) }
    var seekEditing by remember { mutableStateOf(false) }
    LaunchedEffect(remotePositionMs, durationMs, seekEditing) {
        if (!seekEditing) {
            seekValue = remotePositionMs.toFloat()
        }
    }
    val displayPositionMs = if (seekEditing) seekValue.toLong().coerceIn(0L, maxSeekMs) else remotePositionMs
    val canSeek = cast.connected && durationMs > 0L
    PanelCard {
        SectionHeader(title = "Điều khiển TV", badge = cast.playerState)
        MarqueeText(
            text = cast.activeTitle.ifBlank { "Chưa có media đang phát" },
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPlayPause,
                enabled = cast.connected,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    if (cast.playerState == "PLAYING") Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Phát hoặc tạm dừng",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton(onClick = onStop, enabled = cast.connected) {
                Icon(Icons.Filled.Stop, contentDescription = "Dừng")
            }
            Text(
                text = "${formatMillis(displayPositionMs)} / ${formatMillis(durationMs)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(formatMillis(displayPositionMs), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                if (seekEditing) {
                    StatusPill(text = "Tới ${formatMillis(displayPositionMs)}")
                    Spacer(Modifier.width(8.dp))
                }
                Text(formatMillis(durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = seekValue.coerceIn(0f, maxSeekMs.toFloat()),
                onValueChange = {
                    seekEditing = true
                    seekValue = it.coerceIn(0f, maxSeekMs.toFloat())
                },
                onValueChangeFinished = {
                    val targetMs = seekValue.toLong().coerceIn(0L, maxSeekMs)
                    seekEditing = false
                    if (canSeek) onSeek(targetMs)
                },
                enabled = canSeek,
                valueRange = 0f..maxSeekMs.toFloat()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMuteToggle, enabled = cast.connected) {
                Icon(if (cast.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Tắt hoặc bật âm")
            }
            Slider(
                value = cast.volume.coerceIn(0f, 1f),
                onValueChange = onVolume,
                enabled = cast.connected,
                modifier = Modifier.weight(1f)
            )
            Text("${(cast.volume * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LinkCard(
    uiState: AppUiState,
    showPreview: Boolean,
    onRescan: () -> Unit,
    onSelectCandidate: (String) -> Unit,
    onSubtitleSelected: (String) -> Unit,
    onClearCandidates: () -> Unit,
    onCastSelected: () -> Unit,
    onQueueSelected: () -> Unit
) {
    val visible = uiState.visibleCandidates
    val selected = uiState.selectedCandidate
    val hiddenCount = (uiState.candidates.size - visible.size).coerceAtLeast(0)
    PanelCard {
        SectionHeader(
            title = "Link phim",
            badge = if (selected != null) "1 link" else "0 link",
            action = {
                TextButton(onClick = onRescan) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (uiState.scanning) "Đang quét" else "Quét lại")
                }
            }
        )
        if (selected == null) {
            EmptyLinkState(uiState.scanning)
        } else {
            var previewInfo by remember(selected.url) { mutableStateOf<MediaPreviewInfo?>(null) }
            CandidateRow(
                candidate = selected,
                selected = true,
                mediaInfo = previewInfo,
                onClick = { onSelectCandidate(selected.url) }
            )
            if (showPreview) {
                PreviewPlayer(selected, onInfo = { previewInfo = it })
            } else {
                Text(
                    text = "Preview đang thu gọn khi TV đang phát.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            SubtitlePicker(
                subtitles = uiState.visibleSubtitles,
                selectedSubtitleUrl = uiState.selectedSubtitleUrl,
                onSubtitleSelected = onSubtitleSelected
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onCastSelected, enabled = uiState.cast.connected, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (uiState.cast.hasActiveMedia) "Cast lại" else "Cast lên TV")
                }
                OutlinedButton(onClick = onQueueSelected, enabled = uiState.cast.connected, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Hàng đợi")
                }
            }
            if (hiddenCount > 0) {
                Text(
                    text = "Đã ẩn $hiddenCount link phụ hoặc quảng cáo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onClearCandidates) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Xóa link hiện tại")
            }
        }
    }
}

@Composable
private fun SubtitlePicker(
    subtitles: List<SubtitleCandidate>,
    selectedSubtitleUrl: String,
    onSubtitleSelected: (String) -> Unit
) {
    if (subtitles.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Phụ đề",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            StatusPill(text = "${subtitles.size} track")
            Spacer(Modifier.weight(1f))
            val activeName = when (selectedSubtitleUrl) {
                SUBTITLE_OFF -> "Tắt"
                SUBTITLE_AUTO -> "Tự động"
                else -> subtitles.firstOrNull { it.url == selectedSubtitleUrl }?.let(MediaDetector::subtitleDisplayName) ?: "Tự động"
            }
            Text(
                text = activeName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            SubtitleChip("Tự động", selectedSubtitleUrl == SUBTITLE_AUTO) { onSubtitleSelected(SUBTITLE_AUTO) }
            SubtitleChip("Tắt", selectedSubtitleUrl == SUBTITLE_OFF) { onSubtitleSelected(SUBTITLE_OFF) }
            subtitles.forEach { subtitle ->
                SubtitleChip(
                    text = MediaDetector.subtitleDisplayName(subtitle),
                    selected = selectedSubtitleUrl == subtitle.url,
                    onClick = { onSubtitleSelected(subtitle.url) }
                )
            }
        }
    }
}

@Composable
private fun SubtitleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ManualLinkCard(onManualCandidate: (String) -> Unit) {
    var manual by remember { mutableStateOf("") }
    PanelCard {
        SectionHeader(title = "Nhập link media", badge = "Manual")
        OutlinedTextField(
            value = manual,
            onValueChange = { manual = it },
            singleLine = true,
            placeholder = { Text("https://...m3u8") },
            colors = textFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                onManualCandidate(manual)
                manual = ""
            },
            enabled = manual.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Thêm link")
        }
    }
}

@Composable
private fun HistoryCard(history: List<HistoryEntry>, onHistoryOpen: (String) -> Unit, onHistoryClear: () -> Unit) {
    PanelCard {
        SectionHeader(
            title = "Lịch sử",
            badge = "${history.size}",
            action = {
                TextButton(onClick = onHistoryClear, enabled = history.isNotEmpty()) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Xóa")
                }
            }
        )
        if (history.isEmpty()) {
            Text("Chưa có lịch sử.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            history.take(6).forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onHistoryOpen(item.url) }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title.ifBlank { MediaDetector.hostForUrl(item.url) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: MediaCandidate,
    selected: Boolean,
    mediaInfo: MediaPreviewInfo?,
    onClick: () -> Unit
) {
    val label = MediaDetector.mediaTypeLabel(candidate.contentType, candidate.url)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color(0xFF141E27))
            .border(
                BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(
                text = MediaDetector.safeTitle(candidate),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Text(MediaDetector.hostForUrl(candidate.url), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            MediaInfoLine(mediaInfo)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Text(
        text = text,
        color = color,
        fontWeight = fontWeight,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 900)
    )
}

@Composable
private fun MediaInfoLine(info: MediaPreviewInfo?) {
    val label = mediaInfoLabel(info)
    if (label.isBlank()) return
    Text(
        text = label,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun PreviewPlayer(candidate: MediaCandidate, onInfo: (MediaPreviewInfo?) -> Unit) {
    val context = LocalContext.current
    var previewError by remember { mutableStateOf<String?>(null) }
    var mediaInfo by remember(candidate.url) { mutableStateOf(MediaPreviewInfo(url = candidate.url)) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            playWhenReady = false
        }
    }
    fun publishInfo() {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        val size = player.videoSize
        val next = mediaInfo.copy(
            durationMs = duration,
            width = size.width.takeIf { it > 0 } ?: mediaInfo.width,
            height = size.height.takeIf { it > 0 } ?: mediaInfo.height,
            live = player.isCurrentMediaItemLive
        )
        mediaInfo = next
        onInfo(next.takeIf { it.durationMs > 0L || it.height > 0 || it.live })
    }
    DisposableEffect(player, candidate.url) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    previewError = null
                }
                publishInfo()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                publishInfo()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                publishInfo()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            onInfo(null)
        }
    }
    LaunchedEffect(candidate.url) {
        previewError = null
        mediaInfo = MediaPreviewInfo(url = candidate.url)
        onInfo(null)
        runCatching {
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(candidate.url))
            player.prepare()
        }.onFailure {
            previewError = "Không preview được link này, vẫn có thể cast thử lên TV."
            onInfo(null)
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    if (previewError != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0C1218))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = previewError.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AndroidView(
            factory = { viewContext ->
                (LayoutInflater.from(viewContext).inflate(R.layout.preview_player, null, false) as PlayerView).apply {
                    this.player = player
                    useController = true
                    setEnableComposeSurfaceSyncWorkaround(true)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setBackgroundColor(AndroidColor.BLACK)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { it.player = player },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        )
        MediaInfoLine(mediaInfo.takeIf { it.durationMs > 0L || it.height > 0 || it.live })
    }
}

@Composable
private fun EmptyLinkState(scanning: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141E27))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (scanning) "Đang chờ player tải video." else "Chưa thấy link phim.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141D25)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SectionHeader(title: String, badge: String, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        StatusPill(text = badge)
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

@Composable
private fun NavigationButtons(onBack: () -> Unit, onForward: () -> Unit, onReload: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
        }
        IconButton(onClick = onForward, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Đi tiếp")
        }
        IconButton(onClick = onReload, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = "Tải lại")
        }
    }
}

@Composable
private fun AppMuteButton(appMuted: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
        Icon(
            if (appMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (appMuted) "Bật âm thanh app" else "Tắt âm thanh app",
            tint = if (appMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddressField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSelectAll: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = modifier
            .onFocusChanged { state ->
                onFocusChange(state.isFocused)
                if (state.isFocused) onSelectAll()
            }
            .pointerInput(value.text) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    if (waitForUpOrCancellation(pass = PointerEventPass.Final) != null) {
                        onSelectAll()
                    }
                }
            },
        placeholder = { Text("https://...") },
        colors = textFieldColors(),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun OpenButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(56.dp)
            .widthIn(min = 96.dp),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Mở", maxLines = 1)
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.widthIn(min = 132.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(19.dp))
        }
        Column {
            Text("Movie Cast", fontWeight = FontWeight.Bold, maxLines = 1)
            Text("Android", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
    focusedContainerColor = Color(0xFF111A22),
    unfocusedContainerColor = Color(0xFF111A22),
    cursorColor = MaterialTheme.colorScheme.primary
)

private fun formatMillis(value: Long): String {
    val totalSeconds = (value / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun mediaInfoLabel(info: MediaPreviewInfo?): String {
    if (info == null) return ""
    val parts = mutableListOf<String>()
    if (info.live) {
        parts += "Trực tiếp"
    } else if (info.durationMs > 0L) {
        parts += "Thời lượng ${formatMillis(info.durationMs)}"
    }
    resolutionLabel(info.height).takeIf { it.isNotBlank() }?.let { label ->
        val dimensions = if (info.width > 0 && info.height > 0) " ${info.width}x${info.height}" else ""
        parts += "Độ phân giải $label$dimensions"
    }
    return parts.joinToString(" - ")
}

private fun resolutionLabel(height: Int): String {
    return when {
        height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        height >= 360 -> "360p"
        height > 0 -> "${height}p"
        else -> ""
    }
}
