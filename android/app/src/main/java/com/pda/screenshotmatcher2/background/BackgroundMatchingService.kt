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
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Observer
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
 * @property isActive Indicates whether or not the service is active
 * @property contentObserver [ContentObserver] instance, which monitors the gallery
 * @property sp [SharedPreferences] instance, stores matching options
 * @property foregroundNotificationChannelId The channel id used to send notification
 */
class BackgroundMatchingService : Service() {
    private var isActive = false
    private var contentObserver: ContentObserver? = null
    private lateinit var sp: SharedPreferences
    private lateinit var MATCHING_MODE_PREF_KEY: String
    private val foregroundNotificationChannelId = "SM_FG_NOTIFICATION_CHANNEL"
    private val matchResultNotificationChannelId = "SM_MR_NOTIFICATION_CHANNEL"
    private var broadcastReceiver: BroadcastReceiver? = null
    private var isWaitingForMatchingResponse = false
    private lateinit var foregroundNotificationBuilder: Notification.Builder

    val isConnectedObserver = Observer<Boolean> {
        if (it != isConnected) {
            isConnected = it
            foregroundNotificationBuilder.apply {
                setContentText(getConnectedStatusString())
                setSmallIcon(getConnectedStatusIcon())
            }
            NotificationManagerCompat.from(this@BackgroundMatchingService).notify(1, foregroundNotificationBuilder.build())
            Log.d("BackgroundMatchingService", "isConnectedObserver: $it")
        }
    }
    var isConnected: Boolean? = false

    private var matchNotificationChannel: NotificationChannel? = null
    private var matchNotificationManager: NotificationManager? = null

    /**
     * Stores the last 10 files dispatched by [contentObserver] in the variable [recentContentObserverPaths]. This is done to avoid double processing, because [contentObserver] sometimes fires multiple identical events per file.
     */
    companion object {
        // list to keep track of recently checked files to avoid double processing (content observer fires multiple identical events per file)
        var recentContentObserverPaths: LinkedList<String> = object : LinkedList<String>() {
            override fun push(e: String?) {
                if (size > 10) removeAt(10)
                super.push(e)
            }
        }

        fun startBackgroundService(context: Context) {
            Intent(context, BackgroundMatchingService::class.java).also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(it)
                    return
                }
                context.startService(it)
            }
        }

        fun stopBackgroundService(context: Context) {
            Intent(context, BackgroundMatchingService::class.java).also {
                context.stopService(it)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startService()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> sleepService()
                    Intent.ACTION_SCREEN_ON -> startService()
                }
            }
        }
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        this@BackgroundMatchingService.registerReceiver(broadcastReceiver, intentFilter)
        initForegroundNotification()
        startForeground(1, foregroundNotificationBuilder.build())
    }

    private fun startService() {
        if (isActive) return
        isActive = true
        ServerConnectionModel.start(application, false)

        ServerConnectionModel.isConnected.observeForever(isConnectedObserver)
        startContentObserver()
    }

    private fun sleepService() {
        if (!isActive) return
        isActive = false
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
        contentObserver = null
        ServerConnectionModel.stopThreads()
        ServerConnectionModel.isConnected.removeObserver(isConnectedObserver)
    }

    private fun stopService() {
        sleepService()
        this@BackgroundMatchingService.unregisterReceiver(broadcastReceiver)
        this.broadcastReceiver = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Bind other components here if necessary,
        // Method has to be implemented for Android Services
        return null
    }

    private fun startContentObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            @SuppressLint("SimpleDateFormat")
            override fun onChange(selfChange: Boolean, uri: Uri?, flag: Int) {
                super.onChange(selfChange, uri, flag)
                val serverUrl = ServerConnectionModel.serverUrl.value
                if (serverUrl != null && serverUrl != "" && uri != null && !isWaitingForMatchingResponse) {
                    val path = getPathFromObserverUri(uri)
                    if (path != null && !recentContentObserverPaths.contains(path) && !path.contains(
                            Regex("/_")
                        )
                    ) {
                        recentContentObserverPaths.push(path)
                        val candidateFile = File(path)
                        if (!isNewCameraPhoto(candidateFile)) return
                        rescaleAndSendToServer(
                            image = decodeSampledBitmapFromResource(
                                candidateFile,
                                512,
                                512
                            )
                        )
                    }
                }
            }
        }
        contentObserver.let {
            if (it != null) {
                contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    it
                )
            }
        }
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
        val greyImg = rescale(
            image,
            512
        )
        val serverUrl = ServerConnectionModel.serverUrl.value
        if (serverUrl != null && serverUrl != "") {
            CaptureModel.clear()
            CaptureModel.setCameraImage(image)
            CaptureModel.setServerURL(serverUrl)

            val matchingOptions: HashMap<Any?, Any?> =
                getMatchingOptionsFromPref()
            isWaitingForMatchingResponse = true
            sendCaptureRequest(
                greyImg,
                serverUrl,
                this@BackgroundMatchingService.applicationContext,
                matchingOptions,
                captureCallback = captureCallback
            )
        }
    }

    private val captureCallback = object : CaptureCallback {
        override fun onPermissionDenied() {
            Toast.makeText(
                this@BackgroundMatchingService,
                getText(R.string.match_request_perm_denied),
                Toast.LENGTH_LONG
            ).show()
            isWaitingForMatchingResponse = false
        }

        override fun onMatchResult(matchID: String, img: ByteArray) {
            isWaitingForMatchingResponse = false
            onMatch(matchID, img)
        }

        override fun onMatchFailure(uid: String) {
            isWaitingForMatchingResponse = false
        }

        override fun onMatchRequestError() {
            isWaitingForMatchingResponse = false
        }
    }

    private fun onMatch(matchId: String, ba: ByteArray?) {
        CaptureModel.setMatchID(matchId)
        if (ba != null) {
            val image = BitmapFactory.decodeByteArray(ba, 0, ba.size)
            CaptureModel.setCroppedScreenshot(image)
            sendMatchNotification(image)
        }
    }

    private fun sendMatchNotification(bmp: Bitmap) {
        if (matchNotificationChannel == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                matchNotificationChannel = NotificationChannel(
                    matchResultNotificationChannelId,
                    getString(R.string.bgMode_match_notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
                matchNotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                matchNotificationManager?.createNotificationChannel(matchNotificationChannel!!)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) matchNotificationManager?.notify(
            2,
            createMatchNotification(bmp)
        )
        else NotificationManagerCompat.from(this@BackgroundMatchingService)
            .notify(2, createMatchNotification(bmp))
    }

    private fun createMatchNotification(matchResult: Bitmap): Notification {
        val startIntent = Intent(this, ResultsActivity::class.java)
        startIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(this@BackgroundMatchingService, 1, startIntent, 0)

        return NotificationCompat.Builder(
            this@BackgroundMatchingService,
            matchResultNotificationChannelId
        )
            .setSmallIcon(R.drawable.ic_baseline_photo_camera_24)
            .setLargeIcon(matchResult)
            .setContentTitle(getString(R.string.bgMode_match_notification_title))
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(matchResult)
            )
            .setChannelId(matchResultNotificationChannelId)
            .setContentIntent(pendingIntent)
            .setContentText(getString(R.string.bgMode_match_notification_text))
            .build()
    }

    private fun initForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                foregroundNotificationChannelId,
                getString(R.string.bgMode_fg_notification_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).let {
                it.description = getString(R.string.bgMode_fg_notification_description)
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

        if(!::foregroundNotificationBuilder.isInitialized){
            foregroundNotificationBuilder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
                this,
                foregroundNotificationChannelId
            ) else Notification.Builder(this) // for pre-O versions
            foregroundNotificationBuilder
                .setContentTitle(getString(R.string.bgMode_fg_notification_title))
                .setContentText(getConnectedStatusString())
                .setContentIntent(pendingIntent)
                .setSmallIcon(getConnectedStatusIcon())
        }
    }

    private fun getConnectedStatusIcon(): Int {
        return if (ServerConnectionModel.isConnected.value!!) R.drawable.ic_icon_notification_connected else R.drawable.ic_icon_notification_disconnected
    }

    private fun getConnectedStatusString(): String {
        return if (ServerConnectionModel.isConnected.value!!) getString(R.string.bgMode_fg_notification_connected)
        else getString(R.string.bgMode_fg_notification_disconnected)
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
                returnPath = path
            }
        }
        return returnPath
    }
}