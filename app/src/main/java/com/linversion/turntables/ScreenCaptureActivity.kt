package com.linversion.turntables

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast

class ScreenCaptureActivity : Activity() {
    private var resultCode: Int = 0
    private lateinit var data: Intent

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {

        }

        override fun onServiceDisconnected(name: ComponentName?) {

        }
    }

    /****************************************** Activity Lifecycle methods  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startProjection()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProjection()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                this.resultCode = resultCode
                this.data = data
                moveToSubWidow()
            } else {
                Toast.makeText(this, "获取授权失败", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQUEST_OVERLAY) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "授权失败", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "授权成功", Toast.LENGTH_SHORT).show()

                ScreenCaptureService.start(this, serviceConnection, resultCode, data)
            }
        }
    }

    /****************************************** UI Widget Callbacks  */
    private fun startProjection() {
        val mProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    private fun stopProjection() {
        unbindService(serviceConnection)
    }

    private fun moveToSubWidow() {
        moveTaskToBack(true)
        //判断是否有悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "当前无悬浮窗权限，请授权", Toast.LENGTH_SHORT).show()
            startActivityForResult(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ), REQUEST_OVERLAY
            )
        } else {
            ScreenCaptureService.start(this, serviceConnection, resultCode, data)
        }
    }

    companion object {
        private const val REQUEST_CODE = 100
        private const val REQUEST_OVERLAY = 200
    }
}