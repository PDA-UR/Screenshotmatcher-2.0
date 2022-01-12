package com.pda.screenshotmatcher2.utils

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.Size
import androidx.core.app.ActivityCompat
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import com.pda.screenshotmatcher2.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Exception

/**
 * Returns all required permissions for the app.
 *
 * @return An Array of all the permissions required by the app.
 */
fun getPermissions(): Array<String> {
    val basePermissions = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
    )
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) arrayOf(*basePermissions, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    else basePermissions
}

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
 * Verifies if [activity] has been granted all permissions of [getPermissions].
 * Automatically requests the missing permissions if necessary.
 *
 * @return true = all permissions granted; false = at least one permission denied
 */
fun verifyPermissions(activity: Activity): Boolean {
    val rejectedPermissions = getPermissions().filter {
        ActivityCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
    }

    if (rejectedPermissions.isEmpty()) return true
    else ActivityCompat.requestPermissions(
        activity,
        rejectedPermissions.toTypedArray(),
        1
    )
    return false
}

/**
 * Converts a [b64String] to a [Bitmap] and returns it.
 */
fun base64ToBitmap(b64String: String) : Bitmap{
    val byteArray = Base64.decode(b64String, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
}

/**
 * Decodes a b64String to a [Bitmap] and returns it.
 *
 * @param input The b64String to decode
 * @return The decoded [Bitmap]
 */
fun decodeBase64(input: String): ByteArray? {
    return if (input.isNotEmpty()) {
        Base64.decode(input, Base64.DEFAULT)
    } else {
        Log.e("HTTP", "Empty base64 string")
        null
    }
}




/**
 * Saves [bitmap] to [file].
 */
fun saveBitmapToFile(file: File, bitmap: Bitmap){
    try {
        val out = FileOutputStream(file)
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
 * Creates a sharing chooser with the given file.
 *
 * @param file The image file to share.
 */

fun createSharingChooser(file: File, mimeType: MimeType, activity: Activity) {
    val contentUri =
        FileProvider.getUriForFile(
            activity.applicationContext,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            file
        )

    val shareIntent = ShareCompat.IntentBuilder.from(activity)
        .setType(mimeType.string)
        .setStream(contentUri)
        .createChooserIntent()


    val resInfoList: List<ResolveInfo> = activity.packageManager
        .queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)

    for (resolveInfo in resInfoList) {
        val packageName: String = resolveInfo.activityInfo.packageName
        activity.grantUriPermission(
            packageName,
            contentUri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
    startActivity(activity, shareIntent, null)
}

fun saveImageFileToGallery(file: File, description: String, context: Context){
    // if version is Q or higher, use the new API
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        values.put(MediaStore.Images.Media.MIME_TYPE, MimeType(MimeTypes.PNG).string)
        values.put(MediaStore.Images.Media.DESCRIPTION, description)
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        context.contentResolver.openOutputStream(uri!!).use {
            file.inputStream().use { input ->
                if (it != null) {
                    input.copyTo(it)
                }
            }
        }
    } else {
        @Suppress("DEPRECATION") // we checked sdk version above
        MediaStore.Images.Media.insertImage(
            context.contentResolver,
            file.absolutePath,
            file.name,
            description
        )
    }

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