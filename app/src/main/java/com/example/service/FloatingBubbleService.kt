package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
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
import android.view.ViewGroup
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

    private var isMinimized = true
    private var isRoundCompleteReset = false
    private var lastProcessedRowForRoundReset = -1
    private val roundCompleteHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var floatingInboxWindow: FloatingInboxWindow? = null
    private var floatingFacebookWindow: FloatingFacebookWindow? = null
    private var floatingNameWindow: FloatingNameWindow? = null
    private var isToolsMenuExpanded: Boolean = false

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

        val screenWidth = resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Start on the right side of the screen
            x = (screenWidth - dpToPx(64)).coerceAtLeast(0)
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
        val layout = FrameLayout(this).apply {
            val lp = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                val margin = dpToPx(3)
                setMargins(margin, margin, margin, margin)
            }
            layoutParams = lp
        }

        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(
                Color.parseColor("#2563EB"), // Radiant Electric Royal Blue
                Color.parseColor("#06B6D4")  // Radiant Cyan
            )
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dpToPx(3f), Color.WHITE) // Brilliant white rim
        }
        layout.background = backgroundDrawable
        layout.elevation = dpToPx(8).toFloat()

        val text = TextView(this).apply {
            text = "R$currentRow"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(dpToPx(2).toFloat(), 0f, dpToPx(1).toFloat(), Color.parseColor("#80000000"))
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

        // Completely transparent container so no rectangular border or shadow box is visible
        val mainLayout = LinearLayout(this).apply {
            orientation = if (isHorizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            background = null
        }

        // 1. Sleek Drag Handle Grip
        val dragGrip = createDragHandleView(isHorizontal)
        mainLayout.addView(dragGrip)

        // 2. Control / Minimize / Round Header Button ("R1", "R2"...)
        val headerButton = createHeaderControlView(buttonSizePx, state.currentRow)
        mainLayout.addView(headerButton)

        // 3. Special Tools Hub (⚡ Tools Hub with MailGen ✉️ and Facebook Web 🔵)
        val hubToggleButton = createSpecialHubToggleButton(buttonSizePx)
        mainLayout.addView(hubToggleButton)

        if (isToolsMenuExpanded) {
            val inboxBtn = createSpecialInboxButton(buttonSizePx)
            val fbBtn = createSpecialFacebookButton(buttonSizePx)
            val nameBtn = createSpecialNameButton(buttonSizePx)

            mainLayout.addView(inboxBtn)
            mainLayout.addView(fbBtn)
            mainLayout.addView(nameBtn)
        }

        // 4. Column Buttons (Uniform base color, changes to Green with checkmark when pasted in active round)
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
            val width = if (isHorizontal) dpToPx(6) else dpToPx(26)
            val height = if (isHorizontal) dpToPx(26) else dpToPx(6)
            val lp = LinearLayout.LayoutParams(width, height).apply {
                val margin = dpToPx(3)
                setMargins(margin, margin, margin, margin)
            }
            layoutParams = lp

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(3).toFloat()
                setColor(Color.parseColor("#F1F5F9")) // Luminous light grip
                setStroke(dpToPx(1), Color.parseColor("#94A3B8"))
            }
            background = bg
        }
    }

    private fun createHeaderControlView(sizePx: Int, currentRow: Int): View {
        val layout = FrameLayout(this).apply {
            val lp = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                val margin = dpToPx(3)
                setMargins(margin, margin, margin, margin)
            }
            layoutParams = lp
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(
                Color.parseColor("#1D4ED8"), // Deep Vibrant Royal Blue
                Color.parseColor("#0284C7")  // Sky Blue
            )
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dpToPx(2.5f), Color.WHITE)
        }
        layout.background = bg
        layout.elevation = dpToPx(6).toFloat()

        val tv = TextView(this).apply {
            text = "R$currentRow"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(dpToPx(2).toFloat(), 0f, dpToPx(1).toFloat(), Color.parseColor("#80000000"))
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

    /**
     * Special Tools Hub Toggle Button (⚡ Tools).
     * Clicking it expands or collapses the tools menu (MailGen ✉️, Facebook Web 🔵, USA Names 👤).
     */
    private fun createSpecialHubToggleButton(sizePx: Int): View {
        val layout = FrameLayout(this).apply {
            val lp = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                val margin = dpToPx(3)
                setMargins(margin, margin, margin, margin)
            }
            layoutParams = lp
        }

        // Luminous glowing neon circle
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = if (isToolsMenuExpanded) {
                intArrayOf(Color.parseColor("#F59E0B"), Color.parseColor("#EA580C")) // Blazing Amber / Orange
            } else {
                intArrayOf(Color.parseColor("#C084FC"), Color.parseColor("#7C3AED")) // Vivid Electric Purple
            }
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dpToPx(2.5f), Color.WHITE)
        }
        layout.background = bg
        layout.elevation = dpToPx(8).toFloat()

        val iv = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_bolt_logo)
            setColorFilter(Color.WHITE)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val iconPad = (sizePx * 0.22f).toInt()
            setPadding(iconPad, iconPad, iconPad, iconPad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        layout.addView(iv)

        layout.setOnClickListener {
            isToolsMenuExpanded = !isToolsMenuExpanded
            updateOverlayContent(repository.sheetState.value)
        }

        layout.setOnLongClickListener {
            // Quick shortcut: long press toggles Facebook directly!
            toggleFloatingFacebookWindow()
            // Auto collapse floating bar to main bubble
            isMinimized = true
            isToolsMenuExpanded = false
            updateOverlayContent(repository.sheetState.value)
            true
        }

        return layout
    }

    /**
     * Special circular floating bubble for MailGen Inbox reading.
     * Clicking it opens/toggles the compact draggable Inbox Window
     * and automatically minimizes the floating bar back to the single main bubble!
     */
    private fun createSpecialInboxButton(sizePx: Int): View {
        val layout = FrameLayout(this).apply {
            val lp = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                val margin = dpToPx(3)
                setMargins(margin, margin, margin, margin)
            }
            layoutParams = lp
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(Color.parseColor("#F43F5E"), Color.parseColor("#9333EA")) // Glowing Rose to Purple
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dpToPx(2.5f), Color.WHITE)
        }
        layout.background = bg
        layout.elevation = dpToPx(8).toFloat()

        val iv = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_mail_logo)
            setColorFilter(Color.WHITE)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val iconPad = (sizePx * 0.22f).toInt()
            setPadding(iconPad, iconPad, iconPad, iconPad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        layout.addView(iv)

        layout.setOnClickListener {
            toggleFloatingInboxWindow()
            // Auto collapse floating bar back to the single main bubble
            isMinimized = true
            isToolsMenuExpanded = false
            updateOverlayContent(repository.sheetState.value)
        }

        return layout
    }

    /**
     * Special circular floating bubble for Facebook Web window (Official FB Icon).
     * Clicking it opens the floating mobile Facebook overlay for logging in and copying UID
     * and automatically minimizes the floating bar back to the single main bubble!
     */
    private fun createSpecialFacebookButton(sizePx: Int): View {
        val layout = FrameLayout(this).apply {
            val lp = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                val margin = dpToPx(3)
                setMargins(margin, margin, margin, margin)
            }
            layoutParams = lp
        }

        // Glowing official Facebook blue background with bright white border
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(Color.parseColor("#1877F2"), Color.parseColor("#0855BE")) // Authentic Glowing FB Blue
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dpToPx(2.5f), Color.WHITE)
        }
        layout.background = bg
        layout.elevation = dpToPx(8).toFloat()

        // Authentic Official Facebook Vector Logo!
        val iv = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_facebook_logo)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val iconPad = (sizePx * 0.12f).toInt()
            setPadding(iconPad, iconPad, iconPad, iconPad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        layout.addView(iv)

        layout.setOnClickListener {
            toggleFloatingFacebookWindow()
            // Auto collapse floating bar back to the single main bubble
            isMinimized = true
            isToolsMenuExpanded = false
            updateOverlayContent(repository.sheetState.value)
        }

        return layout
    }

    /**
     * Special circular floating bubble for USA Male Names generator (Official User Vector).
     * Clicking it opens the floating Name Generator card and collapses the bar to the single main bubble.
     * Long-clicking it immediately copies a new random First Name and collapses to the main bubble.
     */
    private fun createSpecialNameButton(sizePx: Int): View {
        val layout = FrameLayout(this).apply {
            val lp = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                val margin = dpToPx(3)
                setMargins(margin, margin, margin, margin)
            }
            layoutParams = lp
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(Color.parseColor("#10B981"), Color.parseColor("#06B6D4")) // Glowing Emerald to Cyan
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dpToPx(2.5f), Color.WHITE)
        }
        layout.background = bg
        layout.elevation = dpToPx(8).toFloat()

        val iv = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_person_logo)
            setColorFilter(Color.WHITE)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val iconPad = (sizePx * 0.22f).toInt()
            setPadding(iconPad, iconPad, iconPad, iconPad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        layout.addView(iv)

        layout.setOnClickListener {
            toggleFloatingNameWindow()
            // Auto collapse floating bar back to the single main bubble
            isMinimized = true
            isToolsMenuExpanded = false
            updateOverlayContent(repository.sheetState.value)
        }

        layout.setOnLongClickListener {
            // Quick action: Long-press instantly rolls and copies a new First Name
            val newPerson = com.example.data.model.UsaNameGenerator.nextRandomName()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("USA First Name", newPerson.firstName)
            clipboard.setPrimaryClip(clip)
            triggerHapticFeedback()
            Toast.makeText(applicationContext, "⚡ Rolled & copied First Name: ${newPerson.firstName}", Toast.LENGTH_SHORT).show()
            // Auto collapse floating bar back to the single main bubble
            isMinimized = true
            isToolsMenuExpanded = false
            updateOverlayContent(repository.sheetState.value)
            true
        }

        return layout
    }

    /**
     * Brings the floating bubble overlay to the very top of the window hierarchy
     * so it is never hidden behind floating popup windows.
     */
    fun bringOverlayToFront() {
        val root = overlayRootView ?: return
        val params = windowParams ?: return
        try {
            windowManager.removeView(root)
            windowManager.addView(root, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleFloatingInboxWindow() {
        if (floatingInboxWindow?.isShowing == true) {
            floatingInboxWindow?.dismiss()
        } else {
            if (floatingInboxWindow == null) {
                floatingInboxWindow = FloatingInboxWindow(this, windowManager) {
                    // Closed callback
                }
            }
            floatingInboxWindow?.show()
            bringOverlayToFront()
        }
    }

    private fun toggleFloatingFacebookWindow() {
        if (floatingFacebookWindow?.isShowing == true) {
            floatingFacebookWindow?.dismiss()
        } else {
            if (floatingFacebookWindow == null) {
                floatingFacebookWindow = FloatingFacebookWindow(this, windowManager) {
                    // Closed callback
                }
            }
            floatingFacebookWindow?.show()
            bringOverlayToFront()
        }
    }

    private fun toggleFloatingNameWindow() {
        if (floatingNameWindow?.isShowing == true) {
            floatingNameWindow?.dismiss()
        } else {
            if (floatingNameWindow == null) {
                floatingNameWindow = FloatingNameWindow(this, windowManager) {
                    // Closed callback
                }
            }
            floatingNameWindow?.show()
            bringOverlayToFront()
        }
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
            colors = if (isPasted) {
                intArrayOf(Color.parseColor("#34D399"), Color.parseColor("#059669")) // Luminous Glowing Emerald Green
            } else {
                intArrayOf(Color.parseColor("#60A5FA"), Color.parseColor("#1D4ED8")) // Glowing Vivid Royal Blue
            }
            orientation = GradientDrawable.Orientation.TL_BR
            // Brilliant, crisp white rim for high contrast
            setStroke(dpToPx(2.5f), Color.WHITE)
        }
        layout.background = bg
        layout.elevation = dpToPx(8).toFloat()

        val tv = TextView(this).apply {
            text = if (isPasted) "$colName✓" else colName
            setTextColor(Color.WHITE)
            // Dynamically scale font based on column name length and checkmark
            textSize = when {
                colName.length <= 2 -> if (isPasted) 13.5f else 16.5f
                colName.length <= 4 -> 12.5f
                else -> 10.5f
            }
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(dpToPx(3).toFloat(), 0f, dpToPx(1.5f).toFloat(), Color.parseColor("#B3000000"))
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
        floatingInboxWindow?.dismiss()
        floatingInboxWindow = null
        floatingFacebookWindow?.dismiss()
        floatingFacebookWindow = null
        floatingNameWindow?.dismiss()
        floatingNameWindow = null
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
 * Custom FrameLayout that intercepts drag gestures seamlessly while ensuring ZERO
 * interference with background apps. Any touch that falls on transparent or empty
 * areas passes directly through to the underlying application.
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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Zero Invisible Wall:
        // If the touch lands on transparent/empty space outside interactive child buttons,
        // do not consume it! Return false so Android delivers it directly to the underlying app!
        if (!isDragging && !isTouchWithinInteractiveChild(ev.x, ev.y)) {
            return false
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isTouchWithinInteractiveChild(x: Float, y: Float): Boolean {
        val rect = Rect()
        return findInteractiveChildAt(this, x, y, rect)
    }

    private fun findInteractiveChildAt(parent: ViewGroup, x: Float, y: Float, rect: Rect): Boolean {
        val count = parent.childCount
        for (i in 0 until count) {
            val child = parent.getChildAt(i) ?: continue
            if (child.visibility != View.VISIBLE) continue

            child.getHitRect(rect)
            if (rect.contains(x.toInt(), y.toInt())) {
                if (child is ViewGroup) {
                    val localX = x - child.left
                    val localY = y - child.top
                    if (findInteractiveChildAt(child, localX, localY, Rect())) {
                        return true
                    }
                    if (child.isClickable || child.hasOnClickListeners()) {
                        return true
                    }
                } else {
                    return true
                }
            }
        }
        return false
    }

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
        if (!isDragging) {
            // Never consume non-drag touches at container level
            return false
        }
        when (ev.action) {
            MotionEvent.ACTION_MOVE -> {
                val dx = (ev.rawX - initialTouchX).toInt()
                val dy = (ev.rawY - initialTouchY).toInt()
                layoutParams.x = initialX + dx
                layoutParams.y = initialY + dy
                try {
                    windowManager.updateViewLayout(this, layoutParams)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return false
    }
}
