package com.pda.screenshotmatcher2

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
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

    private var downloadID : Long = 0

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
                    // stuff
                }

                override fun onSuccess(
                    context: Context,
                    uploadInfo: UploadInfo,
                    serverResponse: ServerResponse
                ) {
                    Log.v("TIMING", "Got response.")
//                    val json = JSONObject(serverResponse.bodyString)
//                    onServerResponse(json)
                }

                override fun onError(
                    context: Context,
                    uploadInfo: UploadInfo,
                    exception: Throwable
                ) {
                    Log.v("TIMING", "Got response.")
//                    println(uploadInfo)
//                    println(exception)
//                    onServerResponse(null)
                }

                override fun onCompleted(context: Context, uploadInfo: UploadInfo) {
//                    postSpeedResults(uploadInfo, serverUrl, context, lifecycleOwner)
                }

                override fun onCompletedWhileNotObserving() {
                    // do your thing
                }
            })
    }

    fun onServerResponse(response: JSONObject?) {
        if(response == null) return
        Log.v("TIMING", "Got response from server.")
        when(response["hasResult"]) {
            false -> Log.v(TAG, "Match failure.")
            true -> {
                Log.v(TAG, "Match found.")
                downloadMatch(response)
            }
        }
    }

    fun downloadMatch(response: JSONObject){
        Log.v("TIMING", "Adding result file download to queue")
        val downloadmanager = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val uri: Uri = Uri.parse(serverUrl + response["filename"])
        val request = DownloadManager.Request(uri)
        request.setTitle("Screenshot")
        request.setDescription("downloading")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "/"+response["filename"].toString().substringAfterLast('/'))

        downloadID = downloadmanager.enqueue(request)
        context.registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) // TODO>: call this in the onCreate() function of the fragment, that displays the result
        Log.v("TIMING", "Download queued")
    }

    // TODO: still has to be unregistered somewhere. prefarably in the fragment, that displays the result image. maybe inside onDestroy()
    val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //Fetching the download id received with the broadcast
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            //Checking if the received broadcast is for our enqueued download by matching download id
            if (downloadID == id) {
                Log.v("TIMING", "Download complete")
                Toast.makeText(context, "Download Completed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
