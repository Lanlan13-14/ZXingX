package com.king.zxing

import android.graphics.Rect
import androidx.annotation.FloatRange
import com.google.zxing.DecodeHintType

/**
 * 解码配置：主要用于在扫码识别时，提供一些配置，便于扩展。通过配置可决定内置分析器的能力，从而间接的控制并简化扫码识别的流程
 * <p>
 * 设置解码 [setHints]内置的一些解码可参见如下：
 * <p>
 * [DecodeFormatManager.DEFAULT_HINTS]
 * [DecodeFormatManager.ALL_HINTS]
 * [DecodeFormatManager.CODE_128_HINTS]
 * [DecodeFormatManager.QR_CODE_HINTS]
 * [DecodeFormatManager.ONE_DIMENSIONAL_HINTS]
 * [DecodeFormatManager.TWO_DIMENSIONAL_HINTS]
 * [DecodeFormatManager.DEFAULT_HINTS]
 * <p>
 *
 * <p>
 *
 * 如果不满足您也可以通过[DecodeFormatManager.createDecodeHints]自己配置支持的格式
 * <p>
 * 识别区域可设置的方式有如下几种：
 * [setFullAreaScan] 设置是否支持全区域扫码识别，优先级比识别区域高
 * [setAnalyzeAreaRect] 设置需要分析识别区域，优先级比识别区域比例高，当设置了指定的分析区域时，识别区域比例和识别区域偏移量相关参数都将无效
 * [setAreaRectRatio] 设置识别区域比例，默认[DEFAULT_AREA_RECT_RATIO]，设置的比例最终会基于分析图像帧上裁减出此比例的一个矩形进行扫码识别，优先级最低
 * <p>
 * 以上几种识别区域都是基于[androidx.camera.core.ImageAnalysis] 配置的分析目标分辨率作为参照的；请注意区分 [androidx.camera.core.Preview] 与 [androidx.camera.core.ImageAnalysis]配置的区别。
 * <p>
 * 即判定区域分析的优先级顺序为:[setFullAreaScan] -> [setAnalyzeAreaRect] -> [setAreaRectRatio]
 *
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 * <p>
 * <a href="https://github.com/jenly1314">Follow me</a>
 */
open class DecodeConfig {

    private var hints: Map<DecodeHintType, Any>? = DecodeFormatManager.DEFAULT_HINTS

    /**
     * 是否支持使用多解码。
     */
    private var isMultiDecode = true

    /**
     * 是否支持识别反色码（条码黑白颜色反转的码）。
     */
    private var isSupportLuminanceInvert = false

    /**
     * 是否支持识别反色码（条码黑白颜色反转的码）并使用多解码。
     */
    private var isSupportLuminanceInvertMultiDecode = false

    /**
     * 是否支持垂直的条码。
     */
    private var isSupportVerticalCode = false

    /**
     * 是否支持垂直的条码并使用多解码。
     */
    private var isSupportVerticalCodeMultiDecode = false

    /**
     * 需要分析识别区域。
     */
    private var analyzeAreaRect: Rect? = null

    /**
     * 是否支持全区域扫码识别。
     */
    private var isFullAreaScan = false

    /**
     * 识别区域比例，默认 [DEFAULT_AREA_RECT_RATIO]。
     */
    private var areaRectRatio = DEFAULT_AREA_RECT_RATIO

    /**
     * 识别区域垂直方向偏移量。
     */
    private var areaRectVerticalOffset = 0

    /**
     * 识别区域水平方向偏移量。
     */
    private var areaRectHorizontalOffset = 0

    /**
     * 获取配置的解码支持类型 [DecodeHintType]。
     *
     * @see DecodeFormatManager
     */
    fun getHints(): Map<DecodeHintType, Any>? {
        return hints
    }

    /**
     * 设置解码 Hint。
     *
     * 内置的一些解码配置可参见：
     * [DecodeFormatManager.DEFAULT_HINTS]
     * [DecodeFormatManager.ALL_HINTS]
     * [DecodeFormatManager.CODE_128_HINTS]
     * [DecodeFormatManager.QR_CODE_HINTS]
     * [DecodeFormatManager.ONE_DIMENSIONAL_HINTS]
     * [DecodeFormatManager.TWO_DIMENSIONAL_HINTS]
     *
     * 如果不满足需求，也可以通过 [DecodeFormatManager.createDecodeHints] 自行配置支持的格式。
     */
    fun setHints(hints: Map<DecodeHintType, Any>?): DecodeConfig {
        this.hints = hints
        return this
    }

    /**
     * 是否支持识别反色码（黑白颜色反转）。
     */
    fun isSupportLuminanceInvert(): Boolean {
        return isSupportLuminanceInvert
    }

    /**
     * 设置是否支持识别反色码（黑白颜色反转）。
     *
     * @param supportLuminanceInvert 默认为 `false`。启用后可增强反色码识别能力，但会增加性能消耗。
     */
    fun setSupportLuminanceInvert(supportLuminanceInvert: Boolean): DecodeConfig {
        isSupportLuminanceInvert = supportLuminanceInvert
        return this
    }

    /**
     * 是否支持扫垂直的条码。
     */
    fun isSupportVerticalCode(): Boolean {
        return isSupportVerticalCode
    }

    /**
     * 设置是否支持扫垂直的条码。
     *
     * @param supportVerticalCode 默认为 `false`。启用后可增强垂直条码识别能力，但会增加性能消耗。
     */
    fun setSupportVerticalCode(supportVerticalCode: Boolean): DecodeConfig {
        isSupportVerticalCode = supportVerticalCode
        return this
    }

    /**
     * 是否支持使用多解码。
     */
    fun isMultiDecode(): Boolean {
        return isMultiDecode
    }

    /**
     * 设置是否支持使用多解码。
     *
     * @param multiDecode 默认为 `true`
     * @see com.google.zxing.common.HybridBinarizer
     * @see com.google.zxing.common.GlobalHistogramBinarizer
     */
    fun setMultiDecode(multiDecode: Boolean): DecodeConfig {
        isMultiDecode = multiDecode
        return this
    }

    /**
     * 是否支持识别反色码（条码黑白颜色反转的码）并使用多解码。
     */
    fun isSupportLuminanceInvertMultiDecode(): Boolean {
        return isSupportLuminanceInvertMultiDecode
    }

    /**
     * 设置是否支持识别反色码（条码黑白颜色反转的码）并使用多解码。
     *
     * @param supportLuminanceInvertMultiDecode 默认为 `false`。启用后可增强反色码识别能力，但会增加性能消耗。
     * @see com.google.zxing.common.HybridBinarizer
     * @see com.google.zxing.common.GlobalHistogramBinarizer
     */
    fun setSupportLuminanceInvertMultiDecode(supportLuminanceInvertMultiDecode: Boolean): DecodeConfig {
        isSupportLuminanceInvertMultiDecode = supportLuminanceInvertMultiDecode
        return this
    }

    /**
     * 是否支持垂直的条码并使用多解码。
     */
    fun isSupportVerticalCodeMultiDecode(): Boolean {
        return isSupportVerticalCodeMultiDecode
    }

    /**
     * 设置是否支持垂直的条码并使用多解码。
     *
     * 解码时会使用不同二值化策略组合来增强识别能力。
     *
     * @param supportVerticalCodeMultiDecode 默认为 `false`。启用后可增强垂直条码识别能力，但会增加性能消耗。
     */
    fun setSupportVerticalCodeMultiDecode(supportVerticalCodeMultiDecode: Boolean): DecodeConfig {
        isSupportVerticalCodeMultiDecode = supportVerticalCodeMultiDecode
        return this
    }

    /**
     * 获取需要分析识别的区域。
     */
    fun getAnalyzeAreaRect(): Rect? {
        return analyzeAreaRect
    }

    /**
     * 设置需要分析识别区域。
     *
     * 优先级比识别区域比例高，当设置了指定分析区域时，识别区域比例和识别区域偏移量相关参数都将无效。
     * 由于此方式设置的是绝对位置，存在明显局限性，请谨慎使用（主要预留给特殊需求）。
     *
     * 识别区域设置优先级：
     * [setFullAreaScan] -> [setAnalyzeAreaRect] -> [setAreaRectRatio]
     */
    fun setAnalyzeAreaRect(analyzeAreaRect: Rect?): DecodeConfig {
        this.analyzeAreaRect = analyzeAreaRect
        return this
    }

    /**
     * 是否支持全区域扫码识别。
     */
    fun isFullAreaScan(): Boolean {
        return isFullAreaScan
    }

    /**
     * 设置是否支持全区域扫码识别，优先级高于识别区域相关配置。
     *
     * @param fullAreaScan 默认为 `false`
     */
    fun setFullAreaScan(fullAreaScan: Boolean): DecodeConfig {
        isFullAreaScan = fullAreaScan
        return this
    }

    /**
     * 获取识别区域比例，默认 [DEFAULT_AREA_RECT_RATIO]。
     */
    fun getAreaRectRatio(): Float {
        return areaRectRatio
    }

    /**
     * 设置识别区域比例，默认 [DEFAULT_AREA_RECT_RATIO]。
     *
     * 设置的比例最终会基于分析图像帧上裁减出此比例的一个矩形进行扫码识别，优先级最低。
     * 与 [setAreaRectHorizontalOffset] 和 [setAreaRectVerticalOffset] 结合使用可控制识别区域位置。
     */
    fun setAreaRectRatio(@FloatRange(from = 0.5, to = 1.0) areaRectRatio: Float): DecodeConfig {
        this.areaRectRatio = areaRectRatio
        return this
    }

    /**
     * 获取识别区域垂直方向偏移量，支持负数。
     * 大于 0 时，居中向下偏移；小于 0 时，居中向上偏移。
     */
    fun getAreaRectVerticalOffset(): Int {
        return areaRectVerticalOffset
    }

    /**
     * 设置识别区域垂直方向偏移量，支持负数。
     * 大于 0 时，居中向下偏移；小于 0 时，居中向上偏移。
     */
    fun setAreaRectVerticalOffset(areaRectVerticalOffset: Int): DecodeConfig {
        this.areaRectVerticalOffset = areaRectVerticalOffset
        return this
    }

    /**
     * 获取识别区域水平方向偏移量，支持负数。
     * 大于 0 时，居中向右偏移；小于 0 时，居中向左偏移。
     */
    fun getAreaRectHorizontalOffset(): Int {
        return areaRectHorizontalOffset
    }

    /**
     * 设置识别区域水平方向偏移量，支持负数。
     * 大于 0 时，居中向右偏移；小于 0 时，居中向左偏移。
     */
    fun setAreaRectHorizontalOffset(areaRectHorizontalOffset: Int): DecodeConfig {
        this.areaRectHorizontalOffset = areaRectHorizontalOffset
        return this
    }

    override fun toString(): String {
        return "DecodeConfig{" +
            "hints=$hints" +
            ", isMultiDecode=$isMultiDecode" +
            ", isSupportLuminanceInvert=$isSupportLuminanceInvert" +
            ", isSupportLuminanceInvertMultiDecode=$isSupportLuminanceInvertMultiDecode" +
            ", isSupportVerticalCode=$isSupportVerticalCode" +
            ", isSupportVerticalCodeMultiDecode=$isSupportVerticalCodeMultiDecode" +
            ", analyzeAreaRect=$analyzeAreaRect" +
            ", isFullAreaScan=$isFullAreaScan" +
            ", areaRectRatio=$areaRectRatio" +
            ", areaRectVerticalOffset=$areaRectVerticalOffset" +
            ", areaRectHorizontalOffset=$areaRectHorizontalOffset" +
            '}'
    }

    companion object {
        /**
         * 默认识别区域比例。
         */
        const val DEFAULT_AREA_RECT_RATIO = 0.8f
    }
}
