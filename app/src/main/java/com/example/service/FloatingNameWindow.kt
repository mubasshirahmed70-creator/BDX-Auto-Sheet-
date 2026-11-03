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
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.R
import com.example.data.model.UsaNameGenerator
import com.example.data.model.UsaPersonName
import kotlin.math.abs

/**
 * Bento Glass USA Name Generator Floating Window:
 * - Automatically generates a brand-new random name every time it opens AND after every copy click!
 * - Styled in the Bento Command Pro Midnight Sapphire glass aesthetic with crisp vector icons.
 */
class FloatingNameWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onCloseListener: () -> Unit
) {
    private var windowParams: WindowManager.LayoutParams? = null
    private var rootView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentPerson: UsaPersonName = UsaNameGenerator.nextRandomName()

    private lateinit var firstNameTv: TextView
    private lateinit var lastNameTv: TextView
    private lateinit var firstNameTile: LinearLayout
    private lateinit var lastNameTile: LinearLayout

    var isShowing: Boolean = false
        private set

    fun show() {
        if (isShowing) return

        // Always roll a brand-new unique name every time the window opens!
        currentPerson = UsaNameGenerator.nextRandomName()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val windowWidth = dpToPx(248).coerceAtMost((screenWidth * 0.88f).toInt())

        val params = WindowManager.LayoutParams(
            windowWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - windowWidth - dpToPx(16)).coerceAtLeast(dpToPx(16))
            y = dpToPx(130)
        }
        windowParams = params

        val view = buildWindowView(params)
        rootView = view

        try {
            windowManager.addView(view, params)
            isShowing = true
            updateButtonLabels()
            FloatingWindowAnimator.animateWindowOpen(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismiss() {
        if (!isShowing) return
        mainHandler.removeCallbacksAndMessages(null)
        rootView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        rootView = null
        isShowing = false
        onCloseListener()
    }

    fun refreshTheme() {
        if (!isShowing) return
        val params = windowParams ?: return
        val oldRoot = rootView ?: return
        val newView = buildWindowView(params)
        rootView = newView
        try {
            windowManager.removeView(oldRoot)
            windowManager.addView(newView, params)
            updateButtonLabels()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildWindowView(params: WindowManager.LayoutParams): View {
        val theme = com.example.ui.theme.AppThemeManager.current
        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }

        // Sleek Bento Glass Card
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(18).toFloat()
                setColor(theme.bgInt)
                setStroke(dpToPx(1.5f), theme.accentBrightInt)
            }
            elevation = dpToPx(12).toFloat()
        }

        // 1. Bento Glass Capsule Header Bar with Vector Logo, Title, Shuffle Vector Icon, and Close Vector Icon
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(7)
            }
            setPadding(dpToPx(8), dpToPx(6), dpToPx(6), dpToPx(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                colors = intArrayOf(theme.headerStartInt, theme.headerEndInt)
                orientation = GradientDrawable.Orientation.TL_BR
                setStroke(dpToPx(1f), theme.accentBrightInt)
            }
        }

        val iconBox = FrameLayout(context).apply {
            val boxSize = dpToPx(24)
            layoutParams = LinearLayout.LayoutParams(boxSize, boxSize).apply {
                marginEnd = dpToPx(7)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(7).toFloat()
                setColor(theme.accentSoftBgInt)
                setStroke(dpToPx(1f), theme.accentBrightInt)
            }
            val iv = ImageView(context).apply {
                setImageResource(R.drawable.ic_person_logo)
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
        header.addView(iconBox)

        val titleTv = TextView(context).apply {
            text = "USA Name"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(titleTv)

        // Shuffle / Randomize Vector Button
        val randomBtn = createHeaderIconButton(
            iconRes = R.drawable.ic_action_shuffle,
            bgColor = theme.elevatedInt,
            borderColor = theme.accentBrightInt,
            marginEndDp = 5
        ) { btn ->
            FloatingWindowAnimator.animatePopPulse(btn)
            rollNewName()
        }
        header.addView(randomBtn)

        // Close Vector Button
        val closeBtn = createHeaderIconButton(
            iconRes = R.drawable.ic_window_close,
            bgColor = Color.parseColor("#EF4444"),
            borderColor = Color.parseColor("#F87171"),
            marginEndDp = 0
        ) {
            dismiss()
        }
        header.addView(closeBtn)

        setupDragTouchListener(header, params)
        cardLayout.addView(header)

        // 2. 2-Column Bento Grid for First Name & Last Name Tiles
        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // First Name Bento Tile
        firstNameTv = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        firstNameTile = createBentoNameTile(
            badgeLabel = "FIRST",
            valueTextView = firstNameTv,
            marginEndDp = 4,
            marginStartDp = 0
        ) {
            copyFirstNameAndAutoRoll()
        }
        buttonsRow.addView(firstNameTile)

        // Last Name Bento Tile
        lastNameTv = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        lastNameTile = createBentoNameTile(
            badgeLabel = "LAST",
            valueTextView = lastNameTv,
            marginEndDp = 0,
            marginStartDp = 4
        ) {
            copyLastNameAndAutoRoll()
        }
        buttonsRow.addView(lastNameTile)

        cardLayout.addView(buttonsRow)
        root.addView(cardLayout)
        return root
    }

    private fun createBentoNameTile(
        badgeLabel: String,
        valueTextView: TextView,
        marginEndDp: Int,
        marginStartDp: Int,
        onClick: () -> Unit
    ): LinearLayout {
        val theme = com.example.ui.theme.AppThemeManager.current
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (marginEndDp > 0) marginEnd = dpToPx(marginEndDp)
                if (marginStartDp > 0) marginStart = dpToPx(marginStartDp)
            }
            setPadding(dpToPx(9), dpToPx(7), dpToPx(9), dpToPx(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(11).toFloat()
                colors = intArrayOf(theme.elevatedInt, theme.cardInt)
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(dpToPx(1.2f), theme.accentBrightInt)
            }
            elevation = dpToPx(4).toFloat()

            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(2)
                }
            }

            val badgeTv = TextView(context).apply {
                text = badgeLabel
                setTextColor(theme.accentBrightInt)
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val copyIv = ImageView(context).apply {
                setImageResource(R.drawable.ic_action_copy)
                setColorFilter(theme.textSecondaryInt)
                val s = dpToPx(11)
                layoutParams = LinearLayout.LayoutParams(s, s)
            }

            topRow.addView(badgeTv)
            topRow.addView(copyIv)

            addView(topRow)
            addView(valueTextView)

            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener { onClick() }
        }
    }

    private fun createHeaderIconButton(
        iconRes: Int,
        bgColor: Int,
        borderColor: Int,
        marginEndDp: Int,
        onClick: (View) -> Unit
    ): FrameLayout {
        val size = dpToPx(25)
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
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
                setColorFilter(Color.WHITE)
                val pad = dpToPx(5)
                setPadding(pad, pad, pad, pad)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            addView(iv)
            FloatingWindowAnimator.applyLiquidTouch(this)
            setOnClickListener { onClick(this) }
        }
    }

    private fun rollNewName() {
        currentPerson = UsaNameGenerator.nextRandomName()
        updateButtonLabels()
        FloatingWindowAnimator.animatePopPulse(firstNameTile)
        FloatingWindowAnimator.animatePopPulse(lastNameTile)
        triggerHapticPulse()
    }

    private fun updateButtonLabels() {
        if (!::firstNameTv.isInitialized || !::lastNameTv.isInitialized) return
        firstNameTv.text = currentPerson.firstName
        lastNameTv.text = currentPerson.lastName
    }

    /**
     * Copies the current First Name AND immediately generates a brand-new different First Name
     * so every single click gives a fresh name without needing to tap the shuffle button!
     */
    private fun copyFirstNameAndAutoRoll() {
        val copiedFirst = currentPerson.firstName
        copyToClipboard("USA First Name", copiedFirst)
        triggerHapticPulse()
        FloatingWindowAnimator.animatePopPulse(firstNameTile)

        val nextFirst = UsaNameGenerator.nextRandomFirstName()
        currentPerson = currentPerson.copy(firstName = nextFirst)

        firstNameTv.text = "✓ $copiedFirst"
        mainHandler.postDelayed({
            if (isShowing) {
                firstNameTv.text = currentPerson.firstName
                FloatingWindowAnimator.animatePopPulse(firstNameTile)
            }
        }, 320)

        Toast.makeText(context, "Copied First Name: $copiedFirst", Toast.LENGTH_SHORT).show()
    }

    /**
     * Copies the current Last Name AND immediately generates a brand-new different Last Name
     * so every single click gives a fresh surname without needing to tap the shuffle button!
     */
    private fun copyLastNameAndAutoRoll() {
        val copiedLast = currentPerson.lastName
        copyToClipboard("USA Last Name", copiedLast)
        triggerHapticPulse()
        FloatingWindowAnimator.animatePopPulse(lastNameTile)

        val nextLast = UsaNameGenerator.nextRandomLastName()
        currentPerson = currentPerson.copy(lastName = nextLast)

        lastNameTv.text = "✓ $copiedLast"
        mainHandler.postDelayed({
            if (isShowing) {
                lastNameTv.text = currentPerson.lastName
                FloatingWindowAnimator.animatePopPulse(lastNameTile)
            }
        }, 320)

        Toast.makeText(context, "Copied Last Name: $copiedLast", Toast.LENGTH_SHORT).show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun triggerHapticPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(30)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragTouchListener(view: View, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
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
                        try {
                            rootView?.let { windowManager.updateViewLayout(it, params) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun dpToPx(dp: Float): Int {
        val metrics = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics).toInt()
    }

    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())
}
