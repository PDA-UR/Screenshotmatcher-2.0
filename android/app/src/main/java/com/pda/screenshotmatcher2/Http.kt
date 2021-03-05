package com.pda.screenshotmatcher2

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException


fun sendBitmap(bitmap: Bitmap, serverURL: String, activity : Activity, context: Context){
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
    val b64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)

    val queue = Volley.newRequestQueue(activity.applicationContext)
    val json = JSONObject()
    json.put("b64", b64Image)
    val jsonOR = JsonObjectRequest(
        Request.Method.POST, "$serverURL/match-b64", json,
        { response ->
            Log.v("TIMING", "Got response.")
            if (response.get("hasResult").toString() != "false") {
                try {
                    val b64ImageString = response.get("b64").toString()
                    if (b64ImageString.isNotEmpty()) {
                        val croppedScreenshotFilename: String =
                            saveFileToExternalDir(b64ImageString, context, response.get("uid").toString())
                        val downloadID : Long = downloadFullScreenshot(response.get("uid").toString(), serverURL, context)

                        if(activity is CameraActivity) activity.onMatchResult(croppedScreenshotFilename, downloadID)
                    }
                } catch (e: InvocationTargetException) {
                    e.printStackTrace()
                }
            }
        },
        { error -> Log.v("TIMING", error.toString()) })

    queue.add(jsonOR)
}

fun downloadFullScreenshot(fileName: String, serverURL: String, context: Context): Long {
    Log.v("TIMING", "Adding result file download to queue")
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val uri: Uri = Uri.parse("$serverURL/results/result-$fileName/screenshot.png")
    Log.d("TIMING", "Filename:")
    Log.d("TIMING", fileName)
    val request = DownloadManager.Request(uri)
    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "$APP_DIRECTORY$fileName/fullScreenshot.png")

    Log.v("TIMING", "Download queued")
    return downloadManager.enqueue(request)
}