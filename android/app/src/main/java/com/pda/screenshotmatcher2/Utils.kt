package com.pda.screenshotmatcher2

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


const val APP_DIRECTORY : String = "/ScreenshotMatcher/"

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

fun verifyPermissions(activity: Activity?) {
    // Check if we have write permission
    val writePermission = ActivityCompat.checkSelfPermission(
        activity!!,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    val cameraPermission = ActivityCompat.checkSelfPermission(
        activity!!,
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
fun saveFileToExternalDir(b64String: String, context: Context, dirName: String = "Pictures/"): String {
    val out : OutputStream
    val dir : String = Environment.DIRECTORY_PICTURES + APP_DIRECTORY + dirName
    val filename = "croppedScreenshot.jpg"
    Log.d("saving", "Dir name: $dir")

    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
        val resolver = context.contentResolver
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
        val imageUri : Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
        out = resolver.openOutputStream(imageUri)!!
    }
    else{
        val imageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val file = File(dir)
        if(!file.exists()){
            file.mkdir()
        }
        val image = File(dir, filename)
        out = FileOutputStream(image)
    }

    val byteArray = Base64.decode(b64String, Base64.DEFAULT)
    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    out.flush()
    out.close()

    return dir
}
