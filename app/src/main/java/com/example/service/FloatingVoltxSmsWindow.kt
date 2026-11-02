package com.example.service

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
 * Ultra-compact, draggable 2-Step Floating Window for VOLTX SMS:
 * - No API Key input inside the floating overlay (configured in App Setup).
 * - Step 1 (Ranges View): Shows live ranges (GET /liveaccess) with Facebook ranges always prioritized on top.
 * - Step 2 (Numbers & OTP View): After selecting a range, shows 2, 3, 4 number allocation buttons.
 *   Tap any allocated number to copy it; incoming OTPs appear as a compact clickable badge right next to the number.
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
    private var rootView: View? = null
    private var stateObserverJob: Job? = null
    private var autoOtpPollJob: Job? = null

    private var currentStep: WindowStep = WindowStep.SELECT_RANGE
    private var cachedServiceRanges: List<VoltxServiceRange> = emptyList()
    private var isFetchingRanges: Boolean = false
    private var isAllocatingNumbers: Boolean = false

    // UI Containers
    private lateinit var stepTitleTv: TextView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var refreshBtn: TextView
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
        // Compact width (~232dp) so it takes very little screen space
        val windowWidth = dpToPx(232).coerceAtMost((screenWidth * 0.78f).toInt())

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

        // Always start on Step 1 (Ranges list) when opening from bubble, as requested
        currentStep = WindowStep.SELECT_RANGE

        val view = buildCompactWindowView(params)
        rootView = view

        try {
            windowManager.addView(view, params)
            isShowing = true

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
            Toast.makeText(context, "Cannot open VOLTX window: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun dismiss() {
        if (!isShowing) return
        stateObserverJob?.cancel()
        stateObserverJob = null
        autoOtpPollJob?.cancel()
        autoOtpPollJob = null
        rootView?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        rootView = null
        isShowing = false
        onCloseListener()
    }

    private fun buildCompactWindowView(params: WindowManager.LayoutParams): View {
        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14).toFloat()
                setColor(Color.parseColor("#111115")) // Matte Black / Dark Charcoal
                setStroke(dpToPx(1.5f), Color.parseColor("#27272A"))
            }
            background = bg
            elevation = dpToPx(10).toFloat()
            setPadding(dpToPx(8), dpToPx(7), dpToPx(8), dpToPx(8))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. COMPACT DRAGGABLE HEADER BAR
        val headerBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(2), dpToPx(1), dpToPx(2), dpToPx(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val logoIv = ImageView(context).apply {
            setImageResource(R.drawable.ic_sms_logo)
            setColorFilter(Color.parseColor("#EF4444"))
            val s = dpToPx(14)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginEnd = dpToPx(5)
            }
        }
        headerBar.addView(logoIv)

        stepTitleTv = TextView(context).apply {
            text = "VOLTX • Select Range"
            setTextColor(Color.parseColor("#F4F4F5"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerBar.addView(stepTitleTv)

        loadingSpinner = ProgressBar(context).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dpToPx(13), dpToPx(13)).apply {
                marginEnd = dpToPx(4)
            }
        }
        headerBar.addView(loadingSpinner)

        // Refresh button (reloads ranges in Step 1 or checks OTPs in Step 2)
        refreshBtn = TextView(context).apply {
            text = "↻"
            setTextColor(Color.parseColor("#E4E4E7"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val s = dpToPx(22)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginEnd = dpToPx(4)
            }
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#1E1E24"))
                setStroke(dpToPx(1f), Color.parseColor("#3F3F46"))
            }
            background = bg
            setOnClickListener {
                if (currentStep == WindowStep.SELECT_RANGE) {
                    fetchLiveRanges(silent = false)
                } else {
                    checkOtpsNow(manual = true)
                }
            }
        }
        headerBar.addView(refreshBtn)

        // Close button
        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val s = dpToPx(22)
            layoutParams = LinearLayout.LayoutParams(s, s)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#DC2626"))
            }
            background = bg
            setOnClickListener { dismiss() }
        }
        headerBar.addView(closeBtn)

        setupDragListener(headerBar, params)
        rootCard.addView(headerBar)

        // =========================================================================
        // STEP 1 CONTAINER: RANGE SELECTION LIST (Facebook prioritized on top)
        // =========================================================================
        rangeStepContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        rangeHintTv = TextView(context).apply {
            text = "📘 Facebook ranges prioritized on top"
            setTextColor(Color.parseColor("#A1A1AA"))
            textSize = 9.5f
            setPadding(dpToPx(2), 0, dpToPx(2), dpToPx(4))
        }
        rangeStepContainer.addView(rangeHintTv)

        val rangeScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(145)
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

        // If user already has allocated numbers, allow jumping to Step 2 directly as well
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
                setColor(Color.parseColor("#18181D"))
                setStroke(dpToPx(1f), Color.parseColor("#27272A"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(5)
            }
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

        // Sub-header: [← Ranges] + Selected Range Badge + [Clear]
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
                setColor(Color.parseColor("#1E1E24"))
                setStroke(dpToPx(1f), Color.parseColor("#3F3F46"))
            }
            background = bg
            setOnClickListener {
                switchStep(WindowStep.SELECT_RANGE)
            }
        }
        subHeaderRow.addView(backToRangesBtn)

        selectedRangeLabelTv = TextView(context).apply {
            text = "Range: --"
            setTextColor(Color.parseColor("#F4F4F5"))
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        subHeaderRow.addView(selectedRangeLabelTv)

        val clearBtn = TextView(context).apply {
            text = "Clear"
            setTextColor(Color.parseColor("#A1A1AA"))
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(6), dpToPx(3), dpToPx(6), dpToPx(3))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#18181D"))
                setStroke(dpToPx(1f), Color.parseColor("#27272A"))
            }
            background = bg
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
                dpToPx(130)
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
            setTextColor(Color.parseColor("#A1A1AA"))
            textSize = 9.5f
            gravity = Gravity.CENTER
            setPadding(dpToPx(2), dpToPx(4), dpToPx(2), 0)
        }
        numberStepContainer.addView(statusFooterTv)

        rootCard.addView(numberStepContainer)

        updateStepVisibility()
        return rootCard
    }

    private fun createCountOptionButton(
        label: String,
        isPrimary: Boolean,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(7).toFloat()
                if (isPrimary) {
                    setColor(Color.parseColor("#DC2626"))
                    setStroke(dpToPx(1f), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#1E1E24"))
                    setStroke(dpToPx(1f), Color.parseColor("#3F3F46"))
                }
            }
            background = bg
            setOnClickListener { onClick() }
        }
    }

    private fun switchStep(step: WindowStep) {
        currentStep = step
        updateStepVisibility()
        renderState(repo.state.value)
    }

    private fun updateStepVisibility() {
        if (!::rangeStepContainer.isInitialized) return
        if (currentStep == WindowStep.SELECT_RANGE) {
            stepTitleTv.text = "VOLTX • Select Range"
            rangeStepContainer.visibility = View.VISIBLE
            numberStepContainer.visibility = View.GONE
        } else {
            val rid = repo.state.value.selectedRange.ifBlank { "--" }
            stepTitleTv.text = "VOLTX • Numbers"
            selectedRangeLabelTv.text = "📘 $rid"
            rangeStepContainer.visibility = View.GONE
            numberStepContainer.visibility = View.VISIBLE
        }
    }

    private fun fetchLiveRanges(silent: Boolean) {
        val apiKey = repo.state.value.apiKey
        if (apiKey.isBlank()) {
            populateRangesList()
            if (!silent) {
                Toast.makeText(context, "App-এর Setup থেকে VOLTX API Key সেট করুন", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (isFetchingRanges) return
        isFetchingRanges = true
        loadingSpinner.visibility = View.VISIBLE
        rangeHintTv.text = "⏳ Loading live ranges..."

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
                    rangeHintTv.text = "⚠️ ${res.message}"
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
            rangeHintTv.text = "⚠️ API Key not set"
            val msgTv = TextView(context).apply {
                text = "অ্যাপের ভেতরে Setup / Configuration অপশনে গিয়ে আপনার VOLTX API Key (mauthapi) সেভ করুন।"
                setTextColor(Color.parseColor("#E4E4E7"))
                textSize = 10.5f
                setPadding(dpToPx(6), dpToPx(8), dpToPx(6), dpToPx(8))
            }
            rangesListContainer.addView(msgTv)
            return
        }

        // Flatten ranges with Facebook always prioritized at the very top
        data class RangeEntry(val sid: String, val rawRange: String, val cleanRid: String, val isFb: Boolean)

        val entries = mutableListOf<RangeEntry>()
        val sortedServices = cachedServiceRanges.sortedByDescending { it.isFacebook }
        for (svc in sortedServices) {
            for (r in svc.ranges) {
                val clean = VoltxSmsClient.cleanRangeId(r)
                if (clean.isNotEmpty()) {
                    entries.add(RangeEntry(svc.sid, r, clean, svc.isFacebook))
                }
            }
        }

        // If user had a previously saved range and live list is empty right now, still show it so they can tap it
        val savedRid = VoltxSmsClient.cleanRangeId(repo.state.value.selectedRange)
        if (entries.isEmpty() && savedRid.isNotEmpty()) {
            entries.add(RangeEntry("Saved", savedRid, savedRid, true))
        }

        if (entries.isEmpty()) {
            rangeHintTv.text = "Tap ↻ to load active ranges"
            val emptyTv = TextView(context).apply {
                text = if (isFetchingRanges) "Loading ranges..." else "কোনো লাইভ রেঞ্জ পাওয়া যায়নি। ওপরে ↻ বাটনে ট্যাপ করুন।"
                setTextColor(Color.parseColor("#A1A1AA"))
                textSize = 10.5f
                setPadding(dpToPx(6), dpToPx(8), dpToPx(6), dpToPx(8))
            }
            rangesListContainer.addView(emptyTv)
            return
        }

        val fbCount = entries.count { it.isFb }
        rangeHintTv.text = if (fbCount > 0) {
            "📘 Facebook ($fbCount) prioritized • Tap a range:"
        } else {
            "🌐 Active Ranges (${entries.size}) • Tap a range:"
        }

        for (entry in entries) {
            val isSelected = entry.cleanRid == savedRid
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(7).toFloat()
                    setColor(
                        when {
                            isSelected -> Color.parseColor("#271215")
                            entry.isFb -> Color.parseColor("#161922")
                            else -> Color.parseColor("#18181D")
                        }
                    )
                    setStroke(
                        dpToPx(1f),
                        when {
                            isSelected -> Color.parseColor("#EF4444")
                            entry.isFb -> Color.parseColor("#2563EB")
                            else -> Color.parseColor("#27272A")
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

                setOnClickListener {
                    repo.updateSelectedRange(entry.cleanRid)
                    triggerHapticPulse()
                    switchStep(WindowStep.NUMBERS_AND_OTP)
                }
            }

            val badgeTv = TextView(context).apply {
                text = if (entry.isFb) "📘 FB" else entry.sid.take(6)
                setTextColor(if (entry.isFb) Color.parseColor("#60A5FA") else Color.parseColor("#A1A1AA"))
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(6)
                }
            }
            row.addView(badgeTv)

            val rangeTv = TextView(context).apply {
                text = entry.rawRange
                setTextColor(Color.WHITE)
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(rangeTv)

            val arrowTv = TextView(context).apply {
                text = "Select ›"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            row.addView(arrowTv)

            rangesListContainer.addView(row)
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
                    delay(180)
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

        populateRangesList()
        selectedRangeLabelTv.text = "📘 ${state.selectedRange.ifBlank { "--" }}"

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

        for (item in numbers) {
            val row = buildCompactNumberRow(item)
            numbersListContainer.addView(row)
        }
    }

    /**
     * Compact row for each allocated number:
     * - Left side: Phone number (tapping it copies the number immediately)
     * - Right side: Small OTP badge when OTP arrives (tapping it copies the OTP immediately) or waiting indicator `⏳`
     */
    private fun buildCompactNumberRow(item: VoltxAllocatedNumber): View {
        val hasOtp = !item.otpCode.isNullOrBlank()
        val formattedNumber = repo.formatNumberForCopy(item)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(7), dpToPx(5), dpToPx(7), dpToPx(5))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(7).toFloat()
                setColor(if (hasOtp) Color.parseColor("#11221B") else Color.parseColor("#18181D"))
                setStroke(
                    dpToPx(1f),
                    if (hasOtp) Color.parseColor("#10B981") else Color.parseColor("#27272A")
                )
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(4)
            }
        }

        // Clickable Phone Number Text (Copies Number on tap)
        val numTv = TextView(context).apply {
            text = formattedNumber
            setTextColor(Color.WHITE)
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                copyToClipboard("VOLTX Number", formattedNumber)
                triggerHapticPulse()
                statusFooterTv.text = "📋 Copied Number: $formattedNumber"
                Toast.makeText(context, "📋 নাম্বার কপি হয়েছে: $formattedNumber", Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(numTv)

        if (hasOtp) {
            // Compact clickable OTP badge right beside the number (Copies OTP on tap)
            val otpCode = item.otpCode.orEmpty()
            val otpBadge = TextView(context).apply {
                text = "🔑 $otpCode"
                setTextColor(Color.WHITE)
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(7), dpToPx(3), dpToPx(7), dpToPx(3))
                val badgeBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(6).toFloat()
                    setColor(Color.parseColor("#059669"))
                    setStroke(dpToPx(1f), Color.parseColor("#34D399"))
                }
                background = badgeBg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(4)
                }
                setOnClickListener {
                    copyToClipboard("VOLTX OTP", otpCode)
                    triggerHapticPulse()
                    statusFooterTv.text = "🔑 Copied OTP: $otpCode"
                    Toast.makeText(context, "🔑 OTP কপি হয়েছে: $otpCode", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(otpBadge)
        } else {
            // Compact waiting indicator while polling /success-otp
            val waitingTv = TextView(context).apply {
                text = "⏳"
                setTextColor(Color.parseColor("#A1A1AA"))
                textSize = 10f
                setPadding(dpToPx(4), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            row.addView(waitingTv)
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
                        rootView?.let { v ->
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
