package com.king.zxing

import android.view.View
import androidx.annotation.IdRes
import com.google.zxing.Result
import com.king.camera.scan.BaseCameraScanFragment
import com.king.camera.scan.analyze.Analyzer
import com.king.view.viewfinderview.ViewfinderView
import com.king.zxing.analyze.MultiFormatAnalyzer

/**
 * 基于zxing实现的扫码识别 - 相机扫描基类
 * <p>
 * 通过继承 [BarcodeCameraScanActivity]或[BarcodeCameraScanFragment]可快速实现扫码识别
 *
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 * <p>
 * <a href="https://github.com/jenly1314">Follow me</a>
 */
abstract class BarcodeCameraScanFragment : BaseCameraScanFragment<Result>() {

    /**
     * 扫码框视图；当 [getViewfinderViewId] 返回有效 ID 时会在 [initUI] 中自动绑定。
     */
    protected var viewfinderView: ViewfinderView? = null

    override fun initUI() {
        val viewfinderViewId = getViewfinderViewId()
        if (viewfinderViewId != View.NO_ID && viewfinderViewId != 0) {
            viewfinderView = rootView.findViewById(viewfinderViewId)
        }
        super.initUI()
    }

    override fun createAnalyzer(): Analyzer<Result>? {
        return MultiFormatAnalyzer()
    }

    /**
     * 布局 ID；通过覆写此方法可自定义布局。
     *
     * @return 默认返回 [R.layout.zxl_camera_scan]
     */
    override fun getLayoutId(): Int {
        return R.layout.zxl_camera_scan
    }

    /**
     * [viewfinderView] 的 ID。
     *
     * @return 默认返回 [R.id.viewfinderView]；如果不需要扫码框可返回 [View.NO_ID]
     */
    @IdRes
    open fun getViewfinderViewId(): Int {
        return R.id.viewfinderView
    }
}
