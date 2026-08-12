package com.king.zxing.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import kotlin.math.min

/**
 * 平板（sw600dp）适配：
 * - 手机保持清单里的竖屏锁定；平板放开方向（FULL_SENSOR，含横屏）。
 * - 大屏上把内容列限制到可读宽度并居中（iOS 端 HomeView/ScanResultView 同款规则）。
 */
object TabletUi {

    /** 内容列在大屏上的最大宽度（dp）。 */
    const val CONTENT_MAX_WIDTH_DP = 640

    fun isTablet(activity: Activity): Boolean =
        activity.resources.getBoolean(R.bool.is_tablet)

    /**
     * 平板放开方向锁定；在 onCreate 中 super.onCreate 之后调用即可，
     * 运行时设置会覆盖清单里的 portrait 声明。
     */
    fun applyOrientation(activity: Activity) {
        if (isTablet(activity)) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    /**
     * 把 [view] 的宽度限制为 min(屏幕宽, [maxWidthDp]) 并水平居中；仅平板生效。
     * 支持 CoordinatorLayout / FrameLayout 子布局（gravity 居中）与
     * ConstraintLayout 双侧约束居中（0dp match-constraint 子视图）。
     */
    fun applyContentWidth(activity: Activity, view: View, maxWidthDp: Int = CONTENT_MAX_WIDTH_DP) {
        if (!isTablet(activity)) return
        val density = activity.resources.displayMetrics.density
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val maxPx = (maxWidthDp * density).toInt()
        val lp: ViewGroup.LayoutParams = view.layoutParams ?: return
        lp.width = min(screenWidth, maxPx)
        when (lp) {
            is CoordinatorLayout.LayoutParams -> lp.gravity = Gravity.CENTER_HORIZONTAL
            is FrameLayout.LayoutParams -> lp.gravity = Gravity.CENTER_HORIZONTAL
            else -> Unit // ConstraintLayout：左右约束同时存在时自动居中
        }
        view.layoutParams = lp
    }
}
