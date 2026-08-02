package com.king.zxing.gesture

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Outline
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.view.animation.PathInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import kotlin.math.abs
import kotlin.math.max

/**
 * App-drawn interactive back transition (X / Telegram style).
 *
 * Android 14+ supplies gesture progress through AndroidX; ZXingX draws every frame.
 * Android 13 and below use an edge touch recognizer. No platform predictive-back
 * window animation is used; the Activity window is translucent so the previous
 * Activity remains visible beneath the moving surface.
 */
class EdgeSwipeBackController private constructor(
    private val activity: AppCompatActivity
) {
    private val surface: View = activity.window.decorView
    private val density = surface.resources.displayMetrics.density
    private val edgeWidth = 28f * density
    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
    private val settle = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

    private var progress = 0f
    private var direction = 1f
    private var tracking = false
    private var downX = 0f
    private var downY = 0f
    private var velocityTracker: VelocityTracker? = null
    private var animator: ValueAnimator? = null
    private var cornerRadius = 0f
    private var finishing = false

    private val outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
        }
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            direction = if (backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
            begin()
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            direction = if (backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
            apply(backEvent.progress.coerceIn(0f, 1f))
        }

        override fun handleOnBackCancelled() {
            cancel()
        }

        override fun handleOnBackPressed() {
            commit()
        }
    }

    fun install(): EdgeSwipeBackController {
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
        surface.outlineProvider = outlineProvider
        surface.doOnLayout {
            animateEnter()
            if (Build.VERSION.SDK_INT < 34) installLegacyEdgeGesture()
        }
        return this
    }

    fun requestBack() {
        if (!finishing) commit()
    }

    private fun animateEnter() {
        surface.translationX = surface.width * 0.045f
        surface.alpha = 0.98f
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 220L
            interpolator = settle
            addUpdateListener {
                val p = it.animatedValue as Float
                surface.translationX = surface.width * 0.045f * p
                surface.alpha = 1f - 0.02f * p
            }
            start()
        }
    }

    private fun begin() {
        animator?.cancel()
        finishing = false
        prepareSurface()
    }

    private fun prepareSurface() {
        surface.elevation = 24f * density
        surface.clipToOutline = true
    }

    private fun apply(value: Float) {
        prepareSurface()
        progress = value.coerceIn(0f, 1f)
        // X / Telegram style: the page becomes a rounded card and moves only a
        // little toward the active edge. It does not fly 70% off-screen.
        val easedScale = 1f - 0.075f * progress
        surface.translationX = direction * surface.width * 0.10f * progress
        surface.translationY = 0f
        surface.scaleX = easedScale
        surface.scaleY = easedScale
        surface.alpha = 1f
        cornerRadius = 32f * density * progress
        surface.invalidateOutline()
    }

    private fun cancel() {
        animateTo(0f, false)
    }

    private fun commit() {
        if (finishing) return
        if (progress <= 0f) {
            begin()
            progress = 0f
        }
        animateTo(1f, true)
    }

    private fun animateTo(target: Float, finishAtEnd: Boolean) {
        animator?.cancel()
        val start = progress
        animator = ValueAnimator.ofFloat(start, target).apply {
            duration = (150L + 90L * abs(target - start)).toLong()
            interpolator = settle
            addUpdateListener { apply(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (finishAtEnd) finishImmediately() else reset()
                }
            })
            start()
        }
    }

    private fun finishImmediately() {
        if (finishing) return
        finishing = true
        backCallback.isEnabled = false
        activity.finish()
        activity.overridePendingTransition(0, 0)
    }

    private fun reset() {
        progress = 0f
        tracking = false
        surface.translationX = 0f
        surface.translationY = 0f
        surface.scaleX = 1f
        surface.scaleY = 1f
        surface.alpha = 1f
        surface.elevation = 0f
        cornerRadius = 0f
        surface.clipToOutline = false
        surface.invalidateOutline()
    }

    private fun installLegacyEdgeGesture() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        addEdgeCapture(content, Gravity.START, 1f)
        addEdgeCapture(content, Gravity.END, -1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            content.doOnLayout { view ->
                val edge = edgeWidth.toInt()
                view.systemGestureExclusionRects = listOf(
                    Rect(0, 0, edge, view.height),
                    Rect(view.width - edge, 0, view.width, view.height)
                )
            }
        }
    }

    private fun addEdgeCapture(content: ViewGroup, gravity: Int, edgeDirection: Float) {
        val edge = View(activity).apply {
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = FrameLayout.LayoutParams(edgeWidth.toInt(), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            this.gravity = gravity
        }
        content.addView(edge, params)
        edge.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    direction = edgeDirection
                    tracking = true
                    downX = event.x
                    downY = event.y
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    begin()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!tracking) return@setOnTouchListener false
                    velocityTracker?.addMovement(event)
                    val dx = (event.x - downX) * direction
                    val dy = abs(event.y - downY)
                    if (dx < touchSlop && dy > touchSlop * 1.4f) {
                        cancelLegacyTracking()
                        return@setOnTouchListener false
                    }
                    if (dx >= 0f) {
                        apply((dx / (surface.width * 0.72f)).coerceIn(0f, 1f))
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!tracking) return@setOnTouchListener false
                    velocityTracker?.apply {
                        addMovement(event)
                        computeCurrentVelocity(1000)
                    }
                    val velocity = (velocityTracker?.xVelocity ?: 0f) * direction
                    val projected = progress + velocity / max(1f, surface.width) * 0.18f
                    tracking = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                    if (projected > 0.42f || velocity > 1050f) commit() else cancel()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (tracking) cancel()
                    cancelLegacyTracking()
                    true
                }

                else -> tracking
            }
        }
    }

    private fun cancelLegacyTracking() {
        tracking = false
        velocityTracker?.recycle()
        velocityTracker = null
    }

    companion object {
        fun install(activity: AppCompatActivity): EdgeSwipeBackController {
            return EdgeSwipeBackController(activity).install()
        }
    }
}
