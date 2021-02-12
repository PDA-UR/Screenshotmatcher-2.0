package pda.http_speedtest

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleOwner
import net.gotev.uploadservice.data.UploadInfo
import net.gotev.uploadservice.network.ServerResponse
import net.gotev.uploadservice.observer.request.RequestObserverDelegate
import net.gotev.uploadservice.protocols.multipart.MultipartUploadRequest

//uses https://github.com/gotev/android-upload-service
class HTTPclient {
    private val TAG = "HTTP"
    private val SERVER_IP = "192.168.178.2"
    private val SERVER_PORT = 1337
    private val SERVER_FILE_DEST = SERVER_IP + ":" + SERVER_PORT.toString() + "file/"
    private val SERVER_LOG_DEST = SERVER_IP + ":" + SERVER_PORT.toString() + "results/"

    // Storage Permissions
    private val REQUEST_EXTERNAL_STORAGE = 1
    private val PERMISSIONS_STORAGE = arrayOf<String>(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    companion object {
        // Every intent for result needs a unique ID in your app.
        // Choose the number which is good for you, here I'll use a random one.
        const val pickFileRequestCode = 42
    }

    private fun verifyStoragePermissions(activity: Activity?) {
        // Check if we have write permission
        val permission = ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            )
        }
    }

    private fun postFileHTTP(filePath: String, context: Context, lifecycleOwner: LifecycleOwner){
        MultipartUploadRequest(context = context, serverUrl = SERVER_FILE_DEST)
                .setMethod("POST")
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
                        // do your thing
                    }

                    override fun onError(
                            context: Context,
                            uploadInfo: UploadInfo,
                            exception: Throwable
                    ) {
                        println(uploadInfo)
                        println(exception)
                        // do your thing
                    }

                    override fun onCompleted(context: Context, uploadInfo: UploadInfo) {
                        postSpeedResults(uploadInfo, context, lifecycleOwner)
                    }

                    override fun onCompletedWhileNotObserving() {
                        // do your thing
                    }
                })
    }

    private fun postSpeedResults(uploadInfo: UploadInfo, context: Context, lifecycleOwner: LifecycleOwner){
        if(uploadInfo.files[0].successfullyUploaded) {
            Log.v(TAG, "upload successful")
            val filePath = uploadInfo.files[0].path
            val fileSize = uploadInfo.totalBytes.toString()
            val received = System.currentTimeMillis().toString()
            val pixelsLongSide = filePath.substringBeforeLast('/').substringAfterLast('/') // get the name of the folder the file is in
            val filename = filePath.substringAfterLast('/')

            MultipartUploadRequest(context = context, serverUrl = SERVER_LOG_DEST)
                    .setMethod("POST")
                    .addParameter("t_sent", uploadInfo.startTime.toString())
                    .addParameter("t_recv", received)
                    .addParameter("filename", filename)
                    .addParameter("filesize", fileSize)
                    .addParameter("pixels_long_side", pixelsLongSide)
                    .subscribe(context = context, lifecycleOwner = lifecycleOwner, delegate = object : RequestObserverDelegate {
                        override fun onProgress(context: Context, uploadInfo: UploadInfo) {
                            // do your thing
                        }

                        override fun onSuccess(
                                context: Context,
                                uploadInfo: UploadInfo,
                                serverResponse: ServerResponse
                        ) {
                            // do your thing
                        }

                        override fun onError(
                                context: Context,
                                uploadInfo: UploadInfo,
                                exception: Throwable
                        ) {
                            // do your thing
                        }

                        override fun onCompleted(context: Context, uploadInfo: UploadInfo) {
                            // do your thing
                        }

                        override fun onCompletedWhileNotObserving() {
                            // do your thing
                        }
                    })
        }
    }
}
