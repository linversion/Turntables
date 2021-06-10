package com.linversion.turntables.util;

import android.graphics.PixelFormat;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;

public class RecorderFakeUtils {

    private static final String TAG = "RecorderFakeUtils";

    public static final String ROM_MIUI = "MIUI";
    public static final String ROM_EMUI = "EMUI";
    public static final String ROM_FLYME = "FLYME";
    public static final String ROM_OPPO = "OPPO";
    public static final String ROM_SMARTISAN = "SMARTISAN";
    public static final String ROM_VIVO = "VIVO";
    public static final String ROM_QIKU = "QIKU";

    private static final String KEY_VERSION_MIUI = "ro.miui.ui.version.name";
    private static final String KEY_VERSION_EMUI = "ro.build.version.emui";
    private static final String KEY_VERSION_OPPO = "ro.build.version.opporom";
    private static final String KEY_VERSION_SMARTISAN = "ro.smartisan.version";
    private static final String KEY_VERSION_VIVO = "ro.vivo.os.version";

    private static String sName;
    private static String sVersion;

    //华为
    public static boolean isEmui() {
        return check(ROM_EMUI);
    }
    //小米
    public static boolean isMiui() {
        return check(ROM_MIUI);
    }
    //vivo
    public static boolean isVivo() {
        return check(ROM_VIVO);
    }
    //oppo
    public static boolean isOppo() {
        return check(ROM_OPPO);
    }
    //魅族
    public static boolean isFlyme() {
        return check(ROM_FLYME);
    }
    //360手机
    public static boolean is360() {
        return check(ROM_QIKU) || check("360");
    }

    public static boolean isSmartisan() {
        return check(ROM_SMARTISAN);
    }

    public static String getName() {
        if (sName == null) {
            check("");
        }
        return sName;
    }

    public static String getVersion() {
        if (sVersion == null) {
            check("");
        }
        return sVersion;
    }

    public static WindowManager.LayoutParams getFakeRecorderWindowLayoutParams() {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isMiui()) {
                layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
            } else {
                layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            }

        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;//悬浮框在布局的位置
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;//悬浮窗的宽，不指定则无法滑动
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;//悬浮窗的高，不指定则无法滑动
        layoutParams.x = 0; //初始位置的x坐标
        layoutParams.y = 0; //初始位置的y坐标
        layoutParams.setTitle(getFakeRecordWindowTitle());
        if (check(ROM_FLYME)) {
            setMeizuParams(layoutParams, 0x2000);
        }
        if (check(ROM_MIUI)) {
            //不要问我为啥写成这样，因为小米自带的截屏软件 就是写的这个鬼标识
            layoutParams.flags = 4136;
        }
        return layoutParams;
    }

    /**
     * 这丫的自定义了特殊的参数
     * @param params
     * @param flagValue
     */
    private static void setMeizuParams(WindowManager.LayoutParams params, int flagValue) {
        try {
            Class MeizuParamsClass = Class.forName("android.view.MeizuLayoutParams");
            Field flagField = MeizuParamsClass.getDeclaredField("flags");
            flagField.setAccessible(true);
            Object MeizuParams = MeizuParamsClass.newInstance();
            flagField.setInt(MeizuParams, flagValue);

            Field mzParamsField = params.getClass().getField("meizuParams");
            mzParamsField.set(params, MeizuParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 总结下可以操作的是华为，魅族，oppo
     * @return
     */
    public static String getFakeRecordWindowTitle() {
        if (sName == null) {
            check("");
        }
        if (sName == null) {
            return "";
        }
        switch (sName) {
            case ROM_MIUI:
                return "com.miui.screenrecorder";
            case ROM_EMUI:
                return "ScreenRecoderTimer";
            case ROM_OPPO:
                //oppo手机的悬浮框截屏，每次都需要弹出授权框，我去，这就很尴尬了
                return "com.coloros.screenrecorder.FloatView";
            case ROM_VIVO:
                return "screen_record_menu";
            case ROM_SMARTISAN:
                break;
            case ROM_FLYME:
                return "SysScreenRecorder";
        }
        return "";
    }

    public static boolean check(String rom) {
        if (sName != null) {
            return sName.equals(rom);
        }

        if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_MIUI))) {
            sName = ROM_MIUI;
        } else if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_EMUI))) {
            sName = ROM_EMUI;
        } else if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_OPPO))) {
            sName = ROM_OPPO;
        } else if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_VIVO))) {
            sName = ROM_VIVO;
        } else if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_SMARTISAN))) {
            sName = ROM_SMARTISAN;
        } else {
            sVersion = Build.DISPLAY;
            if (sVersion.toUpperCase().contains(ROM_FLYME)) {
                sName = ROM_FLYME;
            } else {
                sVersion = Build.UNKNOWN;
                sName = Build.MANUFACTURER.toUpperCase();
            }
        }
        return sName.equals(rom);
    }

    public static String getProp(String name) {
        String line = null;
        BufferedReader input = null;
        try {
            Process p = Runtime.getRuntime().exec("getprop " + name);
            input = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);
            line = input.readLine();
            input.close();
        } catch (IOException ex) {
            Log.d(TAG, "Unable to read prop " + name + ex);
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return line;
    }

}
