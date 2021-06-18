package com.linversion.turntables.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import java.io.IOException

object Hash {
    const val TAG = "HashUtil"
    private val HEX_CHAR = charArrayOf(
        '0', '1', '2', '3', '4', '5',
        '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    )
    private val binaryArray = arrayOf(
        "0000", "0001", "0010", "0011",
        "0100", "0101", "0110", "0111",
        "1000", "1001", "1010", "1011",
        "1100", "1101", "1110", "1111"
    )

    /**
     * pHash算法流程
     * 1.缩小图片，最佳大小为32*32
     * 2.转化成灰度图
     * 3.转化为DCT图
     * 4.取dct图左上角8*8的范围
     * 5.计算所有点的平均值
     * 6.8*8的范围刚好64个点，计算出64位的图片指纹，如果小于平均值记为0，反之记为1，指纹顺序可以随机，但是每张图片的指纹的顺序应该保持一致
     * 7.最后比较两张图片指纹的汉明距离，越小表示越相识
     *
     */
    //获取指纹，long刚好64位，方便存放
    @Throws(IOException::class)
    fun dctImageHash(src: Bitmap?, recycle: Boolean): Long {
        val start = System.currentTimeMillis()
        //由于计算dct需要图片长宽相等，所以统一取32
        val length = 32

        //缩放图片
        val bitmap = scaleBitmap(src, recycle, length.toFloat(), length.toFloat())

        //获取灰度图
        val pixels = createGrayImage(bitmap, length, length)

        //先获得32*32的dct，再取dct左上角8*8的区域
        val res = computeHash(DCT8(pixels, length))
        val dur = System.currentTimeMillis() - start
        Log.i(TAG, "dctImageHash: 计算Hash耗时$dur")
        Log.i(TAG, "dctImageHash: hash=$res")
        return res
    }

    /**
     * pHash算法流程
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
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.recycle()
        
        val res = calDHash(pixels, w)
        Log.i(TAG, "dHash: 耗时" + (System.currentTimeMillis() - start))
        return res
    }

    fun aHash(src: Bitmap) : String {
        val start = System.currentTimeMillis()
        val w = 8
        val h = 8
        //缩放图片
        val bitmap = scaleBitmap(src, true, w.toFloat(), h.toFloat())
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.recycle()
        
        val res = calAHash(pixels, w)
        Log.i(TAG, "dHash: 耗时" + (System.currentTimeMillis() - start))
        return res
    }

    private fun calAHash(pixels: IntArray, w: Int): String {
        val average = pixels.sum() / pixels.size.toFloat()
        val d = ByteArray(w * w)
        var index = 0
        for (e in pixels) {
            //转换成0 1
            d[index++] = (if (e >= average) 1 else 0).toByte()
        }
        return bytesToHex01(d)
    }

    private fun createGrayImage(src: Bitmap, w: Int, h: Int): IntArray {
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        src.recycle()
        for (i in pixels.indices) {
            val gray = computeGray(pixels[i])
            pixels[i] = Color.rgb(gray, gray, gray)
        }
        return pixels
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

    //计算hash值
    private fun computeHash(pxs: DoubleArray): Long {
        var t = 0.0
        for (i in pxs) {
            t += i
        }
        val median = t / pxs.size
        var one: Long = 0x0000000000000001
        var hash: Long = 0x0000000000000000
        for (current in pxs) {
            if (current > median) hash = hash or one
            one = one shl 1
        }
        return hash
    }

    /**
     * 计算灰度值
     * 计算公式Gray = R*0.299 + G*0.587 + B*0.114
     * 由于浮点数运算性能较低，转换成位移运算
     * 向右每位移一位，相当于除以2
     *
     */
    private fun computeGray(pixel: Int): Int {
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        return red * 38 + green * 75 + blue * 15 shr 7
    }

    //取dct图左上角8*8的区域
    private fun DCT8(pix: IntArray, n: Int): DoubleArray {
        val iMatrix = DCT(pix, n)
        val px = DoubleArray(8 * 8)
        for (i in 0..7) {
            System.arraycopy(iMatrix[i], 0, px, i * 8, 8)
        }
        return px
    }

    /**
     * 离散余弦变换
     *
     * 计算公式为：系数矩阵*图片矩阵*转置系数矩阵
     *
     * @param pix 原图像的数据矩阵
     * @param n   原图像(n*n)
     * @return 变换后的矩阵数组
     */
    private fun DCT(pix: IntArray, n: Int): Array<DoubleArray> {
        var iMatrix = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                iMatrix[i][j] = pix[i * n + j].toDouble()
            }
        }
        val quotient = coefficient(n) //求系数矩阵
        val quotientT = transposingMatrix(quotient, n) //转置系数矩阵
        val temp: Array<DoubleArray>
        temp = matrixMultiply(quotient, iMatrix, n)
        iMatrix = matrixMultiply(temp, quotientT, n)
        return iMatrix
    }

    /**
     * 矩阵转置
     *
     * @param matrix 原矩阵
     * @param n      矩阵(n*n)
     * @return 转置后的矩阵
     */
    private fun transposingMatrix(matrix: Array<DoubleArray>, n: Int): Array<DoubleArray> {
        val nMatrix = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                nMatrix[i][j] = matrix[j][i]
            }
        }
        return nMatrix
    }

    /**
     * 求离散余弦变换的系数矩阵
     *
     * @param n n*n矩阵的大小
     * @return 系数矩阵
     */
    private fun coefficient(n: Int): Array<DoubleArray> {
        val coeff = Array(n) { DoubleArray(n) }
        val sqrt = Math.sqrt(1.0 / n)
        val sqrt1 = Math.sqrt(2.0 / n)
        for (i in 0 until n) {
            coeff[0][i] = sqrt
        }
        for (i in 1 until n) {
            for (j in 0 until n) {
                coeff[i][j] = sqrt1 * Math.cos(i * Math.PI * (j + 0.5) / n)
            }
        }
        return coeff
    }

    /**
     * 矩阵相乘
     *
     * @param A 矩阵A
     * @param B 矩阵B
     * @param n 矩阵的大小n*n
     * @return 结果矩阵
     */
    private fun matrixMultiply(
        A: Array<DoubleArray>,
        B: Array<DoubleArray>,
        n: Int
    ): Array<DoubleArray> {
        val nMatrix = Array(n) { DoubleArray(n) }
        var t: Double
        for (i in 0 until n) {
            for (j in 0 until n) {
                t = 0.0
                for (k in 0 until n) {
                    t += A[i][k] * B[k][j]
                }
                nMatrix[i][j] = t
            }
        }
        return nMatrix
    }

    /**
     * 计算差异值
     * @param pils
     * @param w
     * @param h
     * @return
     */
    fun calDHash(pixels: IntArray, w: Int): String {
        val d = ByteArray(w * w)
        var index = 0
        for (i in w until pixels.size) {
            //计算差值
            val `val` = pixels[i] - pixels[i - w]
            //转换成0 1
            d[index++] = (if (`val` >= 0) 1 else 0).toByte()
        }
        return bytesToHex01(d)
    }

    fun bytesToHex01(bytes: ByteArray): String {
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

    /**
     * 计算两个图片指纹的汉明距离
     *
     * @param hash1 指纹1
     * @param hash2 指纹2
     * @return 返回汉明距离 也就是64位long型不相同的位的个数
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        var x = hash1 xor hash2
        val m1 = 0x5555555555555555L
        val m2 = 0x3333333333333333L
        val h01 = 0x0101010101010101L
        val m4 = 0x0f0f0f0f0f0f0f0fL
        x -= x shr 1 and m1
        x = (x and m2) + (x shr 2 and m2)
        x = x + (x shr 4) and m4
        return (x * h01 shr 56).toInt()
    }
}