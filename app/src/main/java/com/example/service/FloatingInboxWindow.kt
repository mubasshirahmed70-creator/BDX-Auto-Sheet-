package com.example.service

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.R
import com.example.api.MailDataParser
import com.example.api.MailGenClient
import com.example.api.MailInboxResult
import com.example.api.MailMessage
import com.example.data.repository.HotmailItem
import com.example.data.repository.HotmailStockRepository
import com.example.data.repository.HotmailStockState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Compact, movable floating popup window for MailGen /api/get-inbox with:
 * - Full Minimize (—) & Maximize (1-tap on floating Mail mini-bubble) support.
 * - Liquid-smooth spring animations, bouncy button physics, and staggered message wave reveals.
 */
class FloatingInboxWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onCloseListener: () -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var windowParams: WindowManager.LayoutParams? = null
    private var rootContainer: FrameLayout? = null
    private var mainCardView: LinearLayout? = null
    private var minimizedBubbleView: FrameLayout? = null
    private var breathingAnimator: ObjectAnimator? = null
    private var observerJob: Job? = null

    private var normalWindowWidth: Int = 0
    private var isMinimized: Boolean = false

    private val hotmailStockRepo = HotmailStockRepository.getInstance(context)
    private var activeHotmailItem: HotmailItem? = null

    private var currentMode = "oauth"
    private var latestExtractedEmail: String? = null

    // UI references
    private lateinit var queueStatusTextView: TextView
    private lateinit var activeHotmailTextView: TextView
    private lateinit var copyFullHotmailButton: TextView
    private lateinit var copyMailOnlyButton: TextView
    private lateinit var inputEditText: EditText
    private lateinit var emailResultContainer: LinearLayout
    private lateinit var emailTextView: TextView
    private lateinit var copyEmailButton: TextView
    private lateinit var modeChipButton: TextView
    private lateinit var getInboxButton: TextView
    private lateinit var loadingBar: ProgressBar
    private lateinit var statusTextView: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var resultsScrollView: ScrollView

    var isShowing: Boolean = false
        private set

    fun isCurrentlyMinimized(): Boolean = isShowing && isMinimized

    fun maximizeIfMinimized() {
        if (isShowing && isMinimized) {
            setMinimizedState(false)
        }
    }

    fun show() {
        if (isShowing) {
            if (isMinimized) {
                setMinimizedState(false)
            }
            return
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val windowWidth = (dpToPx(316)).coerceAtMost((screenWidth * 0.90f).toInt())
        normalWindowWidth = windowWidth
        isMinimized = false

        val params = WindowManager.LayoutParams(
            windowWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - windowWidth - dpToPx(12)).coerceAtLeast(0)
            y = dpToPx(120)
        }
        windowParams = params

        val view = buildRootContainerView(params)
        rootContainer = view

        try {
            windowManager.addView(view, params)
            isShowing = true

            mainCardView?.let { FloatingWindowAnimator.animateWindowOpen(it) }

            observerJob?.cancel()
            observerJob = scope.launch {
                hotmailStockRepo.stockState.collect { state ->
                    updateQueueHeader(state)
                }
            }

            val initial = hotmailStockRepo.getCurrentOrFirstAvailable()
            if (initial != null) {
                loadHotmailItem(initial)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Cannot open inbox window: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Smoothly morphs between Minimized floating Mail bubble and Maximized MailGen Inbox window
     * WITHOUT losing loaded Hotmail data or inbox messages!
     */
    private fun setMinimizedState(minimize: Boolean) {
        val params = windowParams ?: return
        val container = rootContainer ?: return
        val card = mainCardView ?: return
        val bubble = minimizedBubbleView ?: return

        if (isMinimized == minimize) return
        isMinimized = minimize

        if (minimize) {
            releaseInputFocus()
            card.animate()
                .scaleX(0.35f)
                .scaleY(0.35f)
                .alpha(0f)
                .setDuration(160L)
                .withEndAction {
                    card.visibility = View.GONE
                    params.width = WindowManager.LayoutParams.WRAP_CONTENT
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (_: Exception) {
                    }
                    bubble.visibility = View.VISIBLE
                    bubble.scaleX = 0.3f
                    bubble.scaleY = 0.3f
                    bubble.alpha = 0f
                    bubble.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(260L)
                        .setInterpolator(OvershootInterpolator(1.8f))
                        .withEndAction {
                            breathingAnimator?.cancel()
                            breathingAnimator = FloatingWindowAnimator.startBreathingPulse(bubble)
                        }
                        .start()
                }
                .start()
        } else {
            breathingAnimator?.cancel()
            breathingAnimator = null
            bubble.visibility = View.GONE
            params.width = normalWindowWidth
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            try {
                windowManager.updateViewLayout(container, params)
            } catch (_: Exception) {
            }
            card.visibility = View.VISIBLE
            card.scaleX = 0.55f
            card.scaleY = 0.55f
            card.alpha = 0f
            card.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(290L)
                .setInterpolator(OvershootInterpolator(1.16f))
                .start()
        }
    }

    fun requestInputFocus() {
        val p = windowParams ?: return
        val v = rootContainer ?: return
        if ((p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            try {
                windowManager.updateViewLayout(v, p)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        inputEditText.post {
            inputEditText.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(inputEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun releaseInputFocus() {
        val p = windowParams ?: return
        val v = rootContainer ?: return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(inputEditText.windowToken, 0)
        inputEditText.clearFocus()

        if ((p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            try {
                windowManager.updateViewLayout(v, p)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismiss() {
        if (!isShowing) return
        breathingAnimator?.cancel()
        breathingAnimator = null
        observerJob?.cancel()
        observerJob = null
        releaseInputFocus()
        rootContainer?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        rootContainer = null
        mainCardView = null
        minimizedBubbleView = null
        isShowing = false
        isMinimized = false
        onCloseListener()
    }

    private fun buildRootContainerView(params: WindowManager.LayoutParams): FrameLayout {
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val card = buildWindowView(params)
        mainCardView = card
        container.addView(card)

        val minBubble = FloatingWindowAnimator.createMinimizedWindowBubble(
            context = context,
            windowManager = windowManager,
            rootViewProvider = { rootContainer },
            params = params,
            iconResId = R.drawable.ic_mail_logo,
            accentColorHex = "#EF4444",
            tintIconWhite = true,
            onTapMaximize = {
                setMinimizedState(false)
            }
        ).apply {
            visibility = View.GONE
        }
        minimizedBubbleView = minBubble
        container.addView(minBubble)

        return container
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildWindowView(params: WindowManager.LayoutParams): LinearLayout {
        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL

            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#0D0D11")) // Matte Obsidian Black
                setStroke(dpToPx(1.5f), Color.parseColor("#DC2626")) // Crisp Red Border
            }
            background = cardBg
            elevation = dpToPx(10).toFloat()
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(12))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE || event.action == MotionEvent.ACTION_DOWN) {
                    releaseInputFocus()
                }
                false
            }
        }

        // 1. Draggable Header Bar with Minimize (—) and Close (✕)
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val dragHandle = TextView(context).apply {
            text = "::: "
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(2), dpToPx(2), dpToPx(6), dpToPx(2))
        }

        val mailLogoIv = android.widget.ImageView(context).apply {
            setImageResource(R.drawable.ic_mail_logo)
            setColorFilter(Color.parseColor("#EF4444"))
            val logoSize = dpToPx(18)
            val lp = LinearLayout.LayoutParams(logoSize, logoSize).apply {
                marginEnd = dpToPx(6)
            }
            layoutParams = lp
        }

        val titleText = TextView(context).apply {
            text = "MailGen Inbox"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Minimize Button (—)
        val minimizeButton = TextView(context).apply {
            text = "—"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val s = dpToPx(24)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginEnd = dpToPx(5)
            }
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#181820"))
                setStroke(dpToPx(1f), Color.parseColor("#EF4444"))
            }
            background = bg
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                setMinimizedState(true)
            }
        }

        val closeButton = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val s = dpToPx(24)
            layoutParams = LinearLayout.LayoutParams(s, s)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#DC2626"))
            }
            background = bg
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener { dismiss() }
        }

        headerLayout.addView(dragHandle)
        headerLayout.addView(mailLogoIv)
        headerLayout.addView(titleText)
        headerLayout.addView(minimizeButton)
        headerLayout.addView(closeButton)

        setupDragListener(headerLayout, params)
        rootCard.addView(headerLayout)

        // Hotmail Stock Queue Controller Section
        val queueSection = buildHotmailQueueSection()
        rootCard.addView(queueSection)

        // 2. Input Row (EditText + Paste Button)
        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        inputEditText = EditText(context).apply {
            hint = "Paste Hotmail: email|pass|token..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            val editBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#14141B"))
                setStroke(dpToPx(1f), Color.parseColor("#2E2E3A"))
            }
            background = editBg
            isSingleLine = false
            maxLines = 2
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    requestInputFocus()
                }
                false
            }

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    releaseInputFocus()
                    true
                } else false
            }
        }

        val pasteButton = TextView(context).apply {
            text = "📋 Paste"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#181820"))
                setStroke(dpToPx(1.2f), Color.parseColor("#EF4444"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dpToPx(6)
            }
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                FloatingWindowAnimator.animatePopPulse(this)
                pasteFromClipboard()
                releaseInputFocus()
            }
        }

        inputRow.addView(inputEditText)
        inputRow.addView(pasteButton)
        rootCard.addView(inputRow)

        // 3. Extracted Email Display & 1-Tap Copy Box
        emailResultContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            val pillBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#064E3B"))
                setStroke(dpToPx(1f), Color.parseColor("#059669"))
            }
            background = pillBg
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }

        emailTextView = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#A7F3D0"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        copyEmailButton = TextView(context).apply {
            text = "📋 COPY EMAIL"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            val copyBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#10B981"))
            }
            background = copyBg
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                FloatingWindowAnimator.animatePopPulse(this)
                latestExtractedEmail?.let { email ->
                    copyTextToClipboard("Email Address", email)
                    Toast.makeText(context, "Email Copied: $email", Toast.LENGTH_SHORT).show()
                }
            }
        }

        emailResultContainer.addView(emailTextView)
        emailResultContainer.addView(copyEmailButton)
        rootCard.addView(emailResultContainer)

        inputEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString().orEmpty()
                onInputChanged(text)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 4. Action Row: Mode Switcher + "GET INBOX" Button
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        modeChipButton = TextView(context).apply {
            text = "Mode: $currentMode ▾"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            val chipBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#1E1E28"))
                setStroke(dpToPx(1f), Color.parseColor("#3F3F46"))
            }
            background = chipBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(6)
            }
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                currentMode = when (currentMode) {
                    "oauth" -> "graph"
                    "graph" -> "roundcube"
                    else -> "oauth"
                }
                text = "Mode: $currentMode ▾"
            }
        }

        getInboxButton = TextView(context).apply {
            text = "⚡ GET INBOX"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#181820"))
                setStroke(dpToPx(1.5f), Color.parseColor("#EF4444"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                FloatingWindowAnimator.animatePopPulse(this)
                executeGetInbox()
            }
        }

        actionRow.addView(modeChipButton)
        actionRow.addView(getInboxButton)
        rootCard.addView(actionRow)

        // 5. Loading Bar
        loadingBar = ProgressBar(context).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(24),
                dpToPx(24)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dpToPx(4)
                bottomMargin = dpToPx(4)
            }
        }
        rootCard.addView(loadingBar)

        // 6. Status Text
        statusTextView = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11.5f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootCard.addView(statusTextView)

        // 7. Results Scroll View
        resultsScrollView = ScrollView(context).apply {
            visibility = View.GONE
            val maxH = dpToPx(200)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                maxH
            ).apply {
                topMargin = dpToPx(4)
            }
        }

        resultsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        resultsScrollView.addView(resultsContainer)
        rootCard.addView(resultsScrollView)

        return rootCard
    }

    private fun onInputChanged(rawText: String) {
        val extractedEmail = MailDataParser.extractEmail(rawText)
        latestExtractedEmail = extractedEmail

        if (!extractedEmail.isNullOrBlank()) {
            emailTextView.text = extractedEmail
            if (emailResultContainer.visibility != View.VISIBLE) {
                emailResultContainer.visibility = View.VISIBLE
                FloatingWindowAnimator.animateStepTransition(emailResultContainer)
            }

            val suggestedMode = MailDataParser.detectMode(rawText)
            if (suggestedMode != currentMode) {
                currentMode = suggestedMode
                modeChipButton.text = "Mode: $currentMode ▾"
            }
        } else {
            emailResultContainer.visibility = View.GONE
        }
    }

    private fun pasteFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).coerceToText(context).toString().trim()
                if (pastedText.isNotEmpty()) {
                    inputEditText.setText(pastedText)
                    inputEditText.setSelection(pastedText.length)
                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Paste failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeGetInbox() {
        releaseInputFocus()
        val inputData = inputEditText.text.toString().trim()
        if (inputData.isEmpty()) {
            Toast.makeText(context, "Please paste or enter Hotmail data first", Toast.LENGTH_SHORT).show()
            return
        }

        loadingBar.visibility = View.VISIBLE
        statusTextView.visibility = View.VISIBLE
        statusTextView.setTextColor(Color.parseColor("#93C5FD"))
        statusTextView.text = "Fetching inbox from MailGen..."
        getInboxButton.isEnabled = false
        resultsContainer.removeAllViews()
        resultsScrollView.visibility = View.GONE

        scope.launch {
            val result = MailGenClient.getInbox(inputData, currentMode)
            loadingBar.visibility = View.GONE
            getInboxButton.isEnabled = true

            displayInboxResult(result)
        }
    }

    private fun displayInboxResult(result: MailInboxResult) {
        resultsContainer.removeAllViews()

        if (!result.isSuccess) {
            statusTextView.visibility = View.VISIBLE
            statusTextView.setTextColor(Color.parseColor("#F87171"))
            val errorMsg = result.error ?: "Unable to fetch inbox"
            statusTextView.text = "❌ Error: $errorMsg"
            resultsScrollView.visibility = View.GONE
            return
        }

        val messages = result.messages
        if (messages.isEmpty()) {
            statusTextView.visibility = View.VISIBLE
            statusTextView.setTextColor(Color.parseColor("#FBBF24"))
            statusTextView.text = "Inbox checked: 0 messages found."
            resultsScrollView.visibility = View.GONE
            return
        }

        statusTextView.visibility = View.VISIBLE
        statusTextView.setTextColor(Color.parseColor("#34D399"))
        statusTextView.text = "✅ Found ${messages.size} message(s)"
        resultsScrollView.visibility = View.VISIBLE

        var itemIdx = 0
        val firstMessageWithCode = messages.firstOrNull { it.getResolvedCode() != null }
        if (firstMessageWithCode != null) {
            val otpCode = firstMessageWithCode.getResolvedCode()!!
            val currentMailTarget = activeHotmailItem?.id ?: latestExtractedEmail.orEmpty()
            if (currentMailTarget.isNotBlank()) {
                hotmailStockRepo.markMailAsUsed(currentMailTarget)
            }

            val otpCard = createOtpHighlightCard(otpCode, firstMessageWithCode.from)
            resultsContainer.addView(otpCard)
            FloatingWindowAnimator.animateListItemWave(otpCard, itemIdx++)
        }

        for (msg in messages) {
            val msgCard = createMessageCard(msg)
            resultsContainer.addView(msgCard)
            FloatingWindowAnimator.animateListItemWave(msgCard, itemIdx++)
        }
    }

    private fun createOtpHighlightCard(otpCode: String, sender: String): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10).toFloat()
                setColor(Color.parseColor("#14141B"))
                setStroke(dpToPx(1.5f), Color.parseColor("#EF4444"))
            }
            background = bg
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val otpLabel = TextView(context).apply {
            text = "🔑 VERIFICATION CODE"
            setTextColor(Color.parseColor("#FCA5A5"))
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val senderLabel = TextView(context).apply {
            text = if (sender.isNotBlank()) "from $sender" else "✓ Used/বাতিল"
            setTextColor(Color.parseColor("#34D399"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }

        headerRow.addView(otpLabel)
        headerRow.addView(senderLabel)
        card.addView(headerRow)

        val codeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(4), 0, dpToPx(4))
        }

        val codeText = TextView(context).apply {
            text = otpCode
            setTextColor(Color.parseColor("#34D399"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        codeRow.addView(codeText)
        card.addView(codeRow)

        val otpButtonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(2), 0, 0)
        }

        val copyCodeBtn = TextView(context).apply {
            text = "📋 COPY OTP"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#059669"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dpToPx(4)
            }
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                FloatingWindowAnimator.animatePopPulse(this)
                copyTextToClipboard("OTP Code", otpCode)
                val currentMailTarget = activeHotmailItem?.id ?: latestExtractedEmail.orEmpty()
                if (currentMailTarget.isNotBlank()) {
                    hotmailStockRepo.markMailAsUsed(currentMailTarget)
                }
                Toast.makeText(context, "OTP Copied ($otpCode) & Mail marked as Used!", Toast.LENGTH_SHORT).show()
            }
        }

        val copyAndNextBtn = TextView(context).apply {
            text = "⚡ COPY & NEXT ⏭️"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#181820"))
                setStroke(dpToPx(1.2f), Color.parseColor("#EF4444"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(4)
            }
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                FloatingWindowAnimator.animatePopPulse(this)
                copyTextToClipboard("OTP Code", otpCode)
                val next = hotmailStockRepo.markCurrentAsUsedAndGetNext()
                if (next != null) {
                    loadHotmailItem(next)
                    resultsContainer.removeAllViews()
                    resultsScrollView.visibility = View.GONE
                    statusTextView.visibility = View.VISIBLE
                    statusTextView.setTextColor(Color.parseColor("#34D399"))
                    statusTextView.text = "OTP Copied! Next mail loaded: ${next.email}"
                    Toast.makeText(context, "OTP Copied! Loaded next: ${next.email}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "OTP Copied! All hotmails in stock are used.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        otpButtonsRow.addView(copyCodeBtn)
        otpButtonsRow.addView(copyAndNextBtn)
        card.addView(otpButtonsRow)

        return card
    }

    private fun buildHotmailQueueSection(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10).toFloat()
                setColor(Color.parseColor("#13131A"))
                setStroke(dpToPx(1.2f), Color.parseColor("#DC2626"))
            }
            background = bg
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        queueStatusTextView = TextView(context).apply {
            text = "📦 Hotmail Queue: 0 Available"
            setTextColor(Color.parseColor("#FCA5A5"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nextBtn = TextView(context).apply {
            text = "⏭️ Next"
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#181820"))
                setStroke(dpToPx(1f), Color.parseColor("#EF4444"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(4)
            }
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                FloatingWindowAnimator.animatePopPulse(this)
                val next = hotmailStockRepo.getAndAdvanceNextMail()
                if (next != null) {
                    loadHotmailItem(next)
                    Toast.makeText(context, "Loaded: ${next.email}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No unused hotmails! Upload .txt in app.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val discardBtn = TextView(context).apply {
            text = "⏩ Done/Skip"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(7), dpToPx(4), dpToPx(7), dpToPx(4))
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#272732"))
            }
            background = btnBg
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                FloatingWindowAnimator.animatePopPulse(this)
                val next = hotmailStockRepo.markCurrentAsUsedAndGetNext()
                if (next != null) {
                    loadHotmailItem(next)
                    Toast.makeText(context, "Marked as Used & loaded next: ${next.email}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "All hotmails in stock marked as used!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        headerRow.addView(queueStatusTextView)
        headerRow.addView(nextBtn)
        headerRow.addView(discardBtn)
        container.addView(headerRow)

        activeHotmailTextView = TextView(context).apply {
            text = "Tap ⏭️ Next or upload .txt in app"
            setTextColor(Color.parseColor("#6EE7B7"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            isSingleLine = true
            setPadding(0, dpToPx(4), 0, dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(activeHotmailTextView)

        val copyButtonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        copyFullHotmailButton = TextView(context).apply {
            text = "📋 COPY FULL DATA"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#181820"))
                setStroke(dpToPx(1.2f), Color.parseColor("#EF4444"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dpToPx(4)
            }
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                FloatingWindowAnimator.animatePopPulse(this)
                val fullData = activeHotmailItem?.rawData ?: inputEditText.text.toString().trim()
                if (fullData.isNotEmpty()) {
                    copyTextToClipboard("Hotmail Full Data", fullData)
                    Toast.makeText(context, "Copied Full Hotmail Data", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No hotmail data to copy", Toast.LENGTH_SHORT).show()
                }
            }
        }

        copyMailOnlyButton = TextView(context).apply {
            text = "📧 COPY MAIL ONLY"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#181820"))
                setStroke(dpToPx(1.2f), Color.parseColor("#EF4444"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(4)
            }
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                FloatingWindowAnimator.animatePopPulse(this)
                val emailOnly = activeHotmailItem?.email ?: latestExtractedEmail
                if (!emailOnly.isNullOrBlank()) {
                    copyTextToClipboard("Email Address", emailOnly)
                    Toast.makeText(context, "Copied Email: $emailOnly", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No email address found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        copyButtonRow.addView(copyFullHotmailButton)
        copyButtonRow.addView(copyMailOnlyButton)
        container.addView(copyButtonRow)

        return container
    }

    private fun updateQueueHeader(state: HotmailStockState) {
        if (!::queueStatusTextView.isInitialized) return
        val available = state.availableCount
        val total = state.totalCount
        if (total == 0) {
            queueStatusTextView.text = "📦 Stock Empty (Upload .txt in app)"
            activeHotmailTextView.text = "No Hotmail loaded"
        } else {
            queueStatusTextView.text = "📦 Queue: $available Left / $total Total"
            val current = state.currentLoadedItem
            if (current != null && !current.isUsed) {
                activeHotmailTextView.text = "📧 ${current.email}"
            } else if (available > 0) {
                activeHotmailTextView.text = "Tap ⏭️ Next to load next mail"
            } else {
                activeHotmailTextView.text = "✅ All $total Hotmails used/বাতিল"
            }
        }
    }

    private fun loadHotmailItem(item: HotmailItem) {
        activeHotmailItem = item
        if (::activeHotmailTextView.isInitialized) {
            activeHotmailTextView.text = "📧 ${item.email}"
            FloatingWindowAnimator.animatePopPulse(activeHotmailTextView)
        }
        if (::inputEditText.isInitialized) {
            inputEditText.setText(item.rawData)
            inputEditText.setSelection(item.rawData.length)
        }
        onInputChanged(item.rawData)
    }

    private fun createMessageCard(msg: MailMessage): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#14141C"))
                setStroke(dpToPx(1f), Color.parseColor("#2E2E3A"))
            }
            background = bg
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }
        }

        val metaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val fromText = TextView(context).apply {
            text = if (msg.from.isNotBlank()) msg.from else "Unknown Sender"
            setTextColor(Color.parseColor("#FCA5A5"))
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val dateText = TextView(context).apply {
            text = msg.date
            setTextColor(Color.parseColor("#64748B"))
            textSize = 9.5f
        }

        metaRow.addView(fromText)
        metaRow.addView(dateText)
        card.addView(metaRow)

        if (msg.subject.isNotBlank()) {
            val subjectText = TextView(context).apply {
                text = msg.subject
                setTextColor(Color.WHITE)
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dpToPx(2), 0, dpToPx(2))
            }
            card.addView(subjectText)
        }

        val code = msg.getResolvedCode()
        if (code != null) {
            val codeRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2), 0, dpToPx(2))
            }
            val codeBadge = TextView(context).apply {
                text = "Code: $code"
                setTextColor(Color.parseColor("#34D399"))
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val copyBtn = TextView(context).apply {
                text = "Copy"
                setTextColor(Color.WHITE)
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
                val b = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(4).toFloat()
                    setColor(Color.parseColor("#059669"))
                }
                background = b
                FloatingWindowAnimator.applyLiquidTouch(this)
                setOnClickListener {
                    FloatingWindowAnimator.animatePopPulse(this)
                    copyTextToClipboard("Code", code)
                    Toast.makeText(context, "Copied: $code", Toast.LENGTH_SHORT).show()
                }
            }
            codeRow.addView(codeBadge)
            codeRow.addView(copyBtn)
            card.addView(codeRow)
        }

        val preview = msg.message
        if (preview.isNotBlank()) {
            val previewText = TextView(context).apply {
                text = preview
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 10.5f
                maxLines = 3
            }
            card.addView(previewText)
        }

        return card
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener(dragView: View, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        dragView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    releaseInputFocus()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            rootContainer?.let { windowManager.updateViewLayout(it, params) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun copyTextToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
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

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
