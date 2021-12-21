package com.pda.screenshotmatcher2.background

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.models.CaptureModel
import com.pda.screenshotmatcher2.models.ServerConnectionModel
import com.pda.screenshotmatcher2.network.CaptureCallback
import com.pda.screenshotmatcher2.network.sendCaptureRequest
import com.pda.screenshotmatcher2.utils.rescale
import com.pda.screenshotmatcher2.views.activities.CameraActivity
import com.pda.screenshotmatcher2.views.activities.ResultsActivity
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


/**
 * A [Service] running in the background, which detects when new photos are taken on the smartphone.
 * A [ContentObserver] monitors changes in the phone gallery directory and sends match requests once new photos are detected
 *
 * @property isServiceStarted Indicates whether or not the service is active
 * @property contentObserver [ContentObserver] instance, which monitors the gallery
 * @property sp [SharedPreferences] instance, stores matching options
 * @property notificationChannelId The channel id used to send notification
 */
class BackgroundMatchingService : Service() {
    private var isServiceStarted = false
    private lateinit var contentObserver: ContentObserver
    private lateinit var sp: SharedPreferences
    private lateinit var MATCHING_MODE_PREF_KEY: String
    private var timestamp: Long = 0
    private val notificationChannelId = "SM"
    private lateinit var broadcastReceiver: BroadcastReceiver

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
        fun startBackgroundService(context: Context) {
            Intent(context, BackgroundMatchingService::class.java).also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.d("BG", "Starting the service in >=26 Mode")
                    context.startForegroundService(it)
                    return
                }
                Log.d("BG", "Starting the service in < 26 Mode")
                context.startService(it)
            }
        }

        fun stopBackgroundService(context: Context) {
            Intent(context, BackgroundMatchingService::class.java).also {
                Log.d("CA", "Stopping service")
                context.stopService(it)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notification = createNotification()
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d("BG", "Screen off")
                        sleepService()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d("BG", "Screen on")
                        startService()
                    }
                }
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        intentFilter.addAction(Intent.ACTION_SCREEN_ON)
        this@BackgroundMatchingService.registerReceiver(broadcastReceiver, intentFilter)
        startForeground(1, notification)
    }

    override fun onDestroy() {
        this@BackgroundMatchingService.unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startService()
        return START_NOT_STICKY
    }

    private fun sleepService() {
        if (isServiceStarted) return
        isServiceStarted = false
        contentResolver.unregisterContentObserver(contentObserver)
        ServerConnectionModel.stopThreads()
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
            @SuppressLint("SimpleDateFormat")
            override fun onChange(selfChange: Boolean, uri: Uri?, flag: Int) {
                super.onChange(selfChange, uri, flag)
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

                        val isValid = isNewCameraPhoto(file)
                        if (!isValid) {
                            Log.d("NPS", "not a recent camera photo")
                            return
                        }
                        Log.d(
                            "NPS_TS",
                            "validated file : $isValid " + (System.currentTimeMillis() - timestamp).toString()
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
                } else {Log.d("NPS", "CO event, didn't meet requirements")}

            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    @SuppressLint("SimpleDateFormat")
    private fun isNewCameraPhoto(file: File): Boolean {
        val currentTimestamp = Date().time
        val exif = ExifInterface(file.absolutePath)
        val exifDateTimeString = exif.getAttribute(ExifInterface.TAG_DATETIME)
        val exifDateTime = if (exifDateTimeString != null) {
            try {
                // convert imageCaptureDateString to a date
                SimpleDateFormat("yyyy:MM:dd HH:mm:ss").parse(exifDateTimeString)
            } catch (e: ParseException) {
                Log.e("NPS", "exif parse error: ${e.message}")
                return false
            }
        } else return false
        return exifDateTime.time + 10000 > currentTimestamp
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
            CaptureModel.clear()
            CaptureModel.setCameraImage(image)
            CaptureModel.setServerURL(serverUrl)

            Log.d("NPS", serverUrl)
            val matchingOptions: java.util.HashMap<Any?, Any?> =
                getMatchingOptionsFromPref()
            Log.d(
                "NPS_TS",
                "Sending bitmap after: " + (System.currentTimeMillis() - timestamp).toString()
            )
            timestamp = System.currentTimeMillis()

            sendCaptureRequest(
                greyImg,
                serverUrl,
                this@BackgroundMatchingService,
                matchingOptions,
                captureCallback = captureCallback
            )
        } else {
            Log.d("NPS", "invalid serverURL")
        }
    }

    private val captureCallback = object : CaptureCallback {
        override fun onPermissionDenied() {
            Log.d("CA", "Permission denied")
        }

        override fun onMatchResult(matchID: String, img: ByteArray){
            onMatch(matchID, img, CaptureModel.getCameraImage())
        }

        override fun onMatchFailure(uid: String) {
        }

        override fun onMatchRequestError() {
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


    private fun onMatch(matchId: String, ba: ByteArray?, original: Bitmap?) {
        CaptureModel.setMatchID(matchId)
        Log.d("NPS_TS", "got response after:" + (System.currentTimeMillis() - timestamp).toString())
        Log.d("NPS", "matchid: $matchId, url: ${ServerConnectionModel.serverUrl.value}")
        Log.d("NPS", "Byte array: ${ba}")
        timestamp = System.currentTimeMillis()
        if (ba != null) {
            val image = BitmapFactory.decodeByteArray(ba, 0, ba.size)
            CaptureModel.setCroppedScreenshot(image)
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
        val startIntent: Intent = Intent(this, ResultsActivity::class.java)
        startIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(this@BackgroundMatchingService, 0, startIntent, 0)


        val notification =
            NotificationCompat.Builder(this@BackgroundMatchingService, notificationChannelId)
                .setSmallIcon(R.drawable.ic_baseline_close_48)
                .setContentTitle("ScreenshotMatcher")
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bmp)
                )
                .setContentIntent(pendingIntent)
                .apply {
                    if (didMatch) this.setContentText("match success") else this.setContentText("NO SCREENSHOT")
                }

        with(NotificationManagerCompat.from(this@BackgroundMatchingService)) {
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