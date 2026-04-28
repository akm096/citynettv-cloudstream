package com.citynettv.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var client: CityNetTvClient
    private lateinit var root: FrameLayout
    private lateinit var loginPanel: LinearLayout
    private lateinit var contentPanel: LinearLayout
    private lateinit var playerPanel: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var loginMessage: TextView
    private lateinit var userInput: EditText
    private lateinit var passInput: EditText
    private lateinit var channelGrid: GridLayout
    private lateinit var playerView: PlayerView

    private var player: ExoPlayer? = null
    private var channels: List<TvChannel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = CityNetTvClient(this)
        buildUi()
        if (client.isLoggedIn()) {
            showContent()
            loadChannels()
        } else {
            showLogin()
        }
    }

    override fun onDestroy() {
        player?.release()
        webView.destroy()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBackPressed() {
        when {
            webView.visibility == View.VISIBLE -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    showContent()
                }
            }
            playerPanel.visibility == View.VISIBLE -> showContent()
            loginPanel.visibility == View.VISIBLE -> super.onBackPressed()
            else -> super.onBackPressed()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && playerPanel.visibility == View.VISIBLE) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    player?.let {
                        if (it.isPlaying) it.pause() else it.play()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(BG)
        }
        setContentView(root)

        contentPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(24), dp(36), dp(24))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "CityNetTV"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        }
        topBar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(actionButton("Retry") { loadChannels() })
        topBar.addView(actionButton("Open Web") { showWebFallback("Opening CityNetTV web fallback.") })
        topBar.addView(actionButton("Logout") {
            client.logout()
            showLogin()
        })
        contentPanel.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        statusText = TextView(this).apply {
            setTextColor(TEXT_DIM)
            textSize = 16f
            text = "Ready"
        }
        contentPanel.addView(statusText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
        }
        channelGrid = GridLayout(this).apply {
            columnCount = 5
            useDefaultMargins = false
        }
        scroll.addView(channelGrid)
        contentPanel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(contentPanel, matchParent())

        loginPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(80), dp(40), dp(80), dp(40))
            setBackgroundColor(BG)
        }
        val loginTitle = TextView(this).apply {
            text = "CityNetTV Giris"
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        loginPanel.addView(loginTitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        userInput = editText("Nomre").apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(client.savedUser())
        }
        passInput = editText("Sifre").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(client.savedPass())
        }
        loginPanel.addView(userInput, formParams())
        loginPanel.addView(passInput, formParams())
        loginPanel.addView(actionButton("Login") { doLogin() }, formParams())
        loginMessage = TextView(this).apply {
            setTextColor(TEXT_DIM)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        loginPanel.addView(loginMessage, LinearLayout.LayoutParams(dp(520), dp(56)))
        root.addView(loginPanel, matchParent())

        playerPanel = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
        }
        playerView = PlayerView(this).apply {
            useController = true
            controllerAutoShow = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            isFocusable = true
        }
        playerPanel.addView(playerView, matchParent())
        root.addView(playerPanel, matchParent())

        webView = WebView(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = client.webUserAgent()
        }
        root.addView(webView, matchParent())

        progress = ProgressBar(this).apply {
            visibility = View.GONE
        }
        root.addView(
            progress,
            FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER)
        )
    }

    private fun doLogin() {
        setBusy(true, "Logging in...")
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                client.login(userInput.text.toString().trim(), passInput.text.toString())
            }
            setBusy(false)
            if (ok) {
                showContent()
                loadChannels()
            } else {
                showLogin(client.lastError ?: "Giris alinmadi.")
            }
        }
    }

    private fun loadChannels() {
        showContent()
        setBusy(true, "Loading channels...")
        scope.launch {
            val list = withContext(Dispatchers.IO) { client.channels() }
            channels = list
            setBusy(false)
            if (list.isEmpty()) {
                statusText.text = client.lastError ?: "Kanal siyahisi alinmadi."
                renderChannels(emptyList())
            } else {
                statusText.text = "${list.size} kanal hazirdir"
                renderChannels(list)
            }
        }
    }

    private fun renderChannels(list: List<TvChannel>) {
        channelGrid.removeAllViews()
        list.forEachIndexed { index, channel ->
            val card = Button(this).apply {
                text = buildString {
                    channel.number?.let { append("$it  ") }
                    append(channel.titleText())
                }
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                isAllCaps = false
                maxLines = 2
                background = bg(CARD, ACCENT, focused = false)
                setOnFocusChangeListener { view, hasFocus ->
                    view.background = bg(if (hasFocus) CARD_FOCUS else CARD, ACCENT, focused = hasFocus)
                }
                setOnClickListener { playChannel(channel) }
            }
            val params = GridLayout.LayoutParams().apply {
                width = dp(210)
                height = dp(94)
                setMargins(dp(8), dp(8), dp(8), dp(8))
                columnSpec = GridLayout.spec(index % 5)
            }
            channelGrid.addView(card, params)
        }
    }

    private fun playChannel(channel: TvChannel) {
        setBusy(true, "Opening ${channel.titleText()}...")
        scope.launch {
            val item = withContext(Dispatchers.IO) { client.playback(channel) }
            setBusy(false)
            if (item == null) {
                showWebFallback(client.lastError ?: "Stream tapilmadi. Web fallback acilir.")
            } else {
                startPlayer(item)
            }
        }
    }

    private fun startPlayer(item: PlaybackItem) {
        webView.visibility = View.GONE
        contentPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        playerPanel.visibility = View.VISIBLE

        player?.release()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(client.webUserAgent())
            .setDefaultRequestProperties(item.headers)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpFactory)
        val mediaItem = MediaItem.Builder()
            .setUri(item.url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.name).build())
            .apply {
                item.licenseUrl?.let { license ->
                    setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(license)
                            .setLicenseRequestHeaders(item.headers)
                            .build()
                    )
                }
            }
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        showWebFallback("Player error: ${error.errorCodeName}. Web fallback acilir.")
                    }
                })
                playerView.player = exo
                exo.setMediaItem(mediaItem)
                exo.prepare()
                exo.playWhenReady = true
            }
        playerView.requestFocus()
    }

    private fun showWebFallback(message: String) {
        player?.pause()
        playerPanel.visibility = View.GONE
        contentPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        webView.visibility = View.VISIBLE
        statusText.text = message
        if (webView.url.isNullOrBlank()) {
            webView.loadUrl(client.webUrl(), clientHeaders())
        }
        webView.requestFocus()
    }

    private fun clientHeaders(): Map<String, String> = mapOf(
        "Origin" to "https://tv.citynettv.az",
        "Referer" to "https://tv.citynettv.az/"
    )

    private fun showLogin(message: String? = null) {
        player?.pause()
        playerPanel.visibility = View.GONE
        contentPanel.visibility = View.GONE
        webView.visibility = View.GONE
        loginPanel.visibility = View.VISIBLE
        loginMessage.text = message ?: ""
        userInput.requestFocus()
    }

    private fun showContent() {
        player?.pause()
        playerPanel.visibility = View.GONE
        webView.visibility = View.GONE
        loginPanel.visibility = View.GONE
        contentPanel.visibility = View.VISIBLE
        if (channels.isNotEmpty()) {
            channelGrid.getChildAt(0)?.requestFocus()
        }
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        message?.let {
            statusText.text = it
        }
    }

    private fun editText(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 20f
        setSingleLine(true)
        setTextColor(Color.WHITE)
        setHintTextColor(TEXT_DIM)
        setPadding(dp(18), 0, dp(18), 0)
        background = bg(CARD, ACCENT, focused = false)
        setOnFocusChangeListener { view, hasFocus ->
            view.background = bg(if (hasFocus) CARD_FOCUS else CARD, ACCENT, focused = hasFocus)
        }
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = bg(CARD, ACCENT, focused = false)
        minHeight = 0
        minWidth = 0
        setPadding(dp(18), 0, dp(18), 0)
        setOnFocusChangeListener { view, hasFocus ->
            view.background = bg(if (hasFocus) CARD_FOCUS else CARD, ACCENT, focused = hasFocus)
        }
        setOnClickListener { onClick() }
    }

    private fun formParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(420), dp(58)).apply {
            setMargins(0, dp(8), 0, dp(8))
        }

    private fun matchParent(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun bg(color: Int, stroke: Int, focused: Boolean): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(6).toFloat()
            setStroke(if (focused) dp(3) else dp(1), if (focused) stroke else EDGE)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val BG: Int = Color.rgb(13, 17, 19)
        val CARD: Int = Color.rgb(28, 34, 38)
        val CARD_FOCUS: Int = Color.rgb(38, 68, 64)
        val EDGE: Int = Color.rgb(55, 65, 70)
        val ACCENT: Int = Color.rgb(37, 194, 160)
        val TEXT_DIM: Int = Color.rgb(170, 184, 190)
    }
}
