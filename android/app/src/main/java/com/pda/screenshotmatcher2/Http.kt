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
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException

private const val LOG_DEST = "/logs"
private const val MATCH_DEST = "/match"
private const val RESULT_DEST = "/results/result-"
var downloadID : Long = 0

fun sendBitmap(bitmap: Bitmap, serverURL: String, activity : Activity, context: Context){
    Log.d("DEBB", serverURL+ MATCH_DEST)
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
    val b64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)

    val queue = Volley.newRequestQueue(activity.applicationContext)
    val json = JSONObject()
    json.put("b64", b64Image)
    val jsonOR = JsonObjectRequest(
        Request.Method.POST, serverURL + MATCH_DEST, json,
        { response ->
            Log.v("TIMING", "Got response.")
            StudyLogger.hashMap["tc_http_response"] = System.currentTimeMillis()
            StudyLogger.hashMap["match_id"] = response.get("uid").toString()
            if (response.get("hasResult").toString() != "false") {
                try {
                    val b64ImageString = response.get("b64").toString()
                    if (b64ImageString.isNotEmpty()) {
                        val byteArray = Base64.decode(b64ImageString, Base64.DEFAULT)
                        //downloadFullScreenshot(response.get("uid").toString(), "screenshot.png", serverURL, context)
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
            } else{
                if(activity is CameraActivity) {
                    activity.openErrorFragment()
                }
            }
        },
        { error -> Log.d("TIMING", error.toString()) })

    StudyLogger.hashMap["tc_http_request"] = System.currentTimeMillis()
    queue.add(jsonOR)
}

fun downloadFullScreenshot(matchID: String, filename : String, serverURL: String, context: Context) {
    val uri: Uri = Uri.parse("${serverURL + RESULT_DEST + matchID}/screenshot.png")
    Log.v("TEST", uri.toString())
    Log.v("TIMING", "Adding result file download to queue")
    val filenameFull = "${filename}_Full.png"
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(uri)
    request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_PICTURES, filenameFull)

    Log.v("TIMING", "Download queued")
    downloadID = downloadManager.enqueue(request)
}

fun sendLog(serverURL: String, context: Context){
// Instantiate the RequestQueue.
    val queue = Volley.newRequestQueue(context)
    val json = JSONObject(StudyLogger.hashMap)
    val jsonObjectRequest = JsonObjectRequest(Request.Method.POST, serverURL + LOG_DEST, json,
        { response ->
            Log.v("HTTP", "Log sent.")
        },
        { error ->
            error.printStackTrace()
        }
    )


// Add the request to the RequestQueue.
    queue.add(jsonObjectRequest)

}