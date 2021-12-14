package com.pda.screenshotmatcher2.network

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

/**
 * The request queue used to send requests to the server.
 */
var queue: RequestQueue? = null

/**
 * Object that stores all available routes to the server
 */
private object Routes {
    const val LOG_DEST = "/logs"
    const val MATCH_DEST = "/match"
    const val SCREENSHOT_DEST = "/screenshot"
    const val FEEDBACK_DEST = "/feedback"
    const val HEARTBEAT_DEST = "/heartbeat"
    const val PERMISSION_DEST = "/permission"
}

/**
 * Public Interface implemented by callback objects that want to be notified about the match status of a capture request.
 *
 * Use this Interface if you want to make capture requests to the server.
 */
interface CaptureCallback {
    fun onPermissionDenied()
    fun onMatchResult(matchID: String, img: ByteArray)
    fun onMatchFailure(uid: String)
    fun onMatchRequestError()
}

/**
 * Private Interface implemented by callback objects that want to be notified about the match status of a capture request AND whether a permission needs to be requested.
 *
 * Use this Interface if you want to request the match request permission.
 * Already implemented in [sendCaptureRequest].
 */
private interface CaptureRequestCallback : CaptureCallback {
    fun onPermissionRequired(uid: String)
}

/**
 * Private interface implemented by callback objects that want to be notified about the status of a permission request.
 *
 * Use this Interface if you want to listen to the permission request status.
 * Already implemented in [sendCaptureRequest].
 */
private interface PermissionRequestCallback {
    fun onPermissionDenied()
    fun onPermissionGranted(matchID: String?, permissionToken: String?)
}

/**
 * Send a match request to the server.
 *
 * @param bitmap The camera image to be matched.
 * @param serverURL The server URL to send the request to.
 * @param context The context of the calling Activity or Service to use for the request.
 * @param matchingOptions The matching options to be used.
 * @param permissionToken The permission token, if available.
 * @param matchID The match ID, if available.
 * @param captureCallback The [CaptureCallback] object to be notified about the match status.
 */
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

/**
 * Create a JSONObjectRequest that sends a match request to the server.
 *
 * @param serverURL The server URL to send the request to.
 * @param json The JSONObject to be sent.
 * @param cb The [CaptureRequestCallback] object to be notified about the match status.
 * @return A JSONObjectRequest that sends a match request to the server.
 */
private fun captureJsonObjectRequest(
    serverURL: String,
    json: JSONObject,
    cb: CaptureRequestCallback
): JsonObjectRequest {
    return JsonObjectRequest(
        Request.Method.POST, serverURL + Routes.MATCH_DEST, json,
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

/**
 * Request a permission token from the server.
 *
 * @param serverURL The server URL to send the request to.
 * @param context The context of the calling Activity or Service to use for the request.
 * @param matchID The match ID of the match to request the permission for.
 * @param cb The [PermissionRequestCallback] object to be notified about the permission status.
 */
private fun requestPermission(
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
        JsonObjectRequest(Request.Method.POST, serverURL + Routes.PERMISSION_DEST, json,
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

/**
 * Request a full screenshot for a match from the server.
 *
 * @param matchID The match ID of the match to request the screenshot for.
 * @param serverURL The server URL to send the request to.
 * @param context The context of the calling Activity or Service to use for the request.
 * @param onDownload Callback function to be notified about the download status.
 */
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
        Request.Method.POST, serverURL + Routes.SCREENSHOT_DEST, json,
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

/**
 * Send a heartbeat to the server.
 *
 * @param serverURL The server URL to send the request to.
 * @param context The context of the calling Activity or Service to use for the request.
 * @param onFail Callback function to be notified about a heartbeat failure.
 */
fun sendHeartbeatRequest(serverURL: String?, context: Context, onFail: () -> Unit) {
    if ((serverURL == null || serverURL.isEmpty())) {
        onFail()
    }
    val request = StringRequest(Request.Method.GET, serverURL + Routes.HEARTBEAT_DEST,
        {
        },
        {
            onFail()
        })

    if (queue === null) queue = Volley.newRequestQueue(context)
    queue!!.add(request)
}

/**
 * Log a match response to the [StudyLogger]
 *
 * @param response The response to log.
 */
private fun logMatchResponse(response: JSONObject) {
    StudyLogger.hashMap["tc_http_response"] = System.currentTimeMillis()
    try {
        StudyLogger.hashMap["match_id"] = response.get("uid").toString()
    } catch (e: Exception) {
        Log.e("HTTP", e.toString())
    }
}

/**
 * Send a log from [StudyLogger] to the logging server
 *
 * @param serverURL The server URL to send the request to.
 * @param context The context of the calling Activity or Service to use for the request.
 */
fun sendLog(serverURL: String, context: Context) {
    // Only send log if preference is set to true
    // otherwise send the match_id only, so the server can delete the corresponding object for the request
    val MATCHING_MODE_PREF_KEY = context.getString(R.string.settings_logging_key)
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val sendLog = sp.getBoolean(MATCHING_MODE_PREF_KEY, false)

    val json: JSONObject
    if (sendLog) {
        json = JSONObject(StudyLogger.hashMap)
    } else {
        val map = HashMap<Any?, Any?>()
        map["match_id"] = StudyLogger.hashMap["match_id"]
        map["logging_disabled"] = true
        json = JSONObject(map)
    }

    val jsonObjectRequest = JsonObjectRequest(Request.Method.POST, serverURL + Routes.LOG_DEST, json,
        {
        },
        { error ->
            Log.e("log", "Error sending Study Log, server offline")
        }
    )
    // Add the request to the RequestQueue.
    if (queue === null) queue = Volley.newRequestQueue(context)
    queue!!.add(jsonObjectRequest)
}

/**
 * Send feedback to a feedback server.
 *
 * @param fragment The fragment to send the feedback from.
 * @param context The context of the calling Activity or Service to use for the request.
 * @param serverUrl The server URL to send the request to.
 * @param uid The user id of the user.
 * @param hasResult Whether the match request has a result.
 * @param hasScreenshot Whether the match request has a screenshot.
 * @param comment The comment typed by the user in [FeedbackFragment].
 */
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
    val jsonObjectRequest = JsonObjectRequest(Request.Method.POST, serverUrl + Routes.FEEDBACK_DEST, json,
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

