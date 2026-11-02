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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.data.model.UsaNameGenerator
import com.example.data.model.UsaPersonName
import kotlin.math.abs

/**
 * Super-compact, minimalist floating window for USA Male Names.
 * - Automatically generates a brand-new random name every time it opens AND after every copy click!
 * - Styled in the Matte Black & Crimson Red theme.
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

    // 2 main copy buttons
    private lateinit var firstNameBtn: TextView
    private lateinit var lastNameBtn: TextView

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
        val windowWidth = dpToPx(236).coerceAtMost((screenWidth * 0.85f).toInt())

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

    @SuppressLint("ClickableViewAccessibility")
    private fun buildWindowView(params: WindowManager.LayoutParams): View {
        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }

        // Sleek Matte Black card with crisp Crimson Red border
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(8))

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14).toFloat()
                setColor(Color.parseColor("#0D0D11")) // Matte Obsidian Black
                setStroke(dpToPx(1.5f), Color.parseColor("#DC2626")) // Crisp Red Accent Border
            }
            background = bg
            elevation = dpToPx(10).toFloat()
        }

        // 1. Header Drag Bar with mini Title, Random button (🎲), and Close button (✕)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }
        }

        val titleTv = TextView(context).apply {
            text = "🇺🇸 USA Name"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(titleTv)

        // Randomize button (🎲) - Matte Black with Red border
        val randomBtn = TextView(context).apply {
            text = "🎲"
            textSize = 14f
            gravity = Gravity.CENTER
            val size = dpToPx(26)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(4)
            }
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#18181F"))
                setStroke(dpToPx(1f), Color.parseColor("#EF4444"))
            }
            background = btnBg
            setOnClickListener {
                rollNewName()
            }
        }
        header.addView(randomBtn)

        // Close button (✕)
        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val size = dpToPx(26)
            layoutParams = LinearLayout.LayoutParams(size, size)
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(Color.parseColor("#DC2626"))
            }
            background = btnBg
            setOnClickListener {
                dismiss()
            }
        }
        header.addView(closeBtn)

        setupDragTouchListener(header, params)
        cardLayout.addView(header)

        // 2. Row with First Name & Last Name buttons (Sleek Black with Red Accent Border)
        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // First Name Button (Matte Black surface + Crimson Red border)
        firstNameBtn = TextView(context).apply {
            val lp = LinearLayout.LayoutParams(0, dpToPx(40), 1f).apply {
                marginEnd = dpToPx(4)
            }
            layoutParams = lp
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(9).toFloat()
                colors = intArrayOf(
                    Color.parseColor("#1A1A22"),
                    Color.parseColor("#121217")
                )
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(dpToPx(1.5f), Color.parseColor("#EF4444"))
            }
            background = btnBg
            elevation = dpToPx(4).toFloat()
            setOnClickListener {
                copyFirstNameAndAutoRoll()
            }
        }
        buttonsRow.addView(firstNameBtn)

        // Last Name Button (Matte Black surface + Crimson Red border)
        lastNameBtn = TextView(context).apply {
            val lp = LinearLayout.LayoutParams(0, dpToPx(40), 1f).apply {
                marginStart = dpToPx(4)
            }
            layoutParams = lp
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(9).toFloat()
                colors = intArrayOf(
                    Color.parseColor("#1A1A22"),
                    Color.parseColor("#121217")
                )
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(dpToPx(1.5f), Color.parseColor("#EF4444"))
            }
            background = btnBg
            elevation = dpToPx(4).toFloat()
            setOnClickListener {
                copyLastNameAndAutoRoll()
            }
        }
        buttonsRow.addView(lastNameBtn)

        cardLayout.addView(buttonsRow)
        root.addView(cardLayout)
        return root
    }

    private fun rollNewName() {
        currentPerson = UsaNameGenerator.nextRandomName()
        updateButtonLabels()
        triggerHapticPulse()
    }

    private fun updateButtonLabels() {
        if (!::firstNameBtn.isInitialized || !::lastNameBtn.isInitialized) return
        firstNameBtn.text = currentPerson.firstName
        lastNameBtn.text = currentPerson.lastName
    }

    /**
     * Copies the current First Name AND immediately generates a brand-new different First Name
     * so every single click gives a fresh name without needing to tap the random (🎲) button!
     */
    private fun copyFirstNameAndAutoRoll() {
        val copiedFirst = currentPerson.firstName
        copyToClipboard("USA First Name", copiedFirst)
        triggerHapticPulse()

        // Generate the next unique First Name immediately
        val nextFirst = UsaNameGenerator.nextRandomFirstName()
        currentPerson = currentPerson.copy(firstName = nextFirst)

        firstNameBtn.text = "✓ $copiedFirst"
        mainHandler.postDelayed({
            if (isShowing) {
                firstNameBtn.text = currentPerson.firstName
            }
        }, 350)

        Toast.makeText(context, "Copied First Name: $copiedFirst", Toast.LENGTH_SHORT).show()
    }

    /**
     * Copies the current Last Name AND immediately generates a brand-new different Last Name
     * so every single click gives a fresh surname without needing to tap the random (🎲) button!
     */
    private fun copyLastNameAndAutoRoll() {
        val copiedLast = currentPerson.lastName
        copyToClipboard("USA Last Name", copiedLast)
        triggerHapticPulse()

        // Generate the next unique Last Name immediately
        val nextLast = UsaNameGenerator.nextRandomLastName()
        currentPerson = currentPerson.copy(lastName = nextLast)

        lastNameBtn.text = "✓ $copiedLast"
        mainHandler.postDelayed({
            if (isShowing) {
                lastNameBtn.text = currentPerson.lastName
            }
        }, 350)

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
