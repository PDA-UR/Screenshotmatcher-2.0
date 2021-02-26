package com.pda.screenshotmatcher2

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import net.gotev.uploadservice.data.UploadInfo
import net.gotev.uploadservice.network.ServerResponse
import net.gotev.uploadservice.observer.request.RequestObserverDelegate
import net.gotev.uploadservice.protocols.multipart.MultipartUploadRequest
import org.json.JSONObject

//uses https://github.com/gotev/android-upload-service
class HTTPClient(
    val serverUrl: String,
    val context: Context,
    val lifecycleOwner: LifecycleOwner
) {
    private val TAG = "HTTP"
    private val MATCHING_DESTINATION = "/match"
    private val LOG_DESTINATION = "/log"

    private companion object {
        // Every intent for result needs a unique ID in your app.
        // Choose the number which is good for you, here I'll use a random one.
        const val fileRequestCode = 42
    }

    fun sendFileToServer(filePath: String) {
        MultipartUploadRequest(context = context, serverUrl = serverUrl + MATCHING_DESTINATION)
            .setMethod("POST")
            .setMaxRetries(2)
            .addFileToUpload(
                filePath = filePath,
                parameterName = "file"
            )
            .subscribe(context = context, lifecycleOwner = lifecycleOwner, delegate = object : RequestObserverDelegate {
                override fun onProgress(context: Context, uploadInfo: UploadInfo) {
                    // do your thing
                }

                override fun onSuccess(
                    context: Context,
                    uploadInfo: UploadInfo,
                    serverResponse: ServerResponse
                ) {
                    val json = JSONObject(serverResponse.bodyString)
                    onServerResponse(json)
                }

                override fun onError(
                    context: Context,
                    uploadInfo: UploadInfo,
                    exception: Throwable
                ) {
                    println(uploadInfo)
                    println(exception)
                    onServerResponse(null)
                }

                override fun onCompleted(context: Context, uploadInfo: UploadInfo) {
//                    postSpeedResults(uploadInfo, serverUrl, context, lifecycleOwner)
                }

                override fun onCompletedWhileNotObserving() {
                    // do your thing
                }
            })
    }

    fun onServerResponse(response : JSONObject?) {
        if(response == null) return

        when(response["hasResult"]) {
            false -> Log.v(TAG, "Match failure.")
            true -> {
                Log.v(TAG, "Match found.")
                downloadMatch(response)
            }
        }
    }

    fun downloadMatch(response : JSONObject){
        Log.v(TAG, serverUrl + response["filename"])
        // TODO
    }
}
