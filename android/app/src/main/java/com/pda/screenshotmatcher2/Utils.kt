package com.pda.screenshotmatcher2

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Exception

private val PERMISSIONS = arrayOf<String>(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.INTERNET,
    Manifest.permission.CAMERA,
)

fun getDateString() : String{
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.getDefault())
    return sdf.format(Date())
}

fun verifyPermissions(activity: Activity?) {
    // Check if we have write permission
    val writePermission = ActivityCompat.checkSelfPermission(
        activity!!,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    val cameraPermission = ActivityCompat.checkSelfPermission(
        activity,
        Manifest.permission.CAMERA
    )
    if (writePermission != PackageManager.PERMISSION_GRANTED || cameraPermission != PackageManager.PERMISSION_GRANTED) {
        // We don't have permission so prompt the user
        ActivityCompat.requestPermissions(
            activity,
            PERMISSIONS,
            1
        )
    }
}

fun base64ToBitmap(b64String: String) : Bitmap{
    val byteArray = Base64.decode(b64String, Base64.DEFAULT)
    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    return bitmap
}

fun saveBitmapToFile(filename: File, bitmap: Bitmap){
    try {
        val out = FileOutputStream(filename)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.flush()
        out.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

fun getDeviceName(): String {
    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL
    return if (model.startsWith(manufacturer)) {
        model
    } else {
        "$manufacturer $model"
    }
}

fun rotateBitmap(bitmap: Bitmap, deg: Float): Bitmap{
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(deg) }, true)
}

fun rotateBitmapAndAdjustRatio(bitmap: Bitmap, deg: Float): Bitmap{
    val width = bitmap.width
    val height = bitmap.height
    var mBitmap = Bitmap.createScaledBitmap(bitmap, height, width, false)
    mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, height, width, Matrix().apply { postRotate(deg) }, true)
    return mBitmap
}

fun createDeviceID(context: Context) {
    val prefs = context.getSharedPreferences("device_id", Context.MODE_PRIVATE)
    val savedID = prefs.getString("ID", "")

    // ensure we only create a new ID when the app is run for the very first time
    if (!savedID.isNullOrEmpty()) return

    val id = UUID.randomUUID().toString()
    prefs.edit().putString("ID", id).apply()
}

fun getDeviceID(context: Context) : String{
    val prefs = context.getSharedPreferences("device_id", Context.MODE_PRIVATE)
    val savedID = prefs.getString("ID", "")
    if(savedID.isNullOrEmpty()) {
        throw Exception("device ID is NULL")
    }
    else {
        return savedID
    }
}