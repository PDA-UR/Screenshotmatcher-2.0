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

var downloadID : Long = 0

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
                        val byteArray = Base64.decode(b64ImageString, Base64.DEFAULT)

                        if(activity is CameraActivity) {
                            activity.onMatchResult(
                                matchID = response.get("uid").toString(),
                                img = byteArray
                            )
                        }
                    }
                } catch (e: InvocationTargetException) {
                    e.printStackTrace()
                }
            }
        },
        { error -> Log.v("TIMING", error.toString()) })

    queue.add(jsonOR)
}

fun downloadFullScreenshot(matchID: String, filename : String, serverURL: String, context: Context) {
    val uri: Uri = Uri.parse("$serverURL/results/result-$matchID/screenshot.png")
    Log.v("TEST", uri.toString())
    Log.v("TIMING", "Adding result file download to queue")
    val filenameFull = "${filename}_Full.png"
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(uri)
    request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_PICTURES, filenameFull)

    Log.v("TIMING", "Download queued")
    downloadID = downloadManager.enqueue(request)
}