@file:Suppress("PrivatePropertyName", "PrivatePropertyName")

package com.pda.screenshotmatcher2.background

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
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
 * A [ContentObserver] monitors changes in the phone gallery directory and sends match requests once new photos are detected.
 *
 * @property MATCHING_MODE_PREF_KEY The key for the preference that determines the matching mode.
 * @property foregroundNotificationChannelId The id of the foreground notification channel.
 * @property matchNotificationChannelId The id of the match notification channel.
 *
 * @property foregroundNotificationBuilder The notification builder for the foreground notification.
 * @property matchNotificationBuilder The notification builder for the match notification.
 *
 * @property broadcastReceiver [BroadcastReceiver] instance, which receives broadcasts from the system (screen on/off).
 * @property sp [SharedPreferences] instance, stores the preferences.
 *
 *  @property isActive Whether or not the service is active.
 *  @property isWaitingForMatchingResponse Whether or not the service is waiting for a match response from the server.
 *  @property isConnected Whether or not the service is connected to the server.
 *
 *  @property isConnectedObserver [Observer] instance, which observes [ServerConnectionModel.isConnected] and updates [isConnected] accordingly.
 *  @property recentContentObserverPaths Stores the last 10 files dispatched by [contentObserver]. This is done to avoid double processing, because [contentObserver] sometimes fires multiple identical events per file.
 */
class BackgroundMatchingService : Service() {

    private lateinit var MATCHING_MODE_PREF_KEY: String
    private val foregroundNotificationChannelId = "SM_FG_NOTIFICATION_CHANNEL"
    private val matchNotificationChannelId = "SM_MR_NOTIFICATION_CHANNEL"

    private lateinit var foregroundNotificationBuilder: NotificationCompat.Builder
    private lateinit var matchNotificationBuilder: NotificationCompat.Builder

    /**
     * Instance of [ContentObserver] which monitors changes in the phone gallery directory and sends match requests once new photos are detected.
     *
     * New photos are files which fulfill the following criteria at the time of detection:
     * - [isWaitingForMatchingResponse] is false
     * - [isConnected] is true
     * - [isValidFilePath] returns true
     * - [isNewCameraPhoto] returns true
     */
    private var contentObserver: ContentObserver? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private lateinit var sp: SharedPreferences

    private var isActive = false
    private var isWaitingForMatchingResponse = false
    var isConnected: Boolean = false

    private val isConnectedObserver = Observer<Boolean> {
        if (it != isConnected) {
            isConnected = it
            updateForegroundNotification()
        }
    }

    private var recentContentObserverPaths: LinkedList<String> = object : LinkedList<String>() {
        override fun push(e: String?) {
            if (size > 10) removeAt(10)
            super.push(e)
        }
    }


    /**
     * Companion object for starting/stopping the service.
     */
    companion object {
        /**
         * Start the service.
         *
         * @param context The context of the calling activity.
         */
        fun startBackgroundService(context: Context) {
            Intent(context, BackgroundMatchingService::class.java).also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(it)
                    return
                }
                context.startService(it)
            }
        }

        /**
         * Stop the service.
         *
         * @param context The context of the calling activity.
         */
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

    /**
     * Called when the service is created, initializes the service.
     *
     * Starts [contentObserver] and [broadcastReceiver].
     */
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
        createForegroundNotification()
        startForeground(1, foregroundNotificationBuilder.build())
    }

    /**
     * Starts the service.
     */
    private fun startService() {
        if (isActive) return
        isActive = true
        ServerConnectionModel.start(application, false)
        ServerConnectionModel.isConnected.observeForever(isConnectedObserver)
        startContentObserver()
    }

    /**
     * Sleeps the service. Called when the screen is turned off.
     *
     * Stops [contentObserver] and sets [isActive] to false.
     */
    private fun sleepService() {
        if (!isActive) return
        isActive = false
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
        contentObserver = null
    }

    /**
     * Stops the service. Called when the service is destroyed.
     *
     * Calls [sleepService] and unregisters [broadcastReceiver] before stopping the service.
     */
    private fun stopService() {
        sleepService()
        ServerConnectionModel.stopThreads()
        ServerConnectionModel.isConnected.removeObserver(isConnectedObserver)
        this@BackgroundMatchingService.unregisterReceiver(broadcastReceiver)
        this.broadcastReceiver = null
        stopForeground(true)
        stopSelf()
    }

    /**
     * On destroy, calls [stopService].
     */
    override fun onDestroy() {
        stopService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Bind other components here if necessary,
        // Method has to be implemented for Android Services
        return null
    }

    /**
     * Initializes [contentObserver] and registers it.
     */
    private fun startContentObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            @SuppressLint("SimpleDateFormat")
            override fun onChange(selfChange: Boolean, uri: Uri?, flag: Int) {
                //Log.d("BackgroundMatchingService", "ContentObserver onChange")
                //super.onChange(selfChange, uri, flag)
                if (isConnected && uri != null && !isWaitingForMatchingResponse) {
                    //Log.d("BackgroundMatchingService", "ContentObserver onChange: uri != null")
                    val path = getPathFromObserverUri(uri)
                    if (isValidFilePath(path)) {
                        //Log.d("BackgroundMatchingService", "ContentObserver onChange: isValidFilePath")
                        val candidateFile = File(path!!) // path != null, checked in isValidFilePath()
                        if (isNewCameraPhoto(candidateFile))
                            //Log.d("BackgroundMatchingService", "ContentObserver onChange: isNewCameraPhoto")
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

    /**
     * Checks whether a given [path] is a valid file path.
     *
     * @return true if [path] is not in [recentContentObserverPaths] and does not contain "_" at the start of the file name, false otherwise.
     */
    private fun isValidFilePath(path: String?): Boolean {
        //Log.d("BackgroundMatchingService", "isValidFilePath: $path")
        return if (path != null && !recentContentObserverPaths.contains(path) && !path.contains(
                Regex("/_")
            )) {
            recentContentObserverPaths.push(path)
            true
        } else false
    }

    /**
     * Checks whether a given [file] is a new camera photo.
     *
     * @return true if [file] has an [ExifInterface.TAG_DATETIME] tag that is max 10s older than the current time, false otherwise
     */
    @SuppressLint("SimpleDateFormat")
    private fun isNewCameraPhoto(file: File): Boolean {
        val currentTimestamp = Date().time
        val exif = ExifInterface(file.absolutePath)
        val exifDateTimeString = exif.getAttribute(ExifInterface.TAG_DATETIME)
        val exifDateTime = if (exifDateTimeString != null) {
            try {
                SimpleDateFormat("yyyy:MM:dd HH:mm:ss").parse(exifDateTimeString)
            } catch (e: ParseException) {
                return false
            }
        } else return false
        return exifDateTime.time + 10000 > currentTimestamp
    }

    /**
     * Rescales an [image] to 512x512 and calls [sendCaptureRequest] with the scaled image.
     *
     * @param image the image to rescale and send to the server
     */
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

    /**
     * [CaptureCallback] object that is used to handle the response from the server. Used in [rescaleAndSendToServer].
     */
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

    /**
     * Called when [CaptureCallback.onMatchResult] of [captureCallback] is called.
     * Sends a notification to the user with the result of the matching by calling [sendMatchNotification].
     *
     * @param matchId the id of the match
     * @param ba the image of the match as a [ByteArray]
     */
    private fun onMatch(matchId: String, ba: ByteArray?) {
        //Log.d("BMS", "onMatch: $matchId")
        CaptureModel.setMatchID(matchId)
        if (ba != null) {
            val image = BitmapFactory.decodeByteArray(ba, 0, ba.size)
            CaptureModel.setCroppedScreenshot(image)
            sendMatchNotification(image)
        }
    }

    /**
     * Send a match notification to the user with the result of the matching process.
     *
     * Calls [createMatchNotification] if no match notification has been sent before,
     * otherwise calls [updateMatchNotification].
     *
     * @param bmp the screenshot image of the match
     * @see [onMatch]
     */
    private fun sendMatchNotification(bmp: Bitmap) {
        if (!::matchNotificationBuilder.isInitialized) createMatchNotification(bmp)
        else updateMatchNotification(bmp)

        NotificationManagerCompat.from(this@BackgroundMatchingService)
            .notify(2, matchNotificationBuilder.build())
    }

    /**
     * Creates a notification channel for the match notification as well as the notification itself.
     *
     * @param matchResult the image of the match
     * @see [sendMatchNotification]
     */
    private fun createMatchNotification(matchResult: Bitmap) {
        createMatchNotificationChannel()

        val startIntent = Intent(this, ResultsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(ResultsActivity.EXTRA_STARTED_FROM_BG_SERVICE, true)
        }
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(this@BackgroundMatchingService, 1, startIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        matchNotificationBuilder = NotificationCompat.Builder(
            this@BackgroundMatchingService,
            matchNotificationChannelId
        )
            .setSmallIcon(R.drawable.ic_baseline_photo_camera_24)
            .setLargeIcon(matchResult)
            .setContentTitle(getString(R.string.bgMode_match_notification_title))
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(matchResult)
            )
            .setChannelId(matchNotificationChannelId)
            .setContentIntent(pendingIntent)
            .setContentText(getString(R.string.bgMode_match_notification_text))
    }

    /**
     * Creates the notification channel for the match notification.
     *
     * @see [createMatchNotification]
     */
    private fun createMatchNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val matchNotificationChannel = NotificationChannel(
                matchNotificationChannelId,
                getString(R.string.bgMode_match_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            val matchNotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            matchNotificationManager.createNotificationChannel(matchNotificationChannel)
        }
    }

    /**
     * Updates the match notification with the new image of the match.
     *
     * @param matchResult the image of the match
     * @see [sendMatchNotification]
     */
    private fun updateMatchNotification (matchResult: Bitmap) {
        matchNotificationBuilder
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(matchResult)
            )
    }

    /**
     * Creates a notification channel for the foreground notification as well as the notification itself.
     *
     * @see [onCreate]
     */
    private fun createForegroundNotification() {
        createForegroundNotificationChannel()

        val pendingIntent: PendingIntent =
            Intent(this, CameraActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        if(!::foregroundNotificationBuilder.isInitialized){
            foregroundNotificationBuilder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) NotificationCompat.Builder(
                this,
                foregroundNotificationChannelId
            ) else {
                @Suppress("DEPRECATION") // checked for above
                NotificationCompat.Builder(this) // for pre-O versions
                }

            foregroundNotificationBuilder
                .setContentTitle(getString(R.string.bgMode_fg_notification_title))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentText(getConnectedStatusString())
                .setContentIntent(pendingIntent)
                .setSmallIcon(getConnectedStatusIcon())
        }
    }

    /**
     * Creates the notification channel for the foreground notification.
     *
     * @see [createForegroundNotification]
     */
    private fun createForegroundNotificationChannel() {
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
    }

    /**
     * Updates the foreground notification with the new connected status.
     *
     * @see [isConnectedObserver]
     */
    private fun updateForegroundNotification() {
        foregroundNotificationBuilder.apply {
            setContentText(getConnectedStatusString())
            setSmallIcon(getConnectedStatusIcon())
        }
        NotificationManagerCompat.from(this@BackgroundMatchingService).notify(1, foregroundNotificationBuilder.build())
    }

    /**
     * Returns the icon representing the corresponding connected status.
     *
     * @return the icon of the connected status
     */
    private fun getConnectedStatusIcon(): Int {
        return if (ServerConnectionModel.isConnected.value!!) R.drawable.ic_icon_notification_connected else R.drawable.ic_icon_notification_disconnected
    }

    /**
     * Returns the string representing the corresponding connected status.
     *
     * @return the string of the connected status
     */
    private fun getConnectedStatusString(): String {
        return if (ServerConnectionModel.isConnected.value!!) getString(R.string.bgMode_fg_notification_connected)
        else getString(R.string.bgMode_fg_notification_disconnected)
    }

    /**
     * Returns the matching options for the match request.
     * @return the matching options for the match request
     */
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

    /**
     * Decode a sampled bitmap from a [file] to a [Bitmap].
     *
     * @param file the file to decode
     * @param reqWidth the min required width of the decoded bitmap
     * @param reqHeight the min required height of the decoded bitmap
     * @return the decoded bitmap
     *
     * @see [Loading Large Bitmaps Efficiently](https://developer.android.com/topic/performance/graphics/load-bitmap)
     */
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

    /**
     * Calculates the inSampleSize for the bitmap decoding.
     *
     * @param options the options of the bitmap decoding
     * @param reqWidth the min required width of the decoded bitmap
     * @param reqHeight the min required height of the decoded bitmap
     * @return the inSampleSize for the bitmap decoding
     * @see [decodeSampledBitmapFromResource]
     */
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


    /**
     * Returns the file path of a given [uri].
     *
     * Calls [queryRelativeDataColumn] if the build version is greater than or equal to [Build.VERSION_CODES.Q],
     * otherwise calls [queryAbsoluteDataColumn].
     *
     * @param uri the uri to get the file path from
     * @return the file path of the given uri
     */
    fun getPathFromObserverUri(uri: Uri): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryRelativeDataColumn(uri)
        } else {
            queryAbsoluteDataColumn(uri)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun queryRelativeDataColumn(uri: Uri): String? {
        //Log.d("BMS", "queryRelativeDataColumn: $uri")
        var relativePath: String? = null
        var name: String?
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
                //Log.d("BMS", "queryRelativeDataColumn name: $name")
                relativePath = "${Environment.getExternalStorageDirectory()}/${
                    cursor.getString(
                        relativePathColumn
                    )
                }$name"
            }
        }
        return relativePath
    }

    @Suppress("DEPRECATION") // Taken care of in getPathFromObserverUri
    private fun queryAbsoluteDataColumn(uri: Uri): String? {
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