package com.example.service

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

/**
 * Provides liquid-smooth ("water-like") animations, spring touch physics,
 * staggered wave transitions, and reliable Minimize <-> Maximize bubble morphing
 * for all floating windows.
 */
object FloatingWindowAnimator {

    /**
     * Smooth fluid entrance animation when a floating window opens.
     */
    fun animateWindowOpen(view: View) {
        view.alpha = 0f
        view.scaleX = 0.78f
        view.scaleY = 0.78f
        view.translationY = -18f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(320L)
            .setInterpolator(OvershootInterpolator(1.18f))
            .start()
    }

    /**
     * Smooth fluid transition when switching between internal views/steps.
     */
    fun animateStepTransition(enteringView: View) {
        enteringView.alpha = 0f
        enteringView.translationX = 24f
        enteringView.scaleX = 0.96f
        enteringView.scaleY = 0.96f
        enteringView.animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(250L)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()
    }

    /**
     * Staggered waterfall/wave entrance animation for list items (ranges, numbers, messages).
     */
    fun animateListItemWave(view: View, index: Int) {
        val delay = (index.coerceAtMost(10) * 32L)
        view.alpha = 0f
        view.translationY = 18f
        view.scaleX = 0.94f
        view.scaleY = 0.94f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delay)
            .setDuration(260L)
            .setInterpolator(OvershootInterpolator(1.08f))
            .start()
    }

    /**
     * Celebratory pop animation when an OTP arrives or an item is copied.
     */
    fun animatePopPulse(view: View) {
        view.animate().cancel()
        view.scaleX = 0.86f
        view.scaleY = 0.86f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(280L)
            .setInterpolator(OvershootInterpolator(2.6f))
            .start()
    }

    /**
     * Attaches liquid-smooth press compression & spring rebound physics to any clickable view.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun applyLiquidTouch(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .setDuration(90L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(240L)
                        .setInterpolator(OvershootInterpolator(2.3f))
                        .start()
                }
            }
            // Return false so standard OnClickListener and Ripple still fire normally
            false
        }
    }

    /**
     * Starts a gentle, continuous breathing glow pulse on a minimized bubble or badge.
     */
    fun startBreathingPulse(view: View): ObjectAnimator {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.95f, 1.05f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.95f, 1.05f)
        return ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY).apply {
            duration = 1150L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Creates a sleek, glowing circular minimized bubble for a special floating window.
     * Supports both smooth dragging across the screen AND 1-tap maximizing back to the full window!
     */
    @SuppressLint("ClickableViewAccessibility")
    fun createMinimizedWindowBubble(
        context: Context,
        windowManager: WindowManager,
        rootViewProvider: () -> View?,
        params: WindowManager.LayoutParams,
        iconResId: Int,
        accentColorHex: String? = null,
        tintIconWhite: Boolean = true,
        onTapMaximize: () -> Unit
    ): FrameLayout {
        val theme = com.example.ui.theme.AppThemeManager.current
        val resolvedAccent = accentColorHex?.let { Color.parseColor(it) } ?: theme.accentBrightInt
        val sizePx = dpToPx(context, 48f)
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        val bubble = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                val m = dpToPx(context, 4f)
                setMargins(m, m, m, m)
            }
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(
                    theme.elevatedInt,
                    theme.bgInt
                )
                orientation = GradientDrawable.Orientation.TL_BR
                setStroke(dpToPx(context, 2.5f), resolvedAccent)
            }
            background = bg
            elevation = dpToPx(context, 8f).toFloat()
        }

        val iconView = ImageView(context).apply {
            setImageResource(iconResId)
            if (tintIconWhite) {
                setColorFilter(Color.WHITE)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = (sizePx * 0.22f).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        bubble.addView(iconView)

        var initialX = 0
        var initialY = 0
        var downRawX = 0f
        var downRawY = 0f
        var isDragging = false

        bubble.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    isDragging = false
                    v.animate()
                        .scaleX(0.88f)
                        .scaleY(0.88f)
                        .setDuration(90L)
                        .start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        rootViewProvider()?.let { root ->
                            try {
                                windowManager.updateViewLayout(root, params)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(220L)
                        .setInterpolator(OvershootInterpolator(2.2f))
                        .start()
                    if (!isDragging) {
                        onTapMaximize()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180L)
                        .start()
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        return bubble
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
