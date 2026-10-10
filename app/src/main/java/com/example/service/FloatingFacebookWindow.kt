package com.example.service

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * Floating Facebook Web Overlay Window.
 * Enables browsing mobile Facebook (m.facebook.com), logging in, and with 1-tap:
 * - Auto-extracting & copying the logged-in Facebook User ID (UID from c_user cookie or URL)
 * - Copying Profile URL / Link
 * - Direct navigation to user's profile (/me)
 * All while staying floating on top of any other app or spreadsheet!
 */
class FloatingFacebookWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onCloseListener: () -> Unit
) {
    private var windowParams: WindowManager.LayoutParams? = null
    private var rootView: View? = null
    private var webView: WebView? = null
    private var isMinimized: Boolean = false

    private lateinit var urlStatusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var uidBadgeText: TextView

    var isShowing: Boolean = false
        private set

    companion object {
        const val FACEBOOK_MOBILE_URL = "https://m.facebook.com"
    }

    fun show() {
        if (isShowing) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val windowWidth = (dpToPx(340)).coerceAtMost((screenWidth * 0.94f).toInt())
        val windowHeight = (dpToPx(500)).coerceAtMost((screenHeight * 0.70f).toInt())

        // Default: FLAG_NOT_FOCUSABLE so underlying phone apps operate 100% unrestricted without interference!
        val params = WindowManager.LayoutParams(
            windowWidth,
            windowHeight,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - windowWidth - dpToPx(8)).coerceAtLeast(0)
            y = dpToPx(110)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        windowParams = params

        val view = buildWindowView(params, windowWidth, windowHeight)
        rootView = view

        try {
            windowManager.addView(view, params)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Cannot open Facebook window: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun dismiss() {
        if (!isShowing) return
        releaseInputFocus()
        clearAllWebDataAndCookies()
        rootView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        webView?.destroy()
        webView = null
        rootView = null
        isShowing = false
        onCloseListener()
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun buildWindowView(
        params: WindowManager.LayoutParams,
        normalWidth: Int,
        normalHeight: Int
    ): View {
        val rootContainer = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(normalWidth, normalHeight)
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    releaseInputFocus()
                }
                false
            }
        }

        // Expanded Main Card
        val mainCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14).toFloat()
                setColor(Color.parseColor("#18191A")) // Modern Facebook dark theme background
                setStroke(dpToPx(1.5f), Color.parseColor("#1877F2")) // Facebook signature blue accent border
            }
            background = cardBg
            elevation = dpToPx(8).toFloat()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Sleek Top Header Bar (Draggable)
        val headerBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val headerBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    dpToPx(14).toFloat(), dpToPx(14).toFloat(), // Top Left
                    dpToPx(14).toFloat(), dpToPx(14).toFloat(), // Top Right
                    0f, 0f, 0f, 0f
                )
                colors = intArrayOf(Color.parseColor("#1877F2"), Color.parseColor("#0C63D4"))
                orientation = GradientDrawable.Orientation.TL_BR
            }
            background = headerBg
            setPadding(dpToPx(10), dpToPx(7), dpToPx(10), dpToPx(7))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Drag grip
        val dragHandle = TextView(context).apply {
            text = "::: "
            setTextColor(Color.parseColor("#E4E6EB"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, dpToPx(4), 0)
        }

        // Authentic Facebook Vector Logo
        val fbLogoIv = android.widget.ImageView(context).apply {
            setImageResource(com.example.R.drawable.ic_facebook_logo)
            val logoSize = dpToPx(18)
            val lp = LinearLayout.LayoutParams(logoSize, logoSize).apply {
                marginEnd = dpToPx(6)
            }
            layoutParams = lp
        }

        val titleView = TextView(context).apply {
            text = "Facebook Web"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Minimize Button
        val minimizeBtn = createHeaderIconText("—") {
            toggleMinimize(rootContainer, normalWidth, normalHeight)
        }

        // Close Button (Completely clears all cookies, web storage, and session data)
        val closeBtn = createHeaderIconText("✕") {
            clearAllWebDataAndCookies()
            Toast.makeText(context, "🧹 All Facebook data & cookies cleared!", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        headerBar.addView(dragHandle)
        headerBar.addView(fbLogoIv)
        headerBar.addView(titleView)
        headerBar.addView(minimizeBtn)
        headerBar.addView(closeBtn)

        setupDragListener(headerBar, params)
        mainCard.addView(headerBar)

        // 2. Action Toolbar: COPY UID and COPY COOKIE
        val toolBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#242526"))
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Primary Action: COPY UID BUTTON (Emerald Green)
        val copyUidBtn = TextView(context).apply {
            text = "📋 COPY UID"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                colors = intArrayOf(Color.parseColor("#10B981"), Color.parseColor("#059669")) // Vibrant Emerald
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            background = btnBg
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            val lp = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dpToPx(6)
            }
            layoutParams = lp

            setOnClickListener {
                copyFacebookUserId()
            }
        }

        // Secondary Action: COPY COOKIE BUTTON (Warm Amber / Gold)
        val copyCookieBtn = TextView(context).apply {
            text = "🍪 COPY COOKIE"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                colors = intArrayOf(Color.parseColor("#F59E0B"), Color.parseColor("#D97706")) // Amber
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            background = btnBg
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            val lp = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            layoutParams = lp

            setOnClickListener {
                copyFacebookCookies()
            }
        }

        toolBar.addView(copyUidBtn)
        toolBar.addView(copyCookieBtn)
        mainCard.addView(toolBar)

        // 3. Status Bar & URL / UID Indicator
        val statusBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1E1F21"))
            setPadding(dpToPx(10), dpToPx(3), dpToPx(10), dpToPx(3))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        urlStatusText = TextView(context).apply {
            text = "Loading Facebook..."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 10f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        uidBadgeText = TextView(context).apply {
            text = "UID: Not Logged In"
            setTextColor(Color.parseColor("#FBBF24")) // Amber
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(4), 0, 0, 0)
        }

        statusBar.addView(urlStatusText)
        statusBar.addView(uidBadgeText)
        mainCard.addView(statusBar)

        // Progress Bar
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(2.5f)
            )
        }
        mainCard.addView(progressBar)

        // 4. Embedded WebView
        val wv = WebView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            configureWebView(this)
        }
        webView = wv
        mainCard.addView(wv)

        // 5. Bottom Navigation Bar: Back, Forward, Home, Reload
        val bottomNav = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#242526"))
            setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val backBtn = createNavIconButton("◀") {
            if (wv.canGoBack()) wv.goBack()
            else Toast.makeText(context, "Cannot go back", Toast.LENGTH_SHORT).show()
        }
        val forwardBtn = createNavIconButton("▶") {
            if (wv.canGoForward()) wv.goForward()
        }
        val homeBtn = createNavIconButton("🏠 Home") {
            wv.loadUrl(FACEBOOK_MOBILE_URL)
        }
        val reloadBtn = createNavIconButton("🔄") {
            wv.reload()
        }

        bottomNav.addView(backBtn)
        bottomNav.addView(forwardBtn)
        bottomNav.addView(homeBtn)
        bottomNav.addView(reloadBtn)
        mainCard.addView(bottomNav)

        rootContainer.addView(mainCard)

        // Start loading mobile Facebook
        wv.loadUrl(FACEBOOK_MOBILE_URL)

        return rootContainer
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun configureWebView(wv: WebView) {
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Cookie manager setup to preserve login sessions
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(wv, true)

        // Make webview fully focusable and dynamically request focus so keyboard appears automatically when tapping text inputs
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        wv.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                requestInputFocus()
                if (!v.hasFocus()) {
                    v.requestFocus()
                }
            }
            false
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                view?.loadUrl(url)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                urlStatusText.text = url ?: "Loading..."
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                urlStatusText.text = url ?: ""
                checkAndDisplayDetectedUid()
            }
        }
    }

    /**
     * Checks Facebook session cookies strictly for 'c_user' (which only exists when logged in).
     */
    fun extractFacebookUserId(): String? {
        try {
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie("https://m.facebook.com")
                ?: cookieManager.getCookie("https://www.facebook.com")
                ?: cookieManager.getCookie("https://facebook.com")

            if (!cookies.isNullOrBlank()) {
                val cookiePairs = cookies.split(";")
                for (pair in cookiePairs) {
                    val trimmed = pair.trim()
                    if (trimmed.startsWith("c_user=")) {
                        val uid = trimmed.substringAfter("c_user=").trim()
                        if (uid.isNotEmpty() && uid.all { it.isDigit() }) {
                            return uid
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Executes the Copy User ID action.
     * Strictly copies ONLY if a Facebook account is actually logged in.
     */
    fun copyFacebookUserId() {
        val uid = extractFacebookUserId()
        if (uid != null) {
            copyTextToClipboard("Facebook UID", uid)
            triggerHapticFeedback()
            Toast.makeText(
                context,
                "✅ Facebook UID copied: $uid\n(Now tap any column bubble to insert!)",
                Toast.LENGTH_LONG
            ).show()
            uidBadgeText.text = "UID: $uid"
            uidBadgeText.setTextColor(Color.parseColor("#10B981"))
        } else {
            // Strictly DO NOT copy any URL or link if not logged in!
            Toast.makeText(
                context,
                "⚠️ Facebook account is not logged in! Please log in first.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkAndDisplayDetectedUid() {
        val uid = extractFacebookUserId()
        if (uid != null) {
            uidBadgeText.text = "UID: $uid"
            uidBadgeText.setTextColor(Color.parseColor("#10B981")) // Green
        } else {
            uidBadgeText.text = "UID: Not Logged In"
            uidBadgeText.setTextColor(Color.parseColor("#FBBF24")) // Amber
        }
    }

    /**
     * Copies the complete active Facebook cookies (containing c_user, xs, fr, datr, sb, etc.) to clipboard.
     * Strictly copies ONLY if a Facebook account is actually logged in.
     */
    fun copyFacebookCookies() {
        val uid = extractFacebookUserId()
        if (uid == null) {
            // Strictly DO NOT copy anything if not logged in!
            Toast.makeText(
                context,
                "⚠️ Facebook account is not logged in! Please log in first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie("https://m.facebook.com")
                ?: cookieManager.getCookie("https://www.facebook.com")
                ?: cookieManager.getCookie("https://facebook.com")

            if (!cookies.isNullOrBlank() && cookies.contains("c_user=")) {
                copyTextToClipboard("Facebook Cookies", cookies)
                triggerHapticFeedback()
                Toast.makeText(
                    context,
                    "✅ Facebook Cookies Copied to Clipboard!\n(Now tap column bubble to paste)",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "⚠️ Facebook account is not logged in! Please log in first.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error reading cookies: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Completely wipes all cookies, session storage, cache, and history.
     * Ensures clean slate for next account login with zero cross-contamination.
     */
    fun clearAllWebDataAndCookies() {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.removeSessionCookies(null)
            cookieManager.flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
            webView?.clearCache(true)
            webView?.clearFormData()
            webView?.clearHistory()
            webView?.clearSslPreferences()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun requestInputFocus() {
        val p = windowParams ?: return
        val v = rootView ?: return
        if ((p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            try {
                windowManager.updateViewLayout(v, p)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun releaseInputFocus() {
        val p = windowParams ?: return
        val v = rootView ?: return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(v.windowToken, 0)

        if ((p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            try {
                windowManager.updateViewLayout(v, p)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun toggleMinimize(rootContainer: FrameLayout, normalWidth: Int, normalHeight: Int) {
        val p = windowParams ?: return
        isMinimized = !isMinimized

        if (isMinimized) {
            rootContainer.removeAllViews()
            p.width = dpToPx(52)
            p.height = dpToPx(52)

            val minBubble = FrameLayout(context).apply {
                val lp = FrameLayout.LayoutParams(dpToPx(50), dpToPx(50))
                layoutParams = lp
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1877F2"))
                    setStroke(dpToPx(2f), Color.WHITE)
                }
                background = bg
                elevation = dpToPx(6).toFloat()
            }

            val iconText = TextView(context).apply {
                text = "🔵"
                textSize = 20f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            minBubble.addView(iconText)

            minBubble.setOnClickListener {
                toggleMinimize(rootContainer, normalWidth, normalHeight)
            }

            setupDragListener(minBubble, p)
            rootContainer.addView(minBubble)
        } else {
            p.width = normalWidth
            p.height = normalHeight
            rootContainer.removeAllViews()
            val restoredView = buildWindowView(p, normalWidth, normalHeight)
            rootContainer.addView(restoredView)
        }

        try {
            rootView?.let { windowManager.updateViewLayout(it, p) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener(dragView: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    releaseInputFocus()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    params.x = initialX + dx
                    params.y = initialY + dy
                    try {
                        rootView?.let { windowManager.updateViewLayout(it, params) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun createHeaderIconText(symbol: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = symbol
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            val ripple = createRippleDrawable(Color.parseColor("#384252"), dpToPx(10))
            background = ripple
            setOnClickListener { onClick() }
        }
    }

    private fun createNavIconButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5))
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            val ripple = createRippleDrawable(Color.parseColor("#3A3B3C"), dpToPx(6))
            background = ripple
            setOnClickListener { onClick() }
        }
    }

    private fun copyTextToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(35)
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    private fun createRippleDrawable(color: Int, radiusPx: Int): RippleDrawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(Color.TRANSPARENT)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(color), content, mask)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())
}
