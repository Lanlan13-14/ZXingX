package com.king.zxing

import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.EnumMap

/**
 * 解码格式管理器
 * <p>
 * 将常见的一些解码配置已根据条形码类型进行了几大划分，可根据需要找到符合的划分配置类型直接使用。
 *
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 * <p>
 * <a href="https://github.com/jenly1314">Follow me</a>
 */
object DecodeFormatManager {

    /**
     * 所有支持的条码类型配置。
     */
    @JvmField
    val ALL_HINTS: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)

    /**
     * `CODE_128`（常用一维码）解码配置。
     */
    @JvmField
    val CODE_128_HINTS: MutableMap<DecodeHintType, Any> = createDecodeHint(BarcodeFormat.CODE_128)

    /**
     * `QR_CODE`（常用二维码）解码配置。
     */
    @JvmField
    val QR_CODE_HINTS: MutableMap<DecodeHintType, Any> = createDecodeHint(BarcodeFormat.QR_CODE)

    /**
     * 一维码解码配置。
     */
    @JvmField
    val ONE_DIMENSIONAL_HINTS: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)

    /**
     * 二维码解码配置。
     */
    @JvmField
    val TWO_DIMENSIONAL_HINTS: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)

    /**
     * 默认解码配置。
     */
    @JvmField
    val DEFAULT_HINTS: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)

    init {
        addDecodeHintTypes(ALL_HINTS, getAllFormats())
        addDecodeHintTypes(ONE_DIMENSIONAL_HINTS, getOneDimensionalFormats())
        addDecodeHintTypes(TWO_DIMENSIONAL_HINTS, getTwoDimensionalFormats())
        addDecodeHintTypes(DEFAULT_HINTS, getDefaultFormats())
    }

    /**
     * 获取全部支持的条码格式。
     */
    private fun getAllFormats(): List<BarcodeFormat> {
        return ArrayList<BarcodeFormat>().apply {
            add(BarcodeFormat.AZTEC)
            add(BarcodeFormat.CODABAR)
            add(BarcodeFormat.CODE_39)
            add(BarcodeFormat.CODE_93)
            add(BarcodeFormat.CODE_128)
            add(BarcodeFormat.DATA_MATRIX)
            add(BarcodeFormat.EAN_8)
            add(BarcodeFormat.EAN_13)
            add(BarcodeFormat.ITF)
            add(BarcodeFormat.MAXICODE)
            add(BarcodeFormat.PDF_417)
            add(BarcodeFormat.QR_CODE)
            add(BarcodeFormat.RSS_14)
            add(BarcodeFormat.RSS_EXPANDED)
            add(BarcodeFormat.UPC_A)
            add(BarcodeFormat.UPC_E)
            add(BarcodeFormat.UPC_EAN_EXTENSION)
        }
    }

    /**
     * 获取一维码格式集合。
     *
     * 包括：
     * [BarcodeFormat.CODABAR]
     * [BarcodeFormat.CODE_39]
     * [BarcodeFormat.CODE_93]
     * [BarcodeFormat.CODE_128]
     * [BarcodeFormat.EAN_8]
     * [BarcodeFormat.EAN_13]
     * [BarcodeFormat.ITF]
     * [BarcodeFormat.RSS_14]
     * [BarcodeFormat.RSS_EXPANDED]
     * [BarcodeFormat.UPC_A]
     * [BarcodeFormat.UPC_E]
     * [BarcodeFormat.UPC_EAN_EXTENSION]
     */
    private fun getOneDimensionalFormats(): List<BarcodeFormat> {
        return ArrayList<BarcodeFormat>().apply {
            add(BarcodeFormat.CODABAR)
            add(BarcodeFormat.CODE_39)
            add(BarcodeFormat.CODE_93)
            add(BarcodeFormat.CODE_128)
            add(BarcodeFormat.EAN_8)
            add(BarcodeFormat.EAN_13)
            add(BarcodeFormat.ITF)
            add(BarcodeFormat.RSS_14)
            add(BarcodeFormat.RSS_EXPANDED)
            add(BarcodeFormat.UPC_A)
            add(BarcodeFormat.UPC_E)
            add(BarcodeFormat.UPC_EAN_EXTENSION)
        }
    }

    /**
     * 获取二维码格式集合。
     *
     * 包括：
     * [BarcodeFormat.AZTEC]
     * [BarcodeFormat.DATA_MATRIX]
     * [BarcodeFormat.MAXICODE]
     * [BarcodeFormat.PDF_417]
     * [BarcodeFormat.QR_CODE]
     */
    private fun getTwoDimensionalFormats(): List<BarcodeFormat> {
        return ArrayList<BarcodeFormat>().apply {
            add(BarcodeFormat.AZTEC)
            add(BarcodeFormat.DATA_MATRIX)
            add(BarcodeFormat.MAXICODE)
            add(BarcodeFormat.PDF_417)
            add(BarcodeFormat.QR_CODE)
        }
    }

    /**
     * 获取默认支持的格式集合。
     *
     * 包括：
     * [BarcodeFormat.QR_CODE]
     * [BarcodeFormat.UPC_A]
     * [BarcodeFormat.EAN_13]
     * [BarcodeFormat.CODE_128]
     */
    private fun getDefaultFormats(): List<BarcodeFormat> {
        return ArrayList<BarcodeFormat>().apply {
            add(BarcodeFormat.QR_CODE)
            add(BarcodeFormat.UPC_A)
            add(BarcodeFormat.EAN_13)
            add(BarcodeFormat.CODE_128)
        }
    }

    /**
     * 创建支持指定格式集合的解码配置。
     *
     * @param barcodeFormats 需要支持的 [BarcodeFormat]。
     * @return 返回添加了通用配置后的解码 Hint 配置。
     */
    @JvmStatic
    fun createDecodeHints(vararg barcodeFormats: BarcodeFormat): MutableMap<DecodeHintType, Any> {
        val hints: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)
        addDecodeHintTypes(hints, Arrays.asList(*barcodeFormats))
        return hints
    }

    /**
     * 创建支持单一格式的解码配置。
     *
     * @param barcodeFormat 需要支持的 [BarcodeFormat]。
     * @return 返回添加了通用配置后的解码 Hint 配置。
     */
    @JvmStatic
    fun createDecodeHint(barcodeFormat: BarcodeFormat): MutableMap<DecodeHintType, Any> {
        val hints: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)
        addDecodeHintTypes(hints, Collections.singletonList(barcodeFormat))
        return hints
    }

    /**
     * 为解码配置添加通用参数。
     *
     * - [DecodeHintType.POSSIBLE_FORMATS]：限制待解析格式
     * - [DecodeHintType.TRY_HARDER]：提升识别准确率
     * - [DecodeHintType.CHARACTER_SET]：字符集 `UTF-8`
     */
    private fun addDecodeHintTypes(hints: MutableMap<DecodeHintType, Any>, formats: List<BarcodeFormat>) {
        hints[DecodeHintType.POSSIBLE_FORMATS] = formats
        hints[DecodeHintType.TRY_HARDER] = true
        hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
    }
}
