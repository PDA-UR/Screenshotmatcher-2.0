package com.pda.screenshotmatcher2

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File

lateinit var APP_DIRECTORY : File

private val PERMISSIONS = arrayOf<String>(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.INTERNET,
    Manifest.permission.CAMERA,
)

fun verifyPermissions(activity: Activity?) {
    // Check if we have write permission
    val writePermission = ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    val cameraPermission = ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA)
    if (writePermission != PackageManager.PERMISSION_GRANTED || cameraPermission != PackageManager.PERMISSION_GRANTED) {
        // We don't have permission so prompt the user
        ActivityCompat.requestPermissions(
            activity,
            PERMISSIONS,
            1
        )
    }
}