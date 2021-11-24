package com.pda.screenshotmatcher2.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.util.Base64
import android.util.Size
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Exception

/**
 * The permissions used by the application.
 */
private val PERMISSIONS = arrayOf(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.INTERNET,
    Manifest.permission.CAMERA
)

/**
 * Returns the current date/time.
 *
 * @return The current date/time in the format "yyyy-MM-dd'T'HH-mm-ss"
 */
fun getDateString() : String{
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.getDefault())
    return sdf.format(Date())
}

/**
 * Verifies if [activity] has been granted all permissions of [PERMISSIONS].
 *
 * @return true = all permissions granted; false = at least one permission denied
 */
fun verifyPermissions(activity: Activity?): Boolean {
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
        return false
    } else return true
}

/**
 * Converts a [b64String] to a [Bitmap] and returns it.
 */
fun base64ToBitmap(b64String: String) : Bitmap{
    val byteArray = Base64.decode(b64String, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
}

/**
 * Saves [bitmap] to [filename].
 */
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

/**
 * Returns the devices model name.
 */
fun getDeviceName(): String {
    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL
    return if (model.startsWith(manufacturer)) {
        model
    } else {
        "$manufacturer $model"
    }
}

/**
 * Rotates [bitmap] by [deg] and returns it.
 */
fun rotateBitmap(bitmap: Bitmap, deg: Float): Bitmap{
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(deg) }, true)
}

/**
 * Creates a new device ID if the app is running for the very first time.
 *
 * @param context The application [Context], required to access the application shared preferences.
 */
fun createDeviceID(context: Context) {
    val prefs = context.getSharedPreferences("device_id", Context.MODE_PRIVATE)
    val savedID = prefs.getString("ID", "")

    // ensure we only create a new ID when the app is run for the very first time
    if (!savedID.isNullOrEmpty()) return

    val id = UUID.randomUUID().toString()
    prefs.edit().putString("ID", id).apply()
}

/**
 * Returns the device ID.
 *
 * @exception Exception Thrown when no device ID is set in shared preferences
 * @param context The application [Context], required to access the application shared preferences.
 */
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

/**
 * [Comparator], which compares two [Sizes][Size] and returns the bigger one.
 */
class CompareSizesByArea : Comparator<Size?> {
    override fun compare(o1: Size?, o2: Size?): Int {
        if (o1 != null) {
            if (o2 != null) {
                return java.lang.Long.signum(o1.width.toLong() * o2.height - o1.width.toLong() * o2.height)
            }
        }
        return -1
    }
}