package com.linversion.turntables.util

import android.graphics.PixelFormat
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

object RecorderFakeUtils {
    private const val TAG = "RecorderFakeUtils"
    const val ROM_MIUI = "MIUI"
    const val ROM_EMUI = "EMUI"
    const val ROM_FLYME = "FLYME"
    const val ROM_OPPO = "OPPO"
    const val ROM_SMARTISAN = "SMARTISAN"
    const val ROM_VIVO = "VIVO"
    const val ROM_QIKU = "QIKU"
    private const val KEY_VERSION_MIUI = "ro.miui.ui.version.name"
    private const val KEY_VERSION_EMUI = "ro.build.version.emui"
    private const val KEY_VERSION_OPPO = "ro.build.version.opporom"
    private const val KEY_VERSION_SMARTISAN = "ro.smartisan.version"
    private const val KEY_VERSION_VIVO = "ro.vivo.os.version"
    private var sName: String? = null
    private var sVersion: String? = null

    //华为
    val isEmui: Boolean
        get() = check(ROM_EMUI)

    //小米
    val isMiui: Boolean
        get() = check(ROM_MIUI)

    //vivo
    val isVivo: Boolean
        get() = check(ROM_VIVO)

    //oppo
    val isOppo: Boolean
        get() = check(ROM_OPPO)

    //魅族
    val isFlyme: Boolean
        get() = check(ROM_FLYME)

    //360手机
    fun is360(): Boolean {
        return check(ROM_QIKU) || check("360")
    }

    val isSmartisan: Boolean
        get() = check(ROM_SMARTISAN)
    val name: String?
        get() {
            if (sName == null) {
                check("")
            }
            return sName
        }
    val version: String?
        get() {
            if (sVersion == null) {
                check("")
            }
            return sVersion
        }//不要问我为啥写成这样，因为小米自带的截屏软件 就是写的这个鬼标识

    //悬浮框在布局的位置
    //悬浮窗的宽，不指定则无法滑动
    //悬浮窗的高，不指定则无法滑动
    //初始位置的x坐标
    //初始位置的y坐标
    val fakeRecorderWindowLayoutParams: WindowManager.LayoutParams
        get() {
            val layoutParams = WindowManager.LayoutParams()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isMiui) {
                    layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE
                } else {
                    layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                }
            } else {
                layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE
            }
            layoutParams.format = PixelFormat.RGBA_8888
            layoutParams.flags = (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP //悬浮框在布局的位置
            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT //悬浮窗的宽，不指定则无法滑动
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT //悬浮窗的高，不指定则无法滑动
            layoutParams.x = 0 //初始位置的x坐标
            layoutParams.y = 0 //初始位置的y坐标
            layoutParams.title = fakeRecordWindowTitle
            if (check(ROM_FLYME)) {
                setMeizuParams(layoutParams, 0x2000)
            }
            if (check(ROM_MIUI)) {
                //不要问我为啥写成这样，因为小米自带的截屏软件 就是写的这个鬼标识
                layoutParams.flags = 4136
            }
            return layoutParams
        }

    /**
     * 这丫的自定义了特殊的参数
     * @param params
     * @param flagValue
     */
    private fun setMeizuParams(params: WindowManager.LayoutParams, flagValue: Int) {
        try {
            val MeizuParamsClass = Class.forName("android.view.MeizuLayoutParams")
            val flagField = MeizuParamsClass.getDeclaredField("flags")
            flagField.isAccessible = true
            val MeizuParams = MeizuParamsClass.newInstance()
            flagField.setInt(MeizuParams, flagValue)
            val mzParamsField = params.javaClass.getField("meizuParams")
            mzParamsField[params] = MeizuParams
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }//oppo手机的悬浮框截屏，每次都需要弹出授权框，我去，这就很尴尬了

    /**
     * 总结下可以操作的是华为，魅族，oppo
     * @return
     */
    val fakeRecordWindowTitle: String
        get() {
            if (sName == null) {
                check("")
            }
            if (sName == null) {
                return ""
            }
            when (sName) {
                ROM_MIUI -> return "com.miui.screenrecorder"
                ROM_EMUI -> return "ScreenRecoderTimer"
                ROM_OPPO ->                 //oppo手机的悬浮框截屏，每次都需要弹出授权框，我去，这就很尴尬了
                    return "com.coloros.screenrecorder.FloatView"
                ROM_VIVO -> return "screen_record_menu"
                ROM_SMARTISAN -> {
                }
                ROM_FLYME -> return "SysScreenRecorder"
            }
            return ""
        }

    fun check(rom: String): Boolean {
        if (sName != null) {
            return sName == rom
        }
        if (!TextUtils.isEmpty(getProp(KEY_VERSION_MIUI).also { sVersion = it })) {
            sName = ROM_MIUI
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_EMUI).also { sVersion = it })) {
            sName = ROM_EMUI
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_OPPO).also { sVersion = it })) {
            sName = ROM_OPPO
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_VIVO).also { sVersion = it })) {
            sName = ROM_VIVO
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_SMARTISAN).also { sVersion = it })) {
            sName = ROM_SMARTISAN
        } else {
            sVersion = Build.DISPLAY
            if (sVersion!!.toUpperCase(Locale.ROOT).contains(ROM_FLYME)) {
                sName = ROM_FLYME
            } else {
                sVersion = Build.UNKNOWN
                sName = Build.MANUFACTURER.toUpperCase(Locale.ROOT)
            }
        }
        return sName == rom
    }

    fun getProp(name: String): String? {
        var line: String? = null
        var input: BufferedReader? = null
        try {
            val p = Runtime.getRuntime().exec("getprop $name")
            input = BufferedReader(InputStreamReader(p.inputStream), 1024)
            line = input.readLine()
            input.close()
        } catch (ex: IOException) {
            Log.d(TAG, "Unable to read prop $name$ex")
            return null
        } finally {
            if (input != null) {
                try {
                    input.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return line
    }
}