package com.example.service

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.R
import com.example.api.VoltxAllocatedNumber
import com.example.api.VoltxApiResult
import com.example.api.VoltxServiceRange
import com.example.api.VoltxSmsClient
import com.example.data.repository.VoltxSmsRepository
import com.example.data.repository.VoltxSmsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Ultra-compact, draggable 2-Step Floating Window for VOLTX SMS with:
 * - Full Minimize (—) & Maximize (tap floating mini-bubble) support with liquid spring morphing.
 * - Country Flag + Country Name displayed on the right side of every range row.
 * - Water-smooth staggered list animations and bouncy touch physics on every interactive control.
 */
class FloatingVoltxSmsWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onCloseListener: () -> Unit
) {
    private enum class WindowStep {
        SELECT_RANGE,
        NUMBERS_AND_OTP
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val repo = VoltxSmsRepository.getInstance(context)

    private var windowParams: WindowManager.LayoutParams? = null
    private var rootContainer: FrameLayout? = null
    private var mainCardView: LinearLayout? = null
    private var minimizedBubbleView: FrameLayout? = null
    private var breathingAnimator: ObjectAnimator? = null

    private var normalWindowWidth: Int = 0
    private var isMinimized: Boolean = false

    private var stateObserverJob: Job? = null
    private var autoOtpPollJob: Job? = null

    private var currentStep: WindowStep = WindowStep.SELECT_RANGE
    private var cachedServiceRanges: List<VoltxServiceRange> = emptyList()
    private var isFetchingRanges: Boolean = false
    private var isAllocatingNumbers: Boolean = false

    // UI Containers
    private lateinit var stepTitleTv: TextView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var refreshBtn: View
    private lateinit var rangeStepContainer: LinearLayout
    private lateinit var rangesListContainer: LinearLayout
    private lateinit var rangeHintTv: TextView

    private lateinit var numberStepContainer: LinearLayout
    private lateinit var selectedRangeLabelTv: TextView
    private lateinit var take2Btn: TextView
    private lateinit var take3Btn: TextView
    private lateinit var take4Btn: TextView
    private lateinit var numbersListContainer: LinearLayout
    private lateinit var statusFooterTv: TextView

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
        // Compact width (~244dp) so it takes very little screen space while showing Range + Country neatly
        val windowWidth = dpToPx(244).coerceAtMost((screenWidth * 0.82f).toInt())
        normalWindowWidth = windowWidth
        isMinimized = false

        val params = WindowManager.LayoutParams(
            windowWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - windowWidth - dpToPx(12)).coerceAtLeast(0)
            y = dpToPx(120)
        }
        windowParams = params

        // Always start on Step 1 (Ranges list) when opening from bubble
        currentStep = WindowStep.SELECT_RANGE

        val view = buildRootContainerView(params)
        rootContainer = view

        try {
            windowManager.addView(view, params)
            isShowing = true

            mainCardView?.let { FloatingWindowAnimator.animateWindowOpen(it) }

            stateObserverJob?.cancel()
            stateObserverJob = scope.launch {
                repo.state.collect { state ->
                    renderState(state)
                }
            }

            startAutoOtpPolling()
            fetchLiveRanges(silent = true)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Cannot open BDX SMS window: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun dismiss() {
        if (!isShowing) return
        breathingAnimator?.cancel()
        breathingAnimator = null
        stateObserverJob?.cancel()
        stateObserverJob = null
        autoOtpPollJob?.cancel()
        autoOtpPollJob = null
        rootContainer?.let { v ->
            try {
                windowManager.removeView(v)
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

    /**
     * Smoothly toggles between Minimized floating circle bubble and Maximized compact window
     * WITHOUT losing any loaded ranges, allocated numbers, or background OTP polling,
     * and immediately shrinks WindowManager bounds so no invisible box can block touches.
     */
    private fun setMinimizedState(minimize: Boolean) {
        val params = windowParams ?: return
        val container = rootContainer ?: return
        val card = mainCardView ?: return
        val bubble = minimizedBubbleView ?: return

        if (isMinimized == minimize) return
        isMinimized = minimize

        card.animate().cancel()
        bubble.animate().cancel()
        breathingAnimator?.cancel()
        breathingAnimator = null

        if (minimize) {
            card.visibility = View.GONE
            bubble.visibility = View.VISIBLE
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            try {
                windowManager.updateViewLayout(container, params)
            } catch (_: Exception) {
            }
            bubble.scaleX = 0.35f
            bubble.scaleY = 0.35f
            bubble.alpha = 0f
            bubble.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(250L)
                .setInterpolator(OvershootInterpolator(1.8f))
                .withEndAction {
                    if (isMinimized && isShowing) {
                        breathingAnimator?.cancel()
                        breathingAnimator = FloatingWindowAnimator.startBreathingPulse(bubble)
                    }
                }
                .start()
        } else {
            bubble.visibility = View.GONE
            card.visibility = View.VISIBLE
            params.width = normalWindowWidth
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            try {
                windowManager.updateViewLayout(container, params)
            } catch (_: Exception) {
            }
            card.scaleX = 0.65f
            card.scaleY = 0.65f
            card.alpha = 0f
            card.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(260L)
                .setInterpolator(OvershootInterpolator(1.16f))
                .start()
        }
    }

    fun refreshTheme() {
        if (!isShowing) return
        val params = windowParams ?: return
        val container = rootContainer ?: return
        val wasMin = isMinimized

        container.removeAllViews()
        val card = buildCompactWindowView(params)
        mainCardView = card
        container.addView(card)

        val minBubble = FloatingWindowAnimator.createMinimizedWindowBubble(
            context = context,
            windowManager = windowManager,
            rootViewProvider = { rootContainer },
            params = params,
            iconResId = R.drawable.ic_sms_logo,
            accentColorHex = null,
            tintIconWhite = true,
            onTapMaximize = {
                triggerHapticPulse()
                setMinimizedState(false)
            }
        )
        minimizedBubbleView = minBubble
        container.addView(minBubble)

        if (wasMin) {
            card.visibility = View.GONE
            minBubble.visibility = View.VISIBLE
        } else {
            card.visibility = View.VISIBLE
            minBubble.visibility = View.GONE
        }
        populateRangesList()
        renderState(repo.state.value)
    }

    private fun buildRootContainerView(params: WindowManager.LayoutParams): FrameLayout {
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val card = buildCompactWindowView(params)
        mainCardView = card
        container.addView(card)

        val minBubble = FloatingWindowAnimator.createMinimizedWindowBubble(
            context = context,
            windowManager = windowManager,
            rootViewProvider = { rootContainer },
            params = params,
            iconResId = R.drawable.ic_sms_logo,
            accentColorHex = null,
            tintIconWhite = true,
            onTapMaximize = {
                triggerHapticPulse()
                setMinimizedState(false)
            }
        ).apply {
            visibility = View.GONE
        }
        minimizedBubbleView = minBubble
        container.addView(minBubble)

        return container
    }

    private fun buildCompactWindowView(params: WindowManager.LayoutParams): LinearLayout {
        val theme = com.example.ui.theme.AppThemeManager.current
        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(18).toFloat()
                colors = intArrayOf(theme.headerStartInt, theme.bgInt)
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(dpToPx(1.5f), theme.accentBrightInt)
            }
            background = bg
            elevation = dpToPx(12).toFloat()
            setPadding(dpToPx(10), dpToPx(9), dpToPx(10), dpToPx(10))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. BENTO GLASS CAPSULE HEADER BAR
        val headerBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(6), dpToPx(6), dpToPx(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(theme.cardInt)
                setStroke(dpToPx(1f), theme.cardBorderInt)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }

        val iconBox = FrameLayout(context).apply {
            val s = dpToPx(24)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginEnd = dpToPx(7)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(7).toFloat()
                setColor(theme.accentSoftBgInt)
                setStroke(dpToPx(1f), theme.accentBrightInt)
            }
            val iv = ImageView(context).apply {
                setImageResource(R.drawable.ic_sms_logo)
                setColorFilter(theme.accentBrightInt)
                val pad = dpToPx(4)
                setPadding(pad, pad, pad, pad)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            addView(iv)
        }
        headerBar.addView(iconBox)

        stepTitleTv = TextView(context).apply {
            text = "BDX SMS • Ranges"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerBar.addView(stepTitleTv)

        loadingSpinner = ProgressBar(context).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dpToPx(14), dpToPx(14)).apply {
                marginEnd = dpToPx(5)
            }
        }
        headerBar.addView(loadingSpinner)

        // Refresh Vector Icon Button
        refreshBtn = createHeaderVectorIconButton(
            iconRes = R.drawable.ic_action_refresh,
            bgColor = theme.elevatedInt,
            borderColor = theme.cardBorderInt,
            iconTint = theme.accentLightTextInt,
            marginEndDp = 4
        ) {
            FloatingWindowAnimator.animatePopPulse(refreshBtn)
            if (currentStep == WindowStep.SELECT_RANGE) {
                fetchLiveRanges(silent = false)
            } else {
                checkOtpsNow(manual = true)
            }
        }
        headerBar.addView(refreshBtn)

        // Minimize Vector Icon Button
        val minimizeBtn = createHeaderVectorIconButton(
            iconRes = R.drawable.ic_window_minimize,
            bgColor = theme.elevatedInt,
            borderColor = theme.accentBrightInt,
            iconTint = Color.WHITE,
            marginEndDp = 4
        ) {
            triggerHapticPulse()
            setMinimizedState(true)
        }
        headerBar.addView(minimizeBtn)

        // Close Vector Icon Button
        val closeBtn = createHeaderVectorIconButton(
            iconRes = R.drawable.ic_window_close,
            bgColor = theme.accentInt,
            borderColor = theme.accentBrightInt,
            iconTint = Color.WHITE,
            marginEndDp = 0
        ) {
            dismiss()
        }
        headerBar.addView(closeBtn)

        setupDragListener(headerBar, params)
        rootCard.addView(headerBar)

        // =========================================================================
        // STEP 1 CONTAINER: RANGE SELECTION LIST (Facebook prioritized + Country on right)
        // =========================================================================
        rangeStepContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        rangeHintTv = TextView(context).apply {
            text = "Facebook ranges prioritized on top"
            setTextColor(theme.textSecondaryInt)
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(2), 0, dpToPx(2), dpToPx(5))
        }
        rangeStepContainer.addView(rangeHintTv)

        val rangeScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(148)
            )
        }

        rangesListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        rangeScroll.addView(rangesListContainer)
        rangeStepContainer.addView(rangeScroll)

        // Jump to Step 2 directly if numbers or range already selected
        val jumpToNumbersBar = TextView(context).apply {
            text = "View Active Numbers & OTP →"
            setTextColor(Color.parseColor("#E4E4E7"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dpToPx(6), dpToPx(5), dpToPx(6), dpToPx(5))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(7).toFloat()
                setColor(theme.elevatedInt)
                setStroke(dpToPx(1f), theme.accentBrightInt)
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(5)
            }
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                val rid = repo.state.value.selectedRange
                if (rid.isBlank() && repo.state.value.allocatedNumbers.isEmpty()) {
                    Toast.makeText(context, "প্রথমে তালিকা থেকে একটি রেঞ্জ সিলেক্ট করুন", Toast.LENGTH_SHORT).show()
                } else {
                    switchStep(WindowStep.NUMBERS_AND_OTP)
                }
            }
        }
        rangeStepContainer.addView(jumpToNumbersBar)
        rootCard.addView(rangeStepContainer)

        // =========================================================================
        // STEP 2 CONTAINER: SELECTED RANGE + [2, 3, 4] NUMBER BUTTONS + OTP LIST
        // =========================================================================
        numberStepContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Sub-header: [← Ranges] + Selected Range & Country Badge + [Clear]
        val subHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(5)
            }
        }

        val backToRangesBtn = TextView(context).apply {
            text = "← Ranges"
            setTextColor(Color.parseColor("#E4E4E7"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(7), dpToPx(3), dpToPx(7), dpToPx(3))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(theme.elevatedInt)
                setStroke(dpToPx(1f), theme.accentBrightInt)
            }
            background = bg
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                switchStep(WindowStep.SELECT_RANGE)
            }
        }
        subHeaderRow.addView(backToRangesBtn)

        selectedRangeLabelTv = TextView(context).apply {
            text = "Range: --"
            setTextColor(Color.parseColor("#F4F4F5"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        subHeaderRow.addView(selectedRangeLabelTv)

        val clearBtn = TextView(context).apply {
            text = "Clear"
            setTextColor(theme.textSecondaryInt)
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(6), dpToPx(3), dpToPx(6), dpToPx(3))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(theme.cardInt)
                setStroke(dpToPx(1f), theme.cardBorderInt)
            }
            background = bg
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                repo.clearAllocatedNumbers()
            }
        }
        subHeaderRow.addView(clearBtn)
        numberStepContainer.addView(subHeaderRow)

        // Number Allocation Row: 2, 3, or 4 Numbers buttons
        val allocButtonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }
        }

        take2Btn = createCountOptionButton("2 Num", isPrimary = true) {
            allocateNumbersCount(2)
        }
        take3Btn = createCountOptionButton("3 Num", isPrimary = true) {
            allocateNumbersCount(3)
        }
        take4Btn = createCountOptionButton("4 Num", isPrimary = true) {
            allocateNumbersCount(4)
        }

        allocButtonsRow.addView(take2Btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dpToPx(4)
        })
        allocButtonsRow.addView(take3Btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dpToPx(4)
        })
        allocButtonsRow.addView(take4Btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        numberStepContainer.addView(allocButtonsRow)

        // Compact Scrollable List of Allocated Numbers & Inline OTP Badges
        val numbersScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(132)
            )
        }

        numbersListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        numbersScroll.addView(numbersListContainer)
        numberStepContainer.addView(numbersScroll)

        statusFooterTv = TextView(context).apply {
            text = "Tap number to copy • Auto-OTP (5s)"
            setTextColor(theme.textSecondaryInt)
            textSize = 9.5f
            gravity = Gravity.CENTER
            setPadding(dpToPx(2), dpToPx(4), dpToPx(2), 0)
        }
        numberStepContainer.addView(statusFooterTv)

        rootCard.addView(numberStepContainer)

        updateStepVisibility(animate = false)
        return rootCard
    }

    private fun createHeaderVectorIconButton(
        iconRes: Int,
        bgColor: Int,
        borderColor: Int,
        iconTint: Int,
        marginEndDp: Int,
        onClick: () -> Unit
    ): View {
        val s = dpToPx(24)
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                if (marginEndDp > 0) marginEnd = dpToPx(marginEndDp)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(7).toFloat()
                setColor(bgColor)
                setStroke(dpToPx(1f), borderColor)
            }
            val iv = ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(iconTint)
                val pad = dpToPx(5)
                setPadding(pad, pad, pad, pad)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            addView(iv)
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener { onClick() }
        }
    }

    private fun createCountOptionButton(
        label: String,
        isPrimary: Boolean,
        onClick: () -> Unit
    ): TextView {
        val theme = com.example.ui.theme.AppThemeManager.current
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(9).toFloat()
                if (isPrimary) {
                    setColor(theme.elevatedInt)
                    setStroke(dpToPx(1.2f), theme.accentBrightInt)
                } else {
                    setColor(theme.cardInt)
                    setStroke(dpToPx(1f), theme.cardBorderInt)
                }
            }
            background = bg
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener { onClick() }
        }
    }

    private fun switchStep(step: WindowStep) {
        currentStep = step
        updateStepVisibility(animate = true)
        renderState(repo.state.value)
    }

    private fun updateStepVisibility(animate: Boolean) {
        if (!::rangeStepContainer.isInitialized) return
        if (currentStep == WindowStep.SELECT_RANGE) {
            stepTitleTv.text = "BDX SMS • Ranges"
            rangeStepContainer.visibility = View.VISIBLE
            numberStepContainer.visibility = View.GONE
            if (animate) {
                FloatingWindowAnimator.animateStepTransition(rangeStepContainer)
            }
        } else {
            val rid = repo.state.value.selectedRange.ifBlank { "--" }
            val country = VoltxSmsClient.detectCountryFromRange(rid)
            stepTitleTv.text = "BDX SMS • Numbers"
            selectedRangeLabelTv.text = if (country.isNotBlank()) "$rid • $country" else rid
            rangeStepContainer.visibility = View.GONE
            numberStepContainer.visibility = View.VISIBLE
            if (animate) {
                FloatingWindowAnimator.animateStepTransition(numberStepContainer)
            }
        }
    }

    private fun fetchLiveRanges(silent: Boolean) {
        val apiKey = repo.state.value.apiKey
        if (apiKey.isBlank()) {
            populateRangesList()
            if (!silent) {
                Toast.makeText(context, "App-এর Setup থেকে BDX SMS API Key সেট করুন", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (isFetchingRanges) return
        isFetchingRanges = true
        loadingSpinner.visibility = View.VISIBLE
        rangeHintTv.text = "Loading live ranges..."

        scope.launch {
            val res = VoltxSmsClient.getLiveAccessRanges(apiKey)
            isFetchingRanges = false
            loadingSpinner.visibility = View.GONE

            when (res) {
                is VoltxApiResult.Success -> {
                    // Always sort Facebook ranges to the top
                    cachedServiceRanges = res.data.sortedByDescending { it.isFacebook }
                    populateRangesList()
                }
                is VoltxApiResult.Error -> {
                    rangeHintTv.text = res.message
                    populateRangesList()
                    if (!silent) {
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun populateRangesList() {
        if (!::rangesListContainer.isInitialized) return
        rangesListContainer.removeAllViews()

        val apiKey = repo.state.value.apiKey
        if (apiKey.isBlank()) {
            rangeHintTv.text = "API Key not set"
            val msgTv = TextView(context).apply {
                text = "অ্যাপের ভেতরে Setup & Config অপশনে গিয়ে আপনার BDX SMS API Key সেভ করুন।"
                setTextColor(Color.parseColor("#E4E4E7"))
                textSize = 10.5f
                setPadding(dpToPx(6), dpToPx(8), dpToPx(6), dpToPx(8))
            }
            rangesListContainer.addView(msgTv)
            return
        }

        data class RangeEntry(
            val sid: String,
            val rawRange: String,
            val cleanRid: String,
            val isFb: Boolean,
            val countryLabel: String
        )

        val entries = mutableListOf<RangeEntry>()
        val sortedServices = cachedServiceRanges.sortedByDescending { it.isFacebook }
        for (svc in sortedServices) {
            for (r in svc.ranges) {
                val clean = VoltxSmsClient.cleanRangeId(r)
                if (clean.isNotEmpty()) {
                    val country = VoltxSmsClient.detectCountryFromRange(r)
                    entries.add(RangeEntry(svc.sid, r, clean, svc.isFacebook, country))
                }
            }
        }

        val savedRid = VoltxSmsClient.cleanRangeId(repo.state.value.selectedRange)
        if (entries.isEmpty() && savedRid.isNotEmpty()) {
            entries.add(
                RangeEntry(
                    sid = "Saved",
                    rawRange = savedRid,
                    cleanRid = savedRid,
                    isFb = true,
                    countryLabel = VoltxSmsClient.detectCountryFromRange(savedRid)
                )
            )
        }

        if (entries.isEmpty()) {
            rangeHintTv.text = "Tap refresh icon to load active ranges"
            val emptyTv = TextView(context).apply {
                text = if (isFetchingRanges) "Loading ranges..." else "কোনো লাইভ রেঞ্জ পাওয়া যায়নি। ওপরে রিফ্রেশ বাটনে ট্যাপ করুন।"
                setTextColor(Color.parseColor("#A1A1AA"))
                textSize = 10.5f
                setPadding(dpToPx(6), dpToPx(8), dpToPx(6), dpToPx(8))
            }
            rangesListContainer.addView(emptyTv)
            return
        }

        val fbCount = entries.count { it.isFb }
        rangeHintTv.text = if (fbCount > 0) {
            "Facebook ($fbCount) prioritized • Tap a range:"
        } else {
            "Active Ranges (${entries.size}) • Tap a range:"
        }

        val theme = com.example.ui.theme.AppThemeManager.current
        entries.forEachIndexed { index, entry ->
            val isSelected = entry.cleanRid == savedRid
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(8), dpToPx(7), dpToPx(8), dpToPx(7))
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(9).toFloat()
                    setColor(
                        when {
                            isSelected -> theme.accentSoftBgInt
                            entry.isFb -> theme.elevatedInt
                            else -> theme.cardInt
                        }
                    )
                    setStroke(
                        dpToPx(1f),
                        when {
                            isSelected -> theme.accentBrightInt
                            entry.isFb -> theme.accentInt
                            else -> theme.cardBorderInt
                        }
                    )
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(4)
                }

                FloatingWindowAnimator.applyLiquidTouch(this)
                setOnClickListener {
                    repo.updateSelectedRange(entry.cleanRid)
                    triggerHapticPulse()
                    switchStep(WindowStep.NUMBERS_AND_OTP)
                }
            }

            // Left Service Badge Pill ("FB" or Service ID)
            val badgeTv = TextView(context).apply {
                text = if (entry.isFb) "FB" else entry.sid.take(4).uppercase()
                setTextColor(Color.WHITE)
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(5), dpToPx(2), dpToPx(5), dpToPx(2))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(5).toFloat()
                    setColor(if (entry.isFb) theme.accentInt else theme.cardBorderInt)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(6)
                }
            }
            row.addView(badgeTv)

            // Center Range Digits
            val rangeTv = TextView(context).apply {
                text = entry.rawRange
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(rangeTv)

            // Right Side: Country Flag + Country Name Pill Badge
            val countryTv = TextView(context).apply {
                text = entry.countryLabel
                setTextColor(theme.accentLightTextInt)
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
                val pillBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(6).toFloat()
                    setColor(theme.accentSoftBgInt)
                    setStroke(dpToPx(0.8f), theme.accentInt)
                }
                background = pillBg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(4)
                }
            }
            row.addView(countryTv)

            rangesListContainer.addView(row)
            FloatingWindowAnimator.animateListItemWave(row, index)
        }
    }

    private fun allocateNumbersCount(count: Int) {
        val apiKey = repo.state.value.apiKey
        if (apiKey.isBlank()) {
            Toast.makeText(context, "App-এর Setup থেকে VOLTX API Key সেট করুন!", Toast.LENGTH_SHORT).show()
            return
        }

        val cleanRid = VoltxSmsClient.cleanRangeId(repo.state.value.selectedRange)
        if (cleanRid.isBlank()) {
            Toast.makeText(context, "প্রথমে একটি রেঞ্জ সিলেক্ট করুন!", Toast.LENGTH_SHORT).show()
            switchStep(WindowStep.SELECT_RANGE)
            return
        }

        if (isAllocatingNumbers) return
        isAllocatingNumbers = true
        loadingSpinner.visibility = View.VISIBLE
        statusFooterTv.text = "⏳ Getting $count numbers from $cleanRid..."
        setAllocButtonsEnabled(false)

        scope.launch {
            var successCount = 0
            var lastError: String? = null

            for (i in 1..count) {
                when (val res = VoltxSmsClient.allocateNumber(apiKey, cleanRid)) {
                    is VoltxApiResult.Success -> {
                        successCount++
                        repo.addAllocatedNumber(res.data)
                    }
                    is VoltxApiResult.Error -> {
                        lastError = res.message
                        if (res.code == 2946) break // Out of stock
                    }
                }
                if (i < count) {
                    delay(200)
                }
            }

            isAllocatingNumbers = false
            loadingSpinner.visibility = View.GONE
            setAllocButtonsEnabled(true)

            if (successCount > 0) {
                triggerHapticPulse()
                statusFooterTv.text = "✅ Added $successCount number(s) • Tap number to copy"
            } else {
                statusFooterTv.text = "⚠️ ${lastError ?: "Failed"}"
                Toast.makeText(context, lastError ?: "Failed to allocate number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setAllocButtonsEnabled(enabled: Boolean) {
        if (!::take2Btn.isInitialized) return
        take2Btn.isEnabled = enabled
        take3Btn.isEnabled = enabled
        take4Btn.isEnabled = enabled
        val a = if (enabled) 1f else 0.5f
        take2Btn.alpha = a
        take3Btn.alpha = a
        take4Btn.alpha = a
    }

    private fun startAutoOtpPolling() {
        autoOtpPollJob?.cancel()
        autoOtpPollJob = scope.launch {
            while (isActive && isShowing) {
                val state = repo.state.value
                if (state.apiKey.isNotBlank() && state.allocatedNumbers.isNotEmpty()) {
                    checkOtpsNow(manual = false)
                }
                delay(5000)
            }
        }
    }

    private fun checkOtpsNow(manual: Boolean) {
        val apiKey = repo.state.value.apiKey
        if (apiKey.isBlank()) return

        if (manual) {
            loadingSpinner.visibility = View.VISIBLE
        }

        scope.launch {
            val res = VoltxSmsClient.getSuccessOtps(apiKey)
            if (manual) {
                loadingSpinner.visibility = View.GONE
            }
            if (res is VoltxApiResult.Success) {
                val newlyArrived = repo.mergeIncomingOtps(res.data)
                if (newlyArrived.isNotEmpty()) {
                    val first = newlyArrived.first()
                    val code = first.extractedCode ?: first.message
                    triggerHapticPulse()
                    statusFooterTv.text = "🔑 OTP arrived: $code (Tap OTP to copy)"
                    Toast.makeText(context, "🔑 OTP এসেছে: $code", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderState(state: VoltxSmsState) {
        if (!::numbersListContainer.isInitialized) return

        val rid = state.selectedRange.ifBlank { "--" }
        val country = if (rid != "--") VoltxSmsClient.detectCountryFromRange(rid) else ""
        selectedRangeLabelTv.text = if (country.isNotBlank()) "$rid • $country" else "📘 $rid"

        numbersListContainer.removeAllViews()
        val numbers = state.allocatedNumbers

        if (numbers.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "ওপরে 2 Num, 3 Num বা 4 Num বাটনে ক্লিক করে নাম্বার নিন।"
                setTextColor(Color.parseColor("#A1A1AA"))
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(dpToPx(6), dpToPx(14), dpToPx(6), dpToPx(14))
            }
            numbersListContainer.addView(emptyTv)
            return
        }

        numbers.forEachIndexed { idx, item ->
            val row = buildCompactNumberRow(item)
            numbersListContainer.addView(row)
            FloatingWindowAnimator.animateListItemWave(row, idx)
        }
    }

    /**
     * Compact row for each allocated number:
     * - Left side: Phone number + tiny country flag (tapping copies the number immediately with bouncy pop)
     * - Right side: Clickable OTP badge when OTP arrives (tapping copies the OTP immediately) or waiting indicator `⏳`
     */
    private fun buildCompactNumberRow(item: VoltxAllocatedNumber): View {
        val theme = com.example.ui.theme.AppThemeManager.current
        val hasOtp = !item.otpCode.isNullOrBlank()
        val formattedNumber = repo.formatNumberForCopy(item)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(9).toFloat()
                setColor(if (hasOtp) Color.parseColor("#0D261F") else theme.cardInt)
                setStroke(
                    dpToPx(1f),
                    if (hasOtp) Color.parseColor("#10B981") else theme.cardBorderInt
                )
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(5)
            }
        }

        // Crisp Copy Icon + Phone Number (+Full)
        val copyIconIv = ImageView(context).apply {
            setImageResource(R.drawable.ic_action_copy)
            setColorFilter(theme.accentBrightInt)
            val s = dpToPx(13)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginEnd = dpToPx(6)
            }
        }
        row.addView(copyIconIv)

        val numTv = TextView(context).apply {
            text = formattedNumber
            setTextColor(Color.WHITE)
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener {
                FloatingWindowAnimator.animatePopPulse(row)
                copyToClipboard("BDX SMS Number", formattedNumber)
                triggerHapticPulse()
                statusFooterTv.text = "Copied Number: $formattedNumber"
                Toast.makeText(context, "নাম্বার কপি হয়েছে: $formattedNumber", Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(numTv)

        if (hasOtp) {
            val otpCode = item.otpCode.orEmpty()
            val otpPill = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(7), dpToPx(3), dpToPx(7), dpToPx(3))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(6).toFloat()
                    setColor(Color.parseColor("#059669"))
                    setStroke(dpToPx(1f), Color.parseColor("#34D399"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(4)
                }
                FloatingWindowAnimator.applyLiquidTouch(this)
                setOnClickListener {
                    FloatingWindowAnimator.animatePopPulse(this)
                    copyToClipboard("BDX SMS OTP", otpCode)
                    triggerHapticPulse()
                    statusFooterTv.text = "Copied OTP: $otpCode"
                    Toast.makeText(context, "OTP কপি হয়েছে: $otpCode", Toast.LENGTH_SHORT).show()
                }
            }
            val keyIv = ImageView(context).apply {
                setImageResource(R.drawable.ic_action_key)
                setColorFilter(Color.WHITE)
                val s = dpToPx(11)
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    marginEnd = dpToPx(4)
                }
            }
            val codeTv = TextView(context).apply {
                text = otpCode
                setTextColor(Color.WHITE)
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            otpPill.addView(keyIv)
            otpPill.addView(codeTv)
            row.addView(otpPill)
        } else {
            val waitingBadge = TextView(context).apply {
                text = "WAIT"
                setTextColor(theme.textSecondaryInt)
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(5), dpToPx(2), dpToPx(5), dpToPx(2))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(4).toFloat()
                    setColor(theme.elevatedInt)
                }
            }
            row.addView(waitingBadge)
        }

        return row
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener(dragHandle: View, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
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
                        rootContainer?.let { v ->
                            try {
                                windowManager.updateViewLayout(v, params)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
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

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun triggerHapticPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(35)
                }
            }
        } catch (_: Exception) {
        }
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
