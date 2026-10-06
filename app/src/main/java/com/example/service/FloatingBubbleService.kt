package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
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
import androidx.core.app.NotificationCompat
import com.example.BdxApplication
import com.example.MainActivity
import com.example.R
import com.example.TransparentClipboardActivity
import com.example.data.model.SheetConfig
import com.example.data.model.SheetState
import com.example.data.repository.SheetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayRootView: DraggableOverlayLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private lateinit var repository: SheetRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateCollectJob: Job? = null

    private var isMinimized = false
    private var isRoundCompleteReset = false
    private var lastProcessedRowForRoundReset = -1
    private val roundCompleteHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        repository = SheetRepository.getInstance(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startForegroundNotification()
        createFloatingOverlay()
        observeSheetState()
    }

    private fun startForegroundNotification() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, BdxApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .addAction(0, getString(R.string.stop_service), stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(BdxApplication.NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun observeSheetState() {
        stateCollectJob = serviceScope.launch {
            repository.sheetState.collect { state ->
                updateOverlayContent(state)
            }
        }
    }

    private fun createFloatingOverlay() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
            y = dpToPx(160)
        }
        windowParams = params

        val root = DraggableOverlayLayout(this, windowManager, params)
        overlayRootView = root

        try {
            windowManager.addView(root, params)
            updateOverlayContent(repository.sheetState.value)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to create floating overlay: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun updateOverlayContent(state: SheetState) {
        val root = overlayRootView ?: return
        root.removeAllViews()

        val config = state.config
        val columns = config.columns
        val currentRow = state.currentRow
        val cells = state.cells
        val buttonSize = dpToPx(config.buttonSizeDp.coerceIn(SheetConfig.MIN_BUTTON_SIZE, SheetConfig.MAX_BUTTON_SIZE))

        // Check if every configured column in the active round is filled
        val allColumnsFilledInCurrentRound = columns.isNotEmpty() && columns.indices.all { idx ->
            cells[currentRow to idx]?.isNotEmpty() == true
        }

        if (allColumnsFilledInCurrentRound) {
            if (lastProcessedRowForRoundReset != currentRow) {
                lastProcessedRowForRoundReset = currentRow
                isRoundCompleteReset = false
                roundCompleteHandler.removeCallbacksAndMessages(null)
                // Show completion with green checkmarks for 600ms, then reset all back to uniform blue
                roundCompleteHandler.postDelayed({
                    isRoundCompleteReset = true
                    updateOverlayContent(repository.sheetState.value)
                }, 600)
            }
        } else {
            // New round started or incomplete round
            if (lastProcessedRowForRoundReset == currentRow) {
                lastProcessedRowForRoundReset = -1
            }
            isRoundCompleteReset = false
        }

        if (isMinimized) {
            // Minimized View: A compact, circular floating badge showing active row
            val minContainer = createMinimizedPillView(buttonSize, state.currentRow)
            root.addView(minContainer)
        } else {
            // Expanded View: Drag Grip + Header + Column Buttons
            val expandedContainer = createExpandedView(state, buttonSize)
            root.addView(expandedContainer)
        }
    }

    private fun createMinimizedPillView(sizePx: Int, currentRow: Int): View {
        val minSize = (sizePx * 0.95).toInt().coerceAtLeast(dpToPx(48))
        val layout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(minSize, minSize)
        }

        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#1E3A8A")) // Dark Royal Blue
            setStroke(dpToPx(2.5f), Color.WHITE)
        }
        layout.background = backgroundDrawable
        layout.elevation = dpToPx(10).toFloat()

        val text = TextView(this).apply {
            text = "R$currentRow\n▼"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        layout.addView(text)

        layout.setOnClickListener {
            isMinimized = false
            updateOverlayContent(repository.sheetState.value)
        }

        return layout
    }

    private fun createExpandedView(state: SheetState, buttonSizePx: Int): View {
        val isHorizontal = state.config.isHorizontalBubbleLayout

        val mainLayout = LinearLayout(this).apply {
            orientation = if (isHorizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padding = dpToPx(6)
            setPadding(padding, padding, padding, padding)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(28).toFloat()
                setColor(Color.parseColor("#E60F172A")) // Translucent dark navy
                setStroke(dpToPx(1.5f), Color.parseColor("#475569"))
            }
            background = bg
            elevation = dpToPx(10).toFloat()
        }

        // 1. Dedicated Visual Drag Handle Grip
        val dragGrip = createDragHandleView(isHorizontal)
        mainLayout.addView(dragGrip)

        // 2. Control / Minimize / Round Header Button
        val headerButton = createHeaderControlView(buttonSizePx, state.currentRow)
        mainLayout.addView(headerButton)

        // 3. Column Buttons (Uniform base color, changes to Green with checkmark when pasted in active round)
        val columns = state.config.columns
        val currentRow = state.currentRow
        val cells = state.cells

        columns.forEachIndexed { index, colName ->
            val isPastedInCurrentRound = cells[currentRow to index]?.isNotEmpty() == true
            val showAsPasted = isPastedInCurrentRound && !isRoundCompleteReset

            val colorHex = if (showAsPasted) {
                "#10B981" // Vibrant Emerald Green when pasted
            } else {
                "#2563EB" // All buttons start/reset with this exact SAME Royal Blue
            }

            val btn = createColumnButton(
                colName = colName,
                colorHex = colorHex,
                sizePx = buttonSizePx,
                isPasted = showAsPasted
            )
            mainLayout.addView(btn)
        }

        return mainLayout
    }

    private fun createDragHandleView(isHorizontal: Boolean): View {
        return View(this).apply {
            val width = if (isHorizontal) dpToPx(8) else dpToPx(28)
            val height = if (isHorizontal) dpToPx(28) else dpToPx(8)
            val lp = LinearLayout.LayoutParams(width, height).apply {
                val margin = dpToPx(4)
                setMargins(margin, margin, margin, margin)
            }
            layoutParams = lp

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(4).toFloat()
                setColor(Color.parseColor("#64748B")) // Slate grip
            }
            background = bg
        }
    }

    private fun createHeaderControlView(sizePx: Int, currentRow: Int): View {
        val controlSize = (sizePx * 0.8).toInt().coerceAtLeast(dpToPx(40))
        val layout = FrameLayout(this).apply {
            val lp = LinearLayout.LayoutParams(controlSize, controlSize).apply {
                val margin = dpToPx(3)
                setMargins(margin, margin, margin, margin)
            }
            layoutParams = lp
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#334155")) // Slate
            setStroke(dpToPx(1.5f), Color.parseColor("#94A3B8"))
        }
        layout.background = bg

        val tv = TextView(this).apply {
            text = "R$currentRow"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        layout.addView(tv)

        // Clicking the control header toggles minimize
        layout.setOnClickListener {
            isMinimized = true
            updateOverlayContent(repository.sheetState.value)
        }

        return layout
    }

    private fun createColumnButton(
        colName: String,
        colorHex: String,
        sizePx: Int,
        isPasted: Boolean
    ): View {
        val layout = FrameLayout(this).apply {
            val lp = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                val margin = dpToPx(3)
                setMargins(margin, margin, margin, margin)
            }
            layoutParams = lp
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(colorHex))
            // Crisp white border
            setStroke(dpToPx(2.5f), Color.WHITE)
        }
        layout.background = bg
        layout.elevation = dpToPx(if (isPasted) 6 else 4).toFloat()

        val tv = TextView(this).apply {
            text = if (isPasted) "$colName✓" else colName
            setTextColor(Color.WHITE)
            // Dynamically scale font based on column name length and checkmark
            textSize = when {
                colName.length <= 2 -> if (isPasted) 13f else 16f
                colName.length <= 4 -> 12f
                else -> 10f
            }
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        layout.addView(tv)

        layout.setOnClickListener {
            handleColumnButtonClick(colName)
        }

        return layout
    }

    /**
     * Tapping a column button first attempts direct clipboard reading. If background access
     * is blocked by Android 10+ focus policy, it seamlessly launches TransparentClipboardActivity.
     */
    private fun handleColumnButtonClick(columnName: String) {
        val directResult = com.example.clipboard.ClipboardHelper.readCurrentText(this)
        if (directResult is com.example.clipboard.ClipboardReadResult.Text) {
            val insertResult = repository.insertFromClipboard(columnName, directResult.content)
            if (insertResult is com.example.data.model.ClipboardInsertResult.Success) {
                triggerHapticFeedback()
                Toast.makeText(
                    applicationContext,
                    "${insertResult.columnName}${insertResult.row} saved",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        // Seamless transparent activity for focused window clipboard access
        TransparentClipboardActivity.launch(this, columnName)
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(40)
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    private fun dpToPx(dp: Float): Int {
        val metrics: DisplayMetrics = resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics).toInt()
    }

    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        roundCompleteHandler.removeCallbacksAndMessages(null)
        stateCollectJob?.cancel()
        overlayRootView?.let { root ->
            try {
                windowManager.removeView(root)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayRootView = null
    }

    companion object {
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
            _isRunning.value = false
        }
    }
}

/**
 * Custom FrameLayout that intercepts drag gestures seamlessly while still letting
 * child buttons receive clicks when not dragging. Allows dragging anywhere on screen!
 */
@SuppressLint("ViewConstructor")
class DraggableOverlayLayout(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams
) : FrameLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (ev.rawX - initialTouchX).toInt()
                val dy = (ev.rawY - initialTouchY).toInt()
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    isDragging = true
                    return true // Intercept drag!
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (ev.rawX - initialTouchX).toInt()
                val dy = (ev.rawY - initialTouchY).toInt()
                if (isDragging || abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    isDragging = true
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(this, layoutParams)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDragging = isDragging
                isDragging = false
                if (wasDragging) {
                    return true
                }
            }
        }
        return super.onTouchEvent(ev)
    }
}
