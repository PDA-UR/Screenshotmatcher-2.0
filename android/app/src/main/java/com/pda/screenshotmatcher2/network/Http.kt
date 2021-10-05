package com.pda.screenshotmatcher2.network

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.views.activities.CameraActivity
import com.pda.screenshotmatcher2.views.fragments.FeedbackFragment
import com.pda.screenshotmatcher2.viewHelpers.CameraActivityFragmentHandler
import com.pda.screenshotmatcher2.utils.getDeviceID
import com.pda.screenshotmatcher2.utils.getDeviceName
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.utils.base64ToBitmap
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException

private const val LOG_DEST = "/logs"
private const val MATCH_DEST = "/match"
const val SCREENSHOT_DEST = "/screenshot"
private const val FEEDBACK_DEST = "/feedback"
private const val HEARTBEAT_DEST = "/heartbeat"
private const val PERMISSION_DEST = "/permission"

fun sendBitmap(
    bitmap: Bitmap,
    serverURL: String,
    activity: Activity,
    matchingOptions: HashMap<Any?, Any?>? = null,
    permissionToken: String = "",
    matchID: String = ""
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

    // add the device name
    json.put("device_name", getDeviceName())

    // add device ID for verification
    val id =
        getDeviceID(activity.applicationContext)
    json.put("device_id", id)

    if(permissionToken.isNotEmpty()) {
        json.put("permission_token", permissionToken)
    }
    if(matchID.isNotEmpty()) {
        json.put("match_id", matchID)
    }

    // add the image
    json.put("b64", b64Image)

    val jsonOR = JsonObjectRequest(
        Request.Method.POST, serverURL + MATCH_DEST, json,
        { response ->
            StudyLogger.hashMap["tc_http_response"] = System.currentTimeMillis()
            try{
                StudyLogger.hashMap["match_id"] = response.get("uid").toString()
            } catch (e: Exception) {
                Log.d("HTTP", e.toString())
                Log.d("HTTP", response.toString())
            }
            if (response.has("error")){
                if(activity is CameraActivity && response.getString("error") == "permission_error") {
                    activity.onPermissionDenied()
                }
                else if(response.getString("error") == "permission_required") {
                    requestPermission(
                        bitmap,
                        serverURL,
                        activity,
                        matchingOptions,
                        response.get("uid").toString()
                    )
                }
            }
            else if (response.get("hasResult").toString() != "false") {
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
                    Log.e("HTTP", "b64 string error")
                }
            }
            else if (activity is CameraActivity) {
                val fm: CameraActivityFragmentHandler = activity.cameraActivityFragmentHandler
                fm.openErrorFragment(response.get("uid").toString(), bitmap)
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

fun requestFullScreenshot(matchID: String, serverURL: String, context: Context, onDownload: (bitmap: Bitmap?) -> Unit) {
    val queue = Volley.newRequestQueue(context)
    val json = JSONObject()
    json.put("match_id", matchID)
    val jsonOR = JsonObjectRequest(
        Request.Method.POST, serverURL + SCREENSHOT_DEST, json,
        { response ->
            if(response.has("error")) {
                if(response.getString("error") == "disabled_by_host_error") {
                    onDownload(null)
                }
            }
            else {
                val b64String: String = response.get("result").toString()
                onDownload(base64ToBitmap(b64String))
            }
        },
        { error ->
            error.printStackTrace()
        })
    queue.add(jsonOR)
}


fun sendHeartbeatRequest(serverURL: String?, context: Context, onFail: () -> Unit){
    if ((serverURL == null || serverURL.isEmpty())) {
        onFail()
    }
    val request = StringRequest(Request.Method.GET, serverURL + HEARTBEAT_DEST,
        {
        },
        {
            onFail()
        })

    val queue = Volley.newRequestQueue(context)
    queue.add(request)
}



fun sendLog(serverURL: String, context: Context){
    // Only send log if preference is set to true
    // otherwise send the match_id only, so the server can delete the corresponding object for the request
    val MATCHING_MODE_PREF_KEY = context.getString(R.string.settings_logging_key)
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val sendLog = sp.getBoolean(MATCHING_MODE_PREF_KEY,false)
    
    val queue = Volley.newRequestQueue(context)
    var json : JSONObject = JSONObject()
    if(sendLog) {
        json = JSONObject(StudyLogger.hashMap)
    }
    else {
        val map = HashMap<Any?, Any?>()
        map["match_id"] = StudyLogger.hashMap["match_id"]
        map["logging_disabled"] = true
        json = JSONObject(map)
    }

    val jsonObjectRequest = JsonObjectRequest(Request.Method.POST, serverURL + LOG_DEST, json,
        {
        },
        { error ->
            Log.e("log", "Error sending Study Log, server offline")
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

fun requestPermission(
    bitmap: Bitmap,
    serverURL: String,
    activity: Activity,
    matchingOptions: HashMap<Any?, Any?>? = null,
    matchID: String
){
    // Instantiate the RequestQueue.
    val queue = Volley.newRequestQueue(activity.applicationContext)
    val json = JSONObject()
    json.put("device_id",
        getDeviceID(activity.applicationContext)
    )
    json.put("device_name", getDeviceName())
    json.put("match_id", matchID)

    val jsonObjectRequest = JsonObjectRequest(Request.Method.POST, serverURL + PERMISSION_DEST, json,
        { response ->
            if (response.get("response") == "permission_granted") {
                if(response.has("permission_token")) {
                    sendBitmap(
                        bitmap,
                        serverURL,
                        activity,
                        matchingOptions,
                        permissionToken = response.getString("permission_token"),
                        matchID = matchID
                    )
                }
                else {
                    sendBitmap(
                        bitmap,
                        serverURL,
                        activity,
                        matchingOptions
                    )
                }
            }
            else if(response.get("response") == "permission_denied" && activity is CameraActivity) {
                activity.onPermissionDenied()
            }
        },
        { error ->
            if(error.networkResponse == null) {
                Toast.makeText(activity.applicationContext, "Permission request timeout", Toast.LENGTH_LONG).show()
                val ca = activity as CameraActivity
                ca.cameraInstance.isCapturing = false
            }
            else{
                error.printStackTrace()
            }
        }
    )
    jsonObjectRequest.retryPolicy = DefaultRetryPolicy(
        20000,
        1,
        2.0F
    )
    // Add the request to the RequestQueue.
    queue.add(jsonObjectRequest)
}