package com.pda.screenshotmatcher2.background

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.models.ServerConnectionModel
import com.pda.screenshotmatcher2.utils.rescale
import com.pda.screenshotmatcher2.views.activities.CameraActivity
import java.io.File
import java.util.*
import kotlin.collections.HashMap

/**
 * A [Service] running in the background, which detects when new photos are taken on the smartphone.
 * A [ContentObserver] monitors changes in the phone gallery directory and sends match requests once new photos are detected
 *
 * @property isServiceStarted Indicates whether or not the service is active
 * @property contentObserver [ContentObserver] instance, which monitors the gallery
 * @property sp [SharedPreferences] instance, stores matching options
 * @property notificationChannelId The channel id used to send notification
 */
class NewPhotoService : Service() {
    private var isServiceStarted = false
    private lateinit var contentObserver: ContentObserver
    private lateinit var sp: SharedPreferences
    private lateinit var MATCHING_MODE_PREF_KEY: String
    private var timestamp: Long = 0
    private val notificationChannelId = "SM"

    /**
     * Stores the last 10 files dispatched by [contentObserver] in the variable [lastPaths]. This is done to avoid double processing, because [contentObserver] sometimes fires multiple identical events per file.
     */
    companion object {
        // list to keep track of recently checked files to avoid double processing (content observer fires multiple identical events per file)
        var lastPaths: LinkedList<String> = object : LinkedList<String>() {
            override fun push(e: String?) {
                if (size > 10) removeAt(10)
                super.push(e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startService()
        return START_NOT_STICKY
    }

    private fun startService() {
        Log.d("NPS", "started fg")
        if (isServiceStarted) return
        isServiceStarted = true
        ServerConnectionModel.start(application, false)
        startContentObserver()
    }

    /**
     * Stops the background service
     *
	 * @param context
	 */
    fun stopService(context: Context) {
        try {
            contentResolver.unregisterContentObserver(contentObserver)
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            Log.d("NPS", "Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        //Bind other components here
        return null
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "ScreenshotMatcher",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Matching screenshots in the background"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent =
            Intent(this, CameraActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val builder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
                this,
                notificationChannelId
            ) else Notification.Builder(this)

        return builder
            .setContentTitle("ScreenshotMatcher")
            .setContentText("ScreenshotMatcher is active in the background")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(Notification.PRIORITY_HIGH) // android sdk < 26
            .build()
    }

    private fun startContentObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                val serverUrl = ServerConnectionModel.serverUrl.value
                if (serverUrl != null && serverUrl != "" && uri != null) {
                    val path = getPathFromObserverUri(uri)
                    if (path != null && !lastPaths.contains(path) && !path.contains(Regex("/_"))) {

                        lastPaths.push(path)
                        Log.d("NPS", "call path: $path, paths: ${lastPaths}}")
                        timestamp = System.currentTimeMillis()
                        Log.d("NPS", path)
                        val file = File(path)
                        Log.d(
                            "NPS_TS",
                            "loaded file" + (System.currentTimeMillis() - timestamp).toString()
                        )
                        timestamp = System.currentTimeMillis()
                        val sampledCameraImage = decodeSampledBitmapFromResource(file, 512, 512)
                        Log.d(
                            "NPS_TS",
                            "loaded og bitmap " + (System.currentTimeMillis() - timestamp).toString()
                        )
                        timestamp = System.currentTimeMillis()
                        rescaleAndSendToServer(image = sampledCameraImage)
                    } else path?.let { Log.d("NPS", "Not printed: $path") }
                } else {Log.d("NPS", "CO event, didnt meet requirements")}

            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    private fun rescaleAndSendToServer(image: Bitmap) {
        timestamp = System.currentTimeMillis()
        Log.d("NPS", "Bitmap width: ${image.width}")
        val greyImg = rescale(
            image,
            512
        )
        Log.d(
            "NPS_TS",
            "Converted grey after: " + (System.currentTimeMillis() - timestamp).toString()
        )
        timestamp = System.currentTimeMillis()
        val serverUrl = ServerConnectionModel.serverUrl.value
        if (serverUrl != null && serverUrl != "") {
            Log.d("NPS", serverUrl)
            val matchingOptions: java.util.HashMap<Any?, Any?> =
                getMatchingOptionsFromPref()
            Log.d(
                "NPS_TS",
                "Sending bitmap after: " + (System.currentTimeMillis() - timestamp).toString()
            )
            timestamp = System.currentTimeMillis()
            //TODO: implement sendCaptureRequest
            /*sendBitmap2(
                greyImg,
                serverUrl,
                this@NewPhotoService,
                matchingOptions,
                null,
                null,
                ::onMatch
            )*/
        } else {
            Log.d("NPS", "invalid serverURL")
        }
    }


    fun decodeSampledBitmapFromResource(
        file: File,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap {
        // First decode with inJustDecodeBounds=true to check dimensions
        return BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.path, this)

            // Calculate inSampleSize
            inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)

            // Decode bitmap with inSampleSize set
            inJustDecodeBounds = false

            BitmapFactory.decodeFile(file.path, this)
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 4

        if (height > reqHeight || width > reqWidth) {

            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }


    private fun onMatch(matchId: String?, ba: ByteArray?, original: Bitmap?) {
        Log.d("NPS_TS", "got response after:" + (System.currentTimeMillis() - timestamp).toString())
        Log.d("NPS", "matchid: $matchId, url: ${ServerConnectionModel.serverUrl.value}")
        Log.d("NPS", "Byte array: ${ba}")
        timestamp = System.currentTimeMillis()
        if (ba != null) {
            val image = BitmapFactory.decodeByteArray(ba, 0, ba.size)
            sendMatchNotification(image, true)
            Log.d(
                "NPS_TS",
                "sent notification after: " + (System.currentTimeMillis() - timestamp).toString()
            )
            timestamp = System.currentTimeMillis()
        } else {
            if (original != null) sendMatchNotification(original, false)

        }

    }

    private fun sendMatchNotification(bmp: Bitmap, didMatch: Boolean) {
        val notification =
            NotificationCompat.Builder(this@NewPhotoService, notificationChannelId)
                .setSmallIcon(R.drawable.ic_baseline_close_48)
                .setContentTitle("ScreenshotMatcher")
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bmp)
                )
                .apply {
                    if (didMatch) this.setContentText("match success") else this.setContentText("NO SCREENSHOT")
                }

        with(NotificationManagerCompat.from(this@NewPhotoService)) {
            // notificationId is a unique int for each notification that you must define
            notify(1, notification.build())
        }
    }

    private fun getMatchingOptionsFromPref(): HashMap<Any?, Any?> {
        if (!::sp.isInitialized) {
            sp = PreferenceManager.getDefaultSharedPreferences(this)
            MATCHING_MODE_PREF_KEY = getString(R.string.settings_algorithm_key)
        }
        val matchingMode: HashMap<Any?, Any?> = HashMap()
        val fastMatchingMode: Boolean = sp.getBoolean(MATCHING_MODE_PREF_KEY, true)

        if (fastMatchingMode) {
            matchingMode[getString(R.string.algorithm_key_server)] =
                getString(R.string.algorithm_fast_mode_name_server)
        } else {
            matchingMode[getString(R.string.algorithm_key_server)] =
                getString(R.string.algorithm_accurate_mode_name_server)
        }
        return matchingMode
    }


    // depending on the sdk version, different methods have to be used to obtain the file path
    fun getPathFromObserverUri(uri: Uri): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryRelativeDataColumn(uri)
        } else {
            queryDataColumn(uri)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun queryRelativeDataColumn(uri: Uri): String? {
        var relativePath: String? = null
        var name: String? = null
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val relativePathColumn =
                cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val displayNameColumn =
                cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                name = cursor.getString(displayNameColumn)
                // TODO : Remove?
                relativePath = "${Environment.getExternalStorageDirectory()}/${
                    cursor.getString(
                        relativePathColumn
                    )
                }$name"
            }
        }
        return relativePath
    }


    private fun queryDataColumn(uri: Uri): String? {
        var returnPath: String? = null
        val projection = arrayOf(
            MediaStore.Images.Media.DATA
        )
        contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn)
                // do something
                returnPath = path
            }
        }
        return returnPath
    }

}