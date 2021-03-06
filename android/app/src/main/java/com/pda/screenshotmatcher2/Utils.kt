package com.pda.screenshotmatcher2

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*


const val IMG_DIR : String = "Pictures/ScreenshotMatcher"

private val PERMISSIONS = arrayOf<String>(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.INTERNET,
    Manifest.permission.CAMERA,
)

fun getDataDir(context: Context): String? {
    return context.packageManager
        .getPackageInfo(context.packageName, 0).applicationInfo.dataDir
}

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

fun saveB64ToInternalFile(b64String: String, context: Context){
    val filename = System.currentTimeMillis().toString() + ".jpg"
    val byteArray = Base64.decode(b64String, Base64.DEFAULT)
    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    val file = File(targetDir, filename)
    Log.v("TESTING", targetDir.toString())
    Log.v("TESTING", file.toString())
    try {
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.flush()
        out.fd.sync()
        out.close()
    } catch (e: Exception){
        e.printStackTrace()
    }
}

@Suppress("DEPRECATION")
fun saveFileToExternalDir(bitmap: Bitmap, context: Context, isFullScreenshot : Boolean = false){
    val out : OutputStream
    val filename = when(isFullScreenshot){
        false -> getDateString() + ".jpg"
        true -> getDateString() + "_full.jpg"
    }
    if(!File(IMG_DIR).exists()) {
        File(IMG_DIR).mkdir()
    }

    out = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
        val resolver = context.contentResolver
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, IMG_DIR)
        val imageUri : Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
        resolver.openOutputStream(imageUri)!!
    }
    else{
        val image = File(IMG_DIR, filename)
        FileOutputStream(image)
    }

    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    out.flush()
    out.close()
}