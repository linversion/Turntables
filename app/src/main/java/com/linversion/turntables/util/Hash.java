package com.linversion.turntables.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.Log;

import java.io.IOException;

public class Hash {
    public static final String TAG = "HashUtil";
    private static final char[] HEX_CHAR = {'0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final String[] binaryArray = {
            "0000", "0001", "0010", "0011",
            "0100", "0101", "0110", "0111",
            "1000", "1001", "1010", "1011",
            "1100", "1101", "1110", "1111"
    };
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
    public static long dctImageHash(Bitmap src, boolean recycle) throws IOException {
        long start = System.currentTimeMillis();
        //由于计算dct需要图片长宽相等，所以统一取32
        int length = 32;

        //缩放图片
        Bitmap bitmap = scaleBitmap(src, recycle, length, length);

        //获取灰度图
        int[] pixels = createGrayImage(bitmap, length, length);

        //先获得32*32的dct，再取dct左上角8*8的区域
        long res = computeHash(DCT8(pixels, length));
        long dur = System.currentTimeMillis() - start;
        Log.i(TAG, "dctImageHash: 计算Hash耗时" + dur);
        Log.i(TAG, "dctImageHash: hash=" + res);

        return res;
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
     * 如果第i行j列元素G(i,j) <a， 则dhash += "0"
     */
    public static String dHash(Bitmap src, boolean recycle) throws IOException {
        long start = System.currentTimeMillis();
        int w = 8;
        int h = 9;
        //缩放图片
        Bitmap bitmap = scaleBitmap(src, recycle, w, h);
        //获取灰度图
        int[] pixels = createGrayImage(bitmap, w, h);
        String res = calDHash(pixels, w);
        Log.i(TAG, "dHash: 耗时" + (System.currentTimeMillis() - start));
        return  res;
    }

    private static int[] createGrayImage(Bitmap src, int w, int h) {
        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);
        src.recycle();
        for (int i = 0; i < pixels.length; i++) {
            int gray = computeGray(pixels[i]);
            pixels[i] = Color.rgb(gray, gray, gray);
        }
        return pixels;
    }

    //缩放图片
    private static Bitmap scaleBitmap(Bitmap src, boolean recycle, float w, float h) throws IOException {
        if (src == null) {
            throw new IOException("invalid image");
        }
        int width = src.getWidth();
        int height = src.getHeight();
        if (width == 0 || height == 0) {
            throw new IOException("invalid image");
        }
        Matrix matrix = new Matrix();
        matrix.postScale(w / width, h / height);
        Bitmap bitmap = Bitmap.createBitmap(src, 0, 0, width, height, matrix, false);
        if (recycle) {
            src.recycle();
        }
        return bitmap;
    }

    //计算hash值
    private static long computeHash(double[] pxs) {
        double t = 0;
        for (double i : pxs) {
            t += i;
        }
        double median = t / pxs.length;
        long one = 0x0000000000000001;
        long hash = 0x0000000000000000;
        for (double current : pxs) {
            if (current > median)
                hash |= one;
            one = one << 1;
        }
        return hash;
    }

    /**
     *计算灰度值
     * 计算公式Gray = R*0.299 + G*0.587 + B*0.114
     * 由于浮点数运算性能较低，转换成位移运算
     * 向右每位移一位，相当于除以2
     *
     */
    private static int computeGray(int pixel) {
        int red = Color.red(pixel);
        int green = Color.green(pixel);
        int blue = Color.blue(pixel);
        return (red * 38 + green * 75 + blue * 15) >> 7;
    }

    //取dct图左上角8*8的区域
    private static double[] DCT8(int[] pix, int n) {
        double[][] iMatrix = DCT(pix, n);

        double px[] = new double[8 * 8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(iMatrix[i], 0, px, i * 8, 8);
        }
        return px;
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
    private static double[][] DCT(int[] pix, int n) {
        double[][] iMatrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                iMatrix[i][j] = (double) (pix[i * n + j]);
            }
        }
        double[][] quotient = coefficient(n);   //求系数矩阵
        double[][] quotientT = transposingMatrix(quotient, n);  //转置系数矩阵

        double[][] temp;
        temp = matrixMultiply(quotient, iMatrix, n);
        iMatrix = matrixMultiply(temp, quotientT, n);
        return iMatrix;
    }

    /**
     * 矩阵转置
     *
     * @param matrix 原矩阵
     * @param n      矩阵(n*n)
     * @return 转置后的矩阵
     */
    private static double[][] transposingMatrix(double[][] matrix, int n) {
        double nMatrix[][] = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                nMatrix[i][j] = matrix[j][i];
            }
        }
        return nMatrix;
    }

    /**
     * 求离散余弦变换的系数矩阵
     *
     * @param n n*n矩阵的大小
     * @return 系数矩阵
     */
    private static double[][] coefficient(int n) {
        double[][] coeff = new double[n][n];
        double sqrt = Math.sqrt(1.0 / n);
        double sqrt1 = Math.sqrt(2.0 / n);
        for (int i = 0; i < n; i++) {
            coeff[0][i] = sqrt;
        }
        for (int i = 1; i < n; i++) {
            for (int j = 0; j < n; j++) {
                coeff[i][j] = sqrt1 * Math.cos(i * Math.PI * (j + 0.5) / n);
            }
        }

        return coeff;
    }

    /**
     * 矩阵相乘
     *
     * @param A 矩阵A
     * @param B 矩阵B
     * @param n 矩阵的大小n*n
     * @return 结果矩阵
     */
    private static double[][] matrixMultiply(double[][] A, double[][] B, int n) {
        double nMatrix[][] = new double[n][n];
        double t;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                t = 0;
                for (int k = 0; k < n; k++) {
                    t += A[i][k] * B[k][j];
                }
                nMatrix[i][j] = t;
            }
        }
        return nMatrix;
    }

    /**
     * 计算差异值
     * @param pils
     * @param w
     * @param h
     * @return
     */
    public static String calDHash(int[] pixels, int w) {
        byte[] d = new byte[w * w];
        int index = 0;
        for (int i = w; i < pixels.length; i++) {
            //计算差值
            int val = pixels[i] - pixels[i - w];
            //转换成0 1
            d[index++] = (byte) (val >= 0 ? 1 : 0);
        }
        return bytesToHex01(d);
    }

    public static String bytesToHex01(byte[] bytes) {
        StringBuilder res = new StringBuilder();
        int pos = 0;
        int count = 1;
        for (byte b : bytes) {
            pos += b * (Math.pow(2, 4 - count));
            if (count == 4) {
                res.append(HEX_CHAR[pos]);
                pos = 0;
                count =1;
            } else {
                count++;
            }
        }
        return res.toString();
    }

    public static String bytes2hex03(byte[] bytes) {
        final String HEX = "0123456789abcdef";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            // 取出这个字节的高4位，然后与0x0f与运算，得到一个0-15之间的数据，通过HEX.charAt(0-15)即为16进制数
            sb.append(HEX.charAt((b >> 4) & 0x0f));
            // 取出这个字节的低位，与0x0f与运算，得到一个0-15之间的数据，通过HEX.charAt(0-15)即为16进制数
            sb.append(HEX.charAt(b & 0x0f));
        }
        return sb.toString();
    }

    /**
     * 计算两个图片指纹的汉明距离
     *
     * @param hash1 指纹1
     * @param hash2 指纹2
     * @return 返回汉明距离 也就是64位long型不相同的位的个数
     */
    public static int hammingDistance(long hash1, long hash2) {
        long x = hash1 ^ hash2;
        final long m1 = 0x5555555555555555L;
        final long m2 = 0x3333333333333333L;
        final long h01 = 0x0101010101010101L;
        final long m4 = 0x0f0f0f0f0f0f0f0fL;
        x -= (x >> 1) & m1;
        x = (x & m2) + ((x >> 2) & m2);
        x = (x + (x >> 4)) & m4;
        return (int) ((x * h01) >> 56);
    }


}
