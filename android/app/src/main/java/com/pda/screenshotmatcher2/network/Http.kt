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
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.views.activities.CameraActivity
import com.pda.screenshotmatcher2.views.fragments.FeedbackFragment
import com.pda.screenshotmatcher2.utils.getDeviceID
import com.pda.screenshotmatcher2.utils.getDeviceName
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.utils.base64ToBitmap
import com.pda.screenshotmatcher2.utils.decodeBase64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException

private const val LOG_DEST = "/logs"
private const val MATCH_DEST = "/match"
const val SCREENSHOT_DEST = "/screenshot"
private const val FEEDBACK_DEST = "/feedback"
private const val HEARTBEAT_DEST = "/heartbeat"
private const val PERMISSION_DEST = "/permission"


var queue: RequestQueue? = null

// used by outside classes
interface CaptureCallback {
    fun onPermissionDenied()
    fun onMatchResult(matchID: String, img: ByteArray)
    fun onMatchFailure(uid: String)
    fun onMatchRequestError()
}

// used for making the request
private interface CaptureRequestCallback : CaptureCallback {
    fun onPermissionRequired(uid: String)
}

// used for handling the request permission response
interface PermissionRequestCallback {
    fun onPermissionDenied()
    fun onPermissionGranted(matchID: String?, permissionToken: String?)
}

fun sendCaptureRequest(
    bitmap: Bitmap,
    serverURL: String,
    context: Context,
    matchingOptions: HashMap<Any?, Any?>? = null,
    permissionToken: String? = "",
    matchID: String? = "",
    captureCallback: CaptureCallback
) {
    if (queue === null) queue = Volley.newRequestQueue(context)
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
    val b64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)

    val json = JSONObject()
    // keys: "algorithm", "ORB_nfeatures", "SURF_hessian_threshold"
    matchingOptions?.forEach { (key, value) ->
        json.put(key.toString(), value.toString())
    }
    // add the device name
    json.put("device_name", getDeviceName())

    // add device ID for verification
    val id = getDeviceID(context)
    json.put("device_id", id)

    // add the image
    json.put("b64", b64Image)

    // if a permission token or matchID exists, add it to the request
    if (permissionToken !== null && permissionToken.isNotEmpty()) {
        json.put("permission_token", permissionToken)
    }
    if (matchID !== null && matchID.isNotEmpty()) {
        json.put("match_id", matchID)
    }

    val captureRequestCallback = object : CaptureRequestCallback {
        override fun onPermissionDenied() {
            captureCallback.onPermissionDenied()
        }
        override fun onMatchResult(matchID: String, img: ByteArray) {
            captureCallback.onMatchResult(matchID, img)
        }

        override fun onMatchFailure(uid: String) {
            captureCallback.onMatchFailure(uid)
        }

        override fun onMatchRequestError() {
            captureCallback.onMatchRequestError()
        }

        override fun onPermissionRequired(uid: String) {
            requestPermission(
                serverURL,
                context,
                uid,
                object : PermissionRequestCallback {
                    override fun onPermissionGranted(matchID: String?, permissionToken: String?) {
                        sendCaptureRequest(
                            bitmap,
                            serverURL,
                            context,
                            matchingOptions,
                            permissionToken,
                            matchID,
                            captureCallback
                        )
                    }
                    override fun onPermissionDenied() {
                        captureCallback.onPermissionDenied()
                    }
                }
            )
        }
    }

    val jsonOR = captureJsonObjectRequest(serverURL, json, captureRequestCallback)
    StudyLogger.hashMap["tc_http_request"] = System.currentTimeMillis()
    queue!!.add(jsonOR)
}


private fun captureJsonObjectRequest(
    serverURL: String,
    json: JSONObject,
    cb: CaptureRequestCallback
): JsonObjectRequest {
    return JsonObjectRequest(
        Request.Method.POST, serverURL + MATCH_DEST, json,
        { response ->
            logMatchResponse(response)
            when {
                response.has("hasResult") -> {
                    when (response.getBoolean("hasResult")) {
                        true -> {
                            val byteArray = decodeBase64(response.get("b64").toString())
                            if (byteArray != null) cb.onMatchResult(
                                response.get("uid").toString(),
                                byteArray
                            )
                            else cb.onMatchFailure(response.get("uid").toString())
                        }
                        false -> cb.onMatchFailure(response.get("uid").toString())
                    }

                }
                response.has("error") -> {
                    when (response.getString("error")) {
                        "permission_denied" -> cb.onPermissionDenied()
                        "permission_required" -> cb.onPermissionRequired(
                            response.get("uid").toString()
                        )
                        else -> throw Exception("Unknown error")
                    }
                }
                else -> throw Exception("Unknown response")
            }
        },
        { error ->
            cb.onMatchRequestError()
        })
}

fun requestPermission(
    serverURL: String,
    context: Context,
    matchID: String,
    cb: PermissionRequestCallback
) {
    // Instantiate the RequestQueue if it is not already instantiated
    if (queue === null) queue = Volley.newRequestQueue(context)
    Toast.makeText(context, "Requesting permission...", Toast.LENGTH_LONG).show()
    val json = JSONObject()
    json.put(
        "device_id",
        getDeviceID(context)
    )
    json.put("device_name", getDeviceName())
    json.put("match_id", matchID)

    val jsonObjectRequest =
        JsonObjectRequest(Request.Method.POST, serverURL + PERMISSION_DEST, json,
            { response ->
                Log.d("HTTP", "got response for permission")
                if (response.get("response") == "permission_granted") {
                    if (response.has("permission_token")) {
                        cb.onPermissionGranted(matchID, response.get("permission_token").toString())
                    } else {
                        cb.onPermissionGranted(null, null)
                    }
                } else if (response.get("response") == "permission_denied") {
                    cb.onPermissionDenied()
                }
            },
            { error ->
                if (error.networkResponse == null) {
                    Toast.makeText(
                        context.applicationContext,
                        "Permission request timeout",
                        Toast.LENGTH_LONG
                    ).show()
                    val ca = context as CameraActivity
                    ca.isCapturing = false
                } else {
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
    queue!!.add(jsonObjectRequest)
}

fun requestFullScreenshot(
    matchID: String,
    serverURL: String,
    context: Context,
    onDownload: (bitmap: Bitmap?) -> Unit
) {
    if (queue === null) queue = Volley.newRequestQueue(context)
    val json = JSONObject()
    json.put("match_id", matchID)
    val jsonOR = JsonObjectRequest(
        Request.Method.POST, serverURL + SCREENSHOT_DEST, json,
        { response ->
            if (response.has("error")) {
                if (response.getString("error") == "disabled_by_host_error") {
                    onDownload(null)
                }
            } else {
                val b64String: String = response.get("result").toString()
                onDownload(base64ToBitmap(b64String))
            }
        },
        { error ->
            error.printStackTrace()
        })
    queue!!.add(jsonOR)
}


fun sendHeartbeatRequest(serverURL: String?, context: Context, onFail: () -> Unit) {
    if ((serverURL == null || serverURL.isEmpty())) {
        onFail()
    }
    val request = StringRequest(Request.Method.GET, serverURL + HEARTBEAT_DEST,
        {
        },
        {
            onFail()
        })

    queue = Volley.newRequestQueue(context)
    queue!!.add(request)
}

private fun logMatchResponse(response: JSONObject) {
    StudyLogger.hashMap["tc_http_response"] = System.currentTimeMillis()
    try {
        StudyLogger.hashMap["match_id"] = response.get("uid").toString()
    } catch (e: Exception) {
        Log.e("HTTP", e.toString())
    }
}

fun sendLog(serverURL: String, context: Context) {
    // Only send log if preference is set to true
    // otherwise send the match_id only, so the server can delete the corresponding object for the request
    val MATCHING_MODE_PREF_KEY = context.getString(R.string.settings_logging_key)
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val sendLog = sp.getBoolean(MATCHING_MODE_PREF_KEY, false)

    val queue = Volley.newRequestQueue(context)
    val json: JSONObject
    if (sendLog) {
        json = JSONObject(StudyLogger.hashMap)
    } else {
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
) {
    if (queue === null) queue = Volley.newRequestQueue(context)
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
    queue!!.add(jsonObjectRequest)
}

