package com.linversion.turntables.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import java.io.IOException

object Hash {
    const val TAG = "HashUtil"
    private val HEX_CHAR = charArrayOf(
        '0', '1', '2', '3', '4', '5',
        '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    )

    private var pixelsArray: IntArray? = null
    private var binaryArray: ByteArray? = null

    /**
     * dHash算法流程
     * 1.缩小图片，最佳大小为9*8
     * 2.转化成灰度图
     * 3.算差异值
     * 当前行像素值-前一行像素值， 从第二到第九行共8行，又因为矩阵有8列，所以得到一个8x8差分矩阵G
     * 4.计算指纹
     * 初始化输入图片的dhash = ""
     * 从左到右一行一行地遍历矩阵G每一个像素
     * 如果第i行j列元素G(i,j) >= a，则dhash += "1"
     * 如果第i行j列元素G(i,j) <a></a>， 则dhash += "0"
     */
    @Throws(IOException::class)
    fun dHash(src: Bitmap?, recycle: Boolean): String {
        val start = System.currentTimeMillis()
        val w = 8
        val h = 9
        //缩放图片
        val bitmap = scaleBitmap(src, recycle, w.toFloat(), h.toFloat())
        if (pixelsArray == null) {
            pixelsArray = IntArray(w * h)
        }
        bitmap.getPixels(pixelsArray, 0, w, 0, 0, w, h)
        bitmap.recycle()
        
        val res = calDHash(pixelsArray!!, w)
        Log.i(TAG, "dHash: 耗时" + (System.currentTimeMillis() - start))
        return res
    }

    fun aHash(src: Bitmap) : String {
        val start = System.currentTimeMillis()
        val w = 9
        val h = 9
        //缩放图片
        val bitmap = scaleBitmap(src, false, w.toFloat(), h.toFloat())
        if (pixelsArray == null) {
            pixelsArray = IntArray(w * h)
        }
        bitmap.getPixels(pixelsArray, 0, w, 0, 0, w, h)
        bitmap.recycle()
        
        val res = calAHash(pixelsArray!!, w)
        Log.i(TAG, "aHash: 耗时" + (System.currentTimeMillis() - start))
        return res
    }

    private fun calAHash(pixels: IntArray, w: Int): String {
        val average = pixels.sum() / pixels.size.toFloat()
        if (binaryArray == null) {
            binaryArray = ByteArray(w * w)
        }

        var index = 0
        for (e in pixels) {
            //转换成0 1
            binaryArray!![index++] = (if (e >= average) 1 else 0).toByte()
        }
        return bytesToHex01(binaryArray!!)
    }

    //缩放图片
    @Throws(IOException::class)
    private fun scaleBitmap(src: Bitmap?, recycle: Boolean, w: Float, h: Float): Bitmap {
        if (src == null) {
            throw IOException("invalid image")
        }
        val width = src.width
        val height = src.height
        if (width == 0 || height == 0) {
            throw IOException("invalid image")
        }
        val matrix = Matrix()
        matrix.postScale(w / width, h / height)
        val bitmap = Bitmap.createBitmap(src, 0, 0, width, height, matrix, false)
        if (recycle) {
            src.recycle()
        }
        return bitmap
    }

    /**
     * 计算差异值
     * @param pils
     * @param w
     * @param h
     * @return
     */
    private fun calDHash(pixels: IntArray, w: Int): String {
        if (binaryArray == null) {
            binaryArray = ByteArray(w * w)
        }
        var index = 0
        for (i in w until pixels.size) {
            //计算差值
            val `val` = pixels[i] - pixels[i - w]
            //转换成0 1
            binaryArray!![index++] = (if (`val` >= 0) 1 else 0).toByte()
        }
        return bytesToHex01(binaryArray!!)
    }

    private fun bytesToHex01(bytes: ByteArray): String {
        val res = StringBuilder()
        var pos = 0
        var count = 1
        for (b in bytes) {
            pos += (b * Math.pow(2.0, (4 - count).toDouble())).toInt()
            if (count == 4) {
                res.append(HEX_CHAR[pos])
                pos = 0
                count = 1
            } else {
                count++
            }
        }
        return res.toString()
    }
}