package com.example.service

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.api.MailDataParser
import com.example.api.MailGenClient
import com.example.api.MailInboxResult
import com.example.api.MailMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compact, movable floating popup window for MailGen /api/get-inbox.
 * Non-blocking: Allows interacting with underlying phone screen seamlessly!
 */
class FloatingInboxWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onCloseListener: () -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var windowParams: WindowManager.LayoutParams? = null
    private var rootView: View? = null

    private var currentMode = "oauth"
    private var latestExtractedEmail: String? = null

    // UI references
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
        val windowWidth = (dpToPx(320)).coerceAtMost((screenWidth * 0.90f).toInt())

        // FLAG_NOT_TOUCH_MODAL allows touches outside the window to be sent to windows behind it!
        // We do NOT use FLAG_NOT_FOCUSABLE so the EditText can accept input, but outside touches pass through.
        val params = WindowManager.LayoutParams(
            windowWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
            y = dpToPx(120)
        }
        windowParams = params

        val view = buildWindowView(params)
        rootView = view

        try {
            windowManager.addView(view, params)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Cannot open inbox window: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun dismiss() {
        if (!isShowing) return
        rootView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        rootView = null
        isShowing = false
        onCloseListener()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildWindowView(params: WindowManager.LayoutParams): View {
        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL

            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#1E222D")) // Premium dark navy slate
                setStroke(dpToPx(1.5f), Color.parseColor("#384252"))
            }
            background = cardBg
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(12))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. Draggable Header Bar
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Drag handle pill icon
        val dragHandle = TextView(context).apply {
            text = "::: "
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(2), dpToPx(2), dpToPx(6), dpToPx(2))
        }

        val titleText = TextView(context).apply {
            text = "✉️ MailGen Inbox"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeButton = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
            val ripple = createRippleDrawable(Color.parseColor("#334155"), dpToPx(12))
            background = ripple
            setOnClickListener { dismiss() }
        }

        headerLayout.addView(dragHandle)
        headerLayout.addView(titleText)
        headerLayout.addView(closeButton)

        // Make Header Draggable anywhere across the phone screen!
        setupDragListener(headerLayout, params)
        rootCard.addView(headerLayout)

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
                setColor(Color.parseColor("#0F172A"))
                setStroke(dpToPx(1f), Color.parseColor("#334155"))
            }
            background = editBg
            isSingleLine = false
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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
                setColor(Color.parseColor("#2563EB")) // Bright Royal Blue
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dpToPx(6)
            }
            setOnClickListener {
                pasteFromClipboard()
            }
        }

        inputRow.addView(inputEditText)
        inputRow.addView(pasteButton)
        rootCard.addView(inputRow)

        // 3. Extracted Email Display & 1-Tap Copy Box (Initially Hidden until email detected)
        emailResultContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            val pillBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#064E3B")) // Emerald dark
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
                setColor(Color.parseColor("#10B981")) // Emerald bright
            }
            background = copyBg
            setOnClickListener {
                latestExtractedEmail?.let { email ->
                    copyTextToClipboard("Email Address", email)
                    Toast.makeText(context, "Email Copied: $email", Toast.LENGTH_SHORT).show()
                }
            }
        }

        emailResultContainer.addView(emailTextView)
        emailResultContainer.addView(copyEmailButton)
        rootCard.addView(emailResultContainer)

        // TextWatcher to automatically parse and extract email
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
                setColor(Color.parseColor("#334155"))
            }
            background = chipBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(6)
            }
            setOnClickListener {
                // Cycle modes: oauth -> graph -> roundcube
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
                colors = intArrayOf(Color.parseColor("#6366F1"), Color.parseColor("#4F46E5"))
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
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

        // 6. Status Text (Error or empty notice)
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

        // 7. Results Scroll View (Compact Max Height ~ 200dp)
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
            emailResultContainer.visibility = View.VISIBLE

            // Auto adapt mode if user pasted roundcube format
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
        val inputData = inputEditText.text.toString().trim()
        if (inputData.isEmpty()) {
            Toast.makeText(context, "Please paste or enter Hotmail data first", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading state
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
            statusTextView.setTextColor(Color.parseColor("#F87171")) // Red
            val errorMsg = result.error ?: "Unable to fetch inbox"
            statusTextView.text = "❌ Error: $errorMsg"
            resultsScrollView.visibility = View.GONE
            return
        }

        val messages = result.messages
        if (messages.isEmpty()) {
            statusTextView.visibility = View.VISIBLE
            statusTextView.setTextColor(Color.parseColor("#FBBF24")) // Yellow
            statusTextView.text = "Inbox checked: 0 messages found."
            resultsScrollView.visibility = View.GONE
            return
        }

        statusTextView.visibility = View.VISIBLE
        statusTextView.setTextColor(Color.parseColor("#34D399")) // Green
        statusTextView.text = "✅ Found ${messages.size} message(s)"
        resultsScrollView.visibility = View.VISIBLE

        // Check if ANY message has an OTP or resolved code to feature prominently at the top
        val firstMessageWithCode = messages.firstOrNull { it.getResolvedCode() != null }
        if (firstMessageWithCode != null) {
            val otpCode = firstMessageWithCode.getResolvedCode()!!
            val otpCard = createOtpHighlightCard(otpCode, firstMessageWithCode.from)
            resultsContainer.addView(otpCard)
        }

        // Add message cards
        for (msg in messages) {
            val msgCard = createMessageCard(msg)
            resultsContainer.addView(msgCard)
        }
    }

    private fun createOtpHighlightCard(otpCode: String, sender: String): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10).toFloat()
                colors = intArrayOf(Color.parseColor("#1E1B4B"), Color.parseColor("#312E81"))
                setStroke(dpToPx(1.5f), Color.parseColor("#6366F1"))
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
            setTextColor(Color.parseColor("#A5B4FC"))
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val senderLabel = TextView(context).apply {
            text = if (sender.isNotBlank()) "from $sender" else ""
            setTextColor(Color.parseColor("#C7D2FE"))
            textSize = 10f
        }

        headerRow.addView(otpLabel)
        headerRow.addView(senderLabel)
        card.addView(headerRow)

        val codeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(4), 0, 0)
        }

        val codeText = TextView(context).apply {
            text = otpCode
            setTextColor(Color.parseColor("#34D399")) // Bright green
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val copyCodeBtn = TextView(context).apply {
            text = "📋 COPY OTP"
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#10B981"))
            }
            background = btnBg
            setOnClickListener {
                copyTextToClipboard("OTP Code", otpCode)
                Toast.makeText(context, "OTP Copied: $otpCode", Toast.LENGTH_SHORT).show()
            }
        }

        codeRow.addView(codeText)
        codeRow.addView(copyCodeBtn)
        card.addView(codeRow)

        return card
    }

    private fun createMessageCard(msg: MailMessage): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dpToPx(1f), Color.parseColor("#334155"))
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

        // Header: From + Date
        val metaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val fromText = TextView(context).apply {
            text = if (msg.from.isNotBlank()) msg.from else "Unknown Sender"
            setTextColor(Color.parseColor("#93C5FD"))
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

        // Subject
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

        // Code if distinct
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
                setOnClickListener {
                    copyTextToClipboard("Code", code)
                    Toast.makeText(context, "Copied: $code", Toast.LENGTH_SHORT).show()
                }
            }
            codeRow.addView(codeBadge)
            codeRow.addView(copyBtn)
            card.addView(codeRow)
        }

        // Preview snippet
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
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
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
