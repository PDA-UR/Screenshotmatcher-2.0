package com.pda.screenshotmatcher2

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException

private const val LOG_DEST = "/logs"
private const val MATCH_DEST = "/match"
const val SCREENSHOT_DEST = "/screenshot"
private const val FEEDBACK_DEST = "/feedback"
private const val HEARTBEAT_DEST = "/heartbeat"

fun sendBitmap(
    bitmap: Bitmap,
    serverURL: String,
    activity: Activity,
    matchingOptions: HashMap<Any?, Any?>? = null
){
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
    val b64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)

    val queue = Volley.newRequestQueue(activity.applicationContext)
    val json = JSONObject()
    matchingOptions?.forEach{ (key, value) ->
        json.put(key.toString(), value.toString())
    }
    // keys: "algorithm", "ORB_nfeatures", "SURF_hessian_threshold"
    json.put("b64", b64Image)
    val jsonOR = JsonObjectRequest(
        Request.Method.POST, serverURL + MATCH_DEST, json,
        { response ->
            StudyLogger.hashMap["tc_http_response"] = System.currentTimeMillis()
            StudyLogger.hashMap["match_id"] = response.get("uid").toString()
            if (response.get("hasResult").toString() != "false") {

                try {
                    val b64ImageString = response.get("b64").toString()
                    if (b64ImageString.isNotEmpty()) {
                        val byteArray = Base64.decode(b64ImageString, Base64.DEFAULT)
                        //downloadFullScreenshot(response.get("uid").toString(), "screenshot.png", serverURL, context)
                        if (activity is CameraActivity) {
                            activity.onMatchResult(
                                matchID = response.get("uid").toString(),
                                img = byteArray
                            )
                        }
                    }
                } catch (e: InvocationTargetException) {
                    e.printStackTrace()
                }
            } else if (activity is CameraActivity) {
                activity.openErrorFragment(response.get("uid").toString())
            }
        },
        { error ->
            if (activity is CameraActivity) {
                activity.onMatchRequestError()
            }

        })

    StudyLogger.hashMap["tc_http_request"] = System.currentTimeMillis()
    queue.add(jsonOR)
}

fun sendHeartbeatRequest(serverURL: String, activity: Activity){
    val request = StringRequest(Request.Method.GET, serverURL + HEARTBEAT_DEST,
        { response ->
        },
        {
            if (activity is CameraActivity) {
                activity.runOnUiThread { activity.onHeartbeatFail() }
            }
        })

    val queue = Volley.newRequestQueue(activity.applicationContext)
    queue.add(request)
}



fun sendLog(serverURL: String, context: Context){
    // Instantiate the RequestQueue.
    val queue = Volley.newRequestQueue(context)
    val json = JSONObject(StudyLogger.hashMap)
    val jsonObjectRequest = JsonObjectRequest(Request.Method.POST, serverURL + LOG_DEST, json,
        { _ ->
        },
        { error ->
            error.printStackTrace()
        }
    )
    // Add the request to the RequestQueue.
    queue.add(jsonObjectRequest)
}

fun sendFeedbackToServer(
    fragment: Fragment,
    context: Context,
    serverUrl: String,
    uid: String,
    hasResult: Boolean,
    hasScreenshot: Boolean,
    comment: String
){
    val queue = Volley.newRequestQueue(context)
    val json = JSONObject()
    json.put("uid", uid)
    json.put("hasResult", hasResult)
    json.put("hasScreenshot", hasScreenshot)
    json.put("comment", comment)
    json.put("device", getDeviceName())
    val jsonObjectRequest = JsonObjectRequest(Request.Method.POST, serverUrl + FEEDBACK_DEST, json,
        { response ->
            if (response.getBoolean("feedbackPosted")) {
                if (fragment is FeedbackFragment) {
                    fragment.onFeedbackPosted()
                }
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.ff_submit_failed_en),
                    Toast.LENGTH_LONG
                ).show()
            }
        },
        { error ->
            error.printStackTrace()
        }
    )
    queue.add(jsonObjectRequest)
}