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
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.example.data.repository.HotmailItem
import com.example.data.repository.HotmailStockRepository
import com.example.data.repository.HotmailStockState
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
    private var observerJob: Job? = null

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

        // SMART DYNAMIC FOCUS:
        // Default to FLAG_NOT_FOCUSABLE so that keyboard typing in other apps (Chrome, Notes, WhatsApp)
        // is 100% unrestricted and never blocked!
        // Focus is only requested dynamically when the user explicitly taps this input box.
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
            // Start on the right side of the screen
            x = (screenWidth - windowWidth - dpToPx(12)).coerceAtLeast(0)
            y = dpToPx(120)
        }
        windowParams = params

        val view = buildWindowView(params)
        rootView = view

        try {
            windowManager.addView(view, params)
            isShowing = true

            // Automatically listen to stock changes
            observerJob?.cancel()
            observerJob = scope.launch {
                hotmailStockRepo.stockState.collect { state ->
                    updateQueueHeader(state)
                }
            }

            // Automatically load current or first available hotmail from stock
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
     * Dynamically acquires keyboard focus only when user taps the input box.
     */
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
        inputEditText.post {
            inputEditText.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(inputEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * Releases keyboard focus back to background apps immediately.
     */
    fun releaseInputFocus() {
        val p = windowParams ?: return
        val v = rootView ?: return
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
        observerJob?.cancel()
        observerJob = null
        releaseInputFocus()
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

            // When user taps anywhere on background or outside, immediately release focus to other apps
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE || event.action == MotionEvent.ACTION_DOWN) {
                    releaseInputFocus()
                }
                false
            }
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

        // Crisp vector mail icon
        val mailLogoIv = android.widget.ImageView(context).apply {
            setImageResource(com.example.R.drawable.ic_mail_logo)
            setColorFilter(Color.parseColor("#38BDF8")) // Crisp Electric Sky Blue
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
                setColor(Color.parseColor("#0F172A"))
                setStroke(dpToPx(1f), Color.parseColor("#334155"))
            }
            background = editBg
            isSingleLine = false
            maxLines = 2
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            // When tapped, request keyboard focus dynamically
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    requestInputFocus()
                }
                false
            }

            // When "Done" is pressed on keyboard, immediately release focus back to other apps
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
                // Keep focus released so soft keyboard doesn't open unnecessarily
                releaseInputFocus()
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
        releaseInputFocus()
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

            // Auto-discard requirement: "যে হটমেইলগুলোতে ওটিপি এসে যাবে ওইগুলা আর ব্যবহার হবে না ওইগুলা বাতিল হয়ে যাবে।"
            val currentMailTarget = activeHotmailItem?.id ?: latestExtractedEmail.orEmpty()
            if (currentMailTarget.isNotBlank()) {
                hotmailStockRepo.markMailAsUsed(currentMailTarget)
            }

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
            text = if (sender.isNotBlank()) "from $sender" else "✓ Used/বাতিল"
            setTextColor(Color.parseColor("#34D399")) // Bright mint green
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
            setTextColor(Color.parseColor("#34D399")) // Bright green
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        codeRow.addView(codeText)
        card.addView(codeRow)

        // OTP Action Buttons: "📋 COPY OTP" and "⚡ COPY & NEXT ⏭️"
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
                setColor(Color.parseColor("#10B981"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dpToPx(4)
            }
            setOnClickListener {
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
                colors = intArrayOf(Color.parseColor("#6366F1"), Color.parseColor("#8B5CF6"))
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(4)
            }
            setOnClickListener {
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

    /**
     * Hotmail Stock & Queue Controller Section.
     * Features:
     * - Queue status badge (e.g. "📦 Hotmail Queue: 24 Available / 50 Total")
     * - "⏭️ Next" button to load next unused hotmail
     * - "⏩ Done/Skip" button to mark current as used and load next
     * - Active hotmail email display
     * - "📋 COPY FULL DATA" button (copies full line: email|pass or email:pass)
     * - "📧 COPY MAIL ONLY" button (copies extracted email address only)
     */
    private fun buildHotmailQueueSection(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10).toFloat()
                colors = intArrayOf(Color.parseColor("#172554"), Color.parseColor("#1E1B4B")) // Deep Royal / Indigo gradient
                setStroke(dpToPx(1.2f), Color.parseColor("#3B82F6"))
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

        // Header Row: Queue badge + Next + Discard
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        queueStatusTextView = TextView(context).apply {
            text = "📦 Hotmail Queue: 0 Available"
            setTextColor(Color.parseColor("#93C5FD"))
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
                setColor(Color.parseColor("#2563EB"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(4)
            }
            setOnClickListener {
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
                setColor(Color.parseColor("#475569"))
            }
            background = btnBg
            setOnClickListener {
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

        // Active Mail text display
        activeHotmailTextView = TextView(context).apply {
            text = "Tap ⏭️ Next or upload .txt in app"
            setTextColor(Color.parseColor("#6EE7B7")) // Mint green
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

        // 2 Dedicated Copy Buttons (Requirement 8 & 9)
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
                setColor(Color.parseColor("#3B82F6"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dpToPx(4)
            }
            setOnClickListener {
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
                setColor(Color.parseColor("#10B981"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(4)
            }
            setOnClickListener {
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
