package com.pda.screenshotmatcher2

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import java.io.File

private val PERMISSIONS = arrayOf<String>(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.INTERNET
)

fun verifyPermissions(activity: Activity?) {
    // Check if we have write permission
    val permission = ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    if (permission != PackageManager.PERMISSION_GRANTED) {
        // We don't have permission so prompt the user
        ActivityCompat.requestPermissions(
            activity,
            PERMISSIONS,
            1
        )
    }
}

fun mkdirForApp(){
    val dir = File("/storage/emulated/0/Screenshotmatcher2")
    if(!dir.isDirectory) dir.mkdir()
}