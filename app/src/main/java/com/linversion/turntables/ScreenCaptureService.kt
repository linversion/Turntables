package com.linversion.turntables;

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.*
import android.widget.TextView
import com.linversion.turntables.util.Hash
import com.linversion.turntables.util.RecorderFakeUtils
import java.util.*

class ScreenCaptureService : Service() {
    private var mpManager: MediaProjectionManager? = null
    private var mMediaProjection: MediaProjection? = null
    private var mStoreDir: String? = null
    private var mImageReader: ImageReader? = null
    private var mHandler: Handler? = null

    //判断屏幕方向用
    private var mDisplay: Display? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mDensity = 0
    private var mWidth = 0
    private var mHeight = 0
    private var mRotation = 0
    private var mOrientationChangeCallback: OrientationChangeCallback? = null

    private var windowManager: WindowManager? = null
    private var windowParam: WindowManager.LayoutParams? = null

    private var viewParent: View? = null
    private var tvAction: TextView? = null
    private var tvDuration: TextView? = null
    private var tvFps: TextView? = null
    private var tvAllFrame: TextView? = null

    private var timer: Timer? = null
    private var second = 0
    private var mData: Intent? = null
    private var mCode: Int = 0
    private var isActive = false
    private var firstTime = true
    private var startTime: Long = 0

    //计算
    private var frameCount = 0
    private var bigFreezeCount = 0f
    private var lastHash: String? = null
    private var hitTime = 0

    private val handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == 0) {
                second++
                tvDuration?.text = String.format("%02d:%02d", second / 60, second % 60)
            }
        }
    }

    private inner class ImageAvailableListener : OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader) {
            var bitmap: Bitmap? = null
            try {
                mImageReader!!.acquireLatestImage()?.run {
                    if (isActive) {
                        frameCount++
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * mWidth

                        // create bitmap
                        bitmap = Bitmap.createBitmap(
                            mWidth + rowPadding / pixelStride,
                            mHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap!!.copyPixelsFromBuffer(buffer)

                        val hash = Hash.dHash(bitmap, true)
                        //记录平均1s大卡顿相关数据
                        if (hash == lastHash) {
                            //跟上次的hash相等
                            hitTime++
                        } else if (hitTime != 0) {
                            //记录到一次三帧一样
                            if (hitTime >= 2) {
                                bigFreezeCount++
                            }
                            hitTime = 0;
                        }
                        lastHash = hash
                        Log.i(TAG, "onImageAvailable: hash = $hash")
                    }
                    close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (bitmap != null) {
                    bitmap!!.recycle()
                }
            }
        }
    }

    private inner class OrientationChangeCallback internal constructor(context: Context?) :
        OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            val rotation = mDisplay!!.rotation
            if (rotation != mRotation) {
                mRotation = rotation
                try {
                    // clean up
                    if (mVirtualDisplay != null) mVirtualDisplay!!.release()
                    if (mImageReader != null) mImageReader!!.setOnImageAvailableListener(null, null)

                    // re-create virtual display depending on device width / height
                    createVirtualDisplay()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private inner class MediaProjectionStopCallback : MediaProjection.Callback() {
        override fun onStop() {
            Log.e(TAG, "stopping projection.")
            mHandler!!.post {
                if (mVirtualDisplay != null) mVirtualDisplay!!.release()
                if (mImageReader != null) mImageReader!!.setOnImageAvailableListener(null, null)
                if (mOrientationChangeCallback != null) mOrientationChangeCallback!!.disable()
                mMediaProjection!!.unregisterCallback(this@MediaProjectionStopCallback)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.i(TAG, "onBind: ")
        val notification = NotificationUtils.getNotification(this)
        startForeground(notification.first, notification.second)
        mCode = intent.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED)
        mData = intent.getParcelableExtra(DATA)

        initWindow()
        initListener()
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: ")

        // create store dir
//        val externalFilesDir = getExternalFilesDir(null)
//        if (externalFilesDir != null) {
//            mStoreDir = externalFilesDir.absolutePath + "/screenshots/"
//            val storeDirectory = File(mStoreDir!!)
//            if (!storeDirectory.exists()) {
//                val success = storeDirectory.mkdirs()
//                if (!success) {
//                    Log.e(TAG, "failed to create file storage directory.")
//                    stopSelf()
//                }
//            }
//        } else {
//            Log.e(TAG, "failed to create file storage directory, getExternalFilesDir is null.")
//            stopSelf()
//        }

        // start capture handling thread
        object : Thread() {
            override fun run() {
                Looper.prepare()
                mHandler = Handler()
                Looper.loop()
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: ")

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopProjection()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: ")
        windowManager?.removeView(viewParent)
    }

    private fun initWindow() {
        windowManager = application.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initParams()
        viewParent = LayoutInflater.from(applicationContext).inflate(R.layout.overlay_view, null)
        windowManager!!.addView(viewParent, windowParam)
    }

    private fun initParams() {
        windowParam = WindowManager.LayoutParams()
        windowParam!!.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        //设置可以显示在状态栏上
        windowParam!!.flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        //设置悬浮窗口长宽
        windowParam!!.width = WindowManager.LayoutParams.MATCH_PARENT
        windowParam!!.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowParam!!.gravity = Gravity.START or Gravity.TOP

        windowParam!!.title = RecorderFakeUtils.fakeRecordWindowTitle
        if (RecorderFakeUtils.isMiui) {
            windowParam!!.flags = 4136
        }
    }

    private fun initListener() {
        tvAction = viewParent!!.findViewById(R.id.btn_action)
        tvDuration = viewParent!!.findViewById(R.id.tv_duration)
        tvFps = viewParent!!.findViewById(R.id.tv_fps)
        tvAllFrame = viewParent!!.findViewById(R.id.tv_all_frames_count)

        tvAction!!.setOnClickListener {
            isActive = !isActive
            tvAction!!.text = if (isActive) getString(R.string.end) else getString(R.string.start)

            if (isActive && firstTime) {
                startProjection(mCode, mData)
                startTime = System.currentTimeMillis()
                firstTime = false
            } else if (isActive) {
                startTime = System.currentTimeMillis()
            } else {
                calFps()
            }
            onTimer()
        }
    }

    /**
     * 计算平均FPS
     */
    private fun calFps() {
        val dur = (System.currentTimeMillis() - startTime) / 1000
        val fps = frameCount / dur
        tvAllFrame?.text = "All Frames=$frameCount"
        frameCount = 0
        //计算平均一秒大卡顿次数
        //总大卡顿次数 / 总时间
        val freezeRate = bigFreezeCount / dur
        Log.i(TAG, "calFps: bigFreezecount$bigFreezeCount")
        tvFps?.text = "FPS=$fps/$freezeRate"
    }

    private fun initTimer() {
        timer?.let {
            it.cancel()
            it.purge()
        }
        timer = Timer()
    }

    private fun onTimer() {
        if (!isActive) {
            timer?.cancel()
            timer?.purge()
            timer = null
            tvDuration?.text = "00:00"
            second = 0
            return
        }
        initTimer()
        timer!!.schedule(object : TimerTask() {
            override fun run() {
                //update ui
                val msg = Message.obtain()
                msg.what = 0
                handler.sendMessage(msg)
            }
        }, 0, 1000)
    }

    private fun startProjection(resultCode: Int, data: Intent?) {
        if (mpManager == null) {
            mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        }
        if (mMediaProjection == null) {
            mMediaProjection = mpManager!!.getMediaProjection(resultCode, data!!)
            if (mMediaProjection != null) {
                // display metrics
//                mDensity = Resources.getSystem().displayMetrics.densityDpi
                mDensity = 1
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                mDisplay = windowManager.defaultDisplay

                // create virtual display depending on device width / height
                createVirtualDisplay()

                // register orientation change callback
                mOrientationChangeCallback = OrientationChangeCallback(this)
                if (mOrientationChangeCallback!!.canDetectOrientation()) {
                    mOrientationChangeCallback!!.enable()
                }

                // register media projection stop callback
                mMediaProjection!!.registerCallback(MediaProjectionStopCallback(), mHandler)
            }
        }
    }

    private fun stopProjection() {
//        mHandler?.post {
//            mMediaProjection?.stop()
//        }
        mMediaProjection?.stop()
    }

    private fun createVirtualDisplay() {
        // get width and height
        mWidth = Resources.getSystem().displayMetrics.widthPixels
        mHeight = Resources.getSystem().displayMetrics.heightPixels
        // start capture reader
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2)
        mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(
            SCREENCAP_NAME, mWidth, mHeight,
            mDensity, virtualDisplayFlags, mImageReader!!.surface, null, mHandler
        )

        mImageReader!!.setOnImageAvailableListener(ImageAvailableListener(), mHandler)
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val RESULT_CODE = "RESULT_CODE"
        private const val DATA = "DATA"
        private const val SCREENCAP_NAME = "screencap"

        fun start(context: Context, connection: ServiceConnection, resultCode: Int, data: Intent?) {
            val intent = Intent(context, ScreenCaptureService::class.java)
            intent.putExtra(RESULT_CODE, resultCode)
            intent.putExtra(DATA, data)
            context.bindService(intent, connection, BIND_AUTO_CREATE)
        }

        private val virtualDisplayFlags: Int
            private get() = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
    }
}