package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.example.clipboard.ClipboardHelper
import com.example.clipboard.ClipboardReadResult
import com.example.data.model.ClipboardInsertResult
import com.example.data.repository.SheetRepository

/**
 * Transparent foreground activity used to gain window focus on Android 10+
 * so the system allows reading the clipboard when the user taps an overlay bubble.
 */
class TransparentClipboardActivity : Activity() {

    private var hasProcessed = false
    private val handler = Handler(Looper.getMainLooper())
    private var attempts = 0
    private val maxAttempts = 12

    private val checkClipboardRunnable = object : Runnable {
        override fun run() {
            if (hasProcessed || isFinishing || isDestroyed) return
            attempts++

            val result = ClipboardHelper.readCurrentText(this@TransparentClipboardActivity)
            if (result is ClipboardReadResult.Text) {
                hasProcessed = true
                saveAndFinish(result.content)
                return
            }

            if (attempts < maxAttempts) {
                // Retry every 45ms to catch focus grant from WindowManagerService
                handler.postDelayed(this, 45)
            } else {
                hasProcessed = true
                reportFailureAndFinish(result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableTransitions()

        // Add a real focusable view to ensure WindowManager attaches and grants window focus
        val root = FrameLayout(this)
        val dummy = View(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(dummy, FrameLayout.LayoutParams(1, 1))
        setContentView(root)
        dummy.requestFocus()

        // Begin polling for clipboard availability
        handler.post(checkClipboardRunnable)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasProcessed) {
            checkClipboardRunnable.run()
        }
    }

    private fun saveAndFinish(content: String) {
        val columnName = intent.getStringExtra(EXTRA_COLUMN_NAME) ?: "A"
        val repository = SheetRepository.getInstance(applicationContext)

        val insertResult = repository.insertFromClipboard(columnName, content)
        when (insertResult) {
            is ClipboardInsertResult.Success -> {
                triggerHapticFeedback()
                Toast.makeText(
                    applicationContext,
                    "${insertResult.columnName}${insertResult.row} saved",
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ClipboardInsertResult.EmptyClipboard -> {
                Toast.makeText(applicationContext, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            }
            is ClipboardInsertResult.NoUsableText -> {
                Toast.makeText(applicationContext, "Clipboard does not contain usable text.", Toast.LENGTH_SHORT).show()
            }
            is ClipboardInsertResult.Error -> {
                Toast.makeText(applicationContext, "Error: ${insertResult.message}", Toast.LENGTH_SHORT).show()
            }
        }

        closeActivity()
    }

    private fun reportFailureAndFinish(result: ClipboardReadResult) {
        when (result) {
            is ClipboardReadResult.Empty -> {
                Toast.makeText(applicationContext, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            }
            is ClipboardReadResult.NonTextContent -> {
                Toast.makeText(applicationContext, "Clipboard does not contain usable text.", Toast.LENGTH_SHORT).show()
            }
            is ClipboardReadResult.Error -> {
                Toast.makeText(applicationContext, "Clipboard error: ${result.error}", Toast.LENGTH_SHORT).show()
            }
            is ClipboardReadResult.Text -> {
                saveAndFinish(result.content)
                return
            }
        }
        closeActivity()
    }

    private fun closeActivity() {
        handler.removeCallbacksAndMessages(null)
        finish()
        disableTransitions()
    }

    private fun disableTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(40)
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        const val EXTRA_COLUMN_NAME = "extra_column_name"

        fun launch(context: Context, columnName: String) {
            val intent = Intent(context, TransparentClipboardActivity::class.java).apply {
                putExtra(EXTRA_COLUMN_NAME, columnName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
            context.startActivity(intent)
        }
    }
}
