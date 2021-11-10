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
import com.bumptech.glide.Glide
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.models.ServerConnectionModel
import com.pda.screenshotmatcher2.network.sendBitmap2
import com.pda.screenshotmatcher2.utils.rescale
import com.pda.screenshotmatcher2.views.activities.CameraActivity
import java.io.File
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.thread


class NewPhotoService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private lateinit var observer: FileObserver
    private lateinit var contentObserver: ContentObserver
    private var lastPath: String = ""
    private lateinit var sp: SharedPreferences
    private lateinit var MATCHING_MODE_PREF_KEY: String
    private var timestamp: Long = 0

    companion object {
        var lastPaths: LinkedList<String> = object : LinkedList<String>() {
            override fun push(e: String?) {
                if (size > 10) removeAt(10)
                super.push(e)
            }
        }
    }


    private fun startService() {
        Log.d("NPS", "started fg")
        if (isServiceStarted) return
        isServiceStarted = true
        ServerConnectionModel.start(application, false)
        // Prevent Doze mode
        //acquireDozeLock()
        val pathToWatch = File(
            Environment.getExternalStorageDirectory()
                .toString() + "/" + Environment.DIRECTORY_DCIM + "/"
        )
        startContentObserver()
    }

    fun stopService(context: Context) {
        try {
            // release doze mode lock
            //releaseDozeLock()
            contentResolver.unregisterContentObserver(contentObserver)
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            Log.d("NPS", "Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startService()
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        //Bind other components here
        return null
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Endless Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Endless Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
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
            .setTicker("Ticker text")
            .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }


    private fun startContentObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri != null) {
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

                        //glide



                        //val ogBitmap = BitmapFactory.decodeFile(file.absolutePath)
                        val image = decodeSampledBitmapFromResource(file, 512, 512)
                        //val mImage = MediaStore.Images.Media.getBitmap(contentResolver,uri)
                        //Log.d("NPS", image.width.toString())
                        Log.d(
                            "NPS_TS",
                            "loaded og bitmap " + (System.currentTimeMillis() - timestamp).toString()
                        )
                        timestamp = System.currentTimeMillis()
                        /*
                        //val image = BitmapFactory.decodeFile(file.path)
                        val THUMBSIZE = 512

                        *//*val image = ThumbnailUtils.extractThumbnail(
                             ogBitmap,
                             THUMBSIZE,
                             THUMBSIZE
                         )*//*
                        Log.d(
                            "NPS_TS",
                            "decoded bitmap from file after:" + (System.currentTimeMillis() - timestamp).toString()
                        )*/

                        rescaleAndSendToServer(image = image)



                    } else {
                        path?.let { Log.d("NPS", "Not printed: $path") }
                    }
                }
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
            val matchingOptions: java.util.HashMap<Any?, Any?>? =
                getMatchingOptionsFromPref()
            Log.d(
                "NPS_TS",
                "Sending bitmap after: " + (System.currentTimeMillis() - timestamp).toString()
            )
            timestamp = System.currentTimeMillis()
            sendBitmap2(
                greyImg,
                serverUrl,
                this@NewPhotoService,
                matchingOptions,
                null,
                null,
                ::onMatch
            )
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

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
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
            //val image = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val notification =
                NotificationCompat.Builder(this@NewPhotoService, "ENDLESS SERVICE CHANNEL")
                    .setSmallIcon(R.drawable.ic_baseline_close_48)
                    .setContentTitle("ScreenshotMatcher")
                    .setContentText("Looks like you took a screenshot!")
                    .setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(image)
                    )

            with(NotificationManagerCompat.from(this@NewPhotoService)) {
                // notificationId is a unique int for each notification that you must define
                notify(1, notification.build())
            }
            Log.d(
                "NPS_TS",
                "sent notification after: " + (System.currentTimeMillis() - timestamp).toString()
            )
            timestamp = System.currentTimeMillis()
        } else {
            val notification =
                NotificationCompat.Builder(this@NewPhotoService, "ENDLESS SERVICE CHANNEL")
                    .setSmallIcon(R.drawable.ic_baseline_close_48)
                    .setContentTitle("ScreenshotMatcher")
                    .setContentText("NO SCREENSHOT")
                    .setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(original)
                    )

            with(NotificationManagerCompat.from(this@NewPhotoService)) {
                // notificationId is a unique int for each notification that you must define
                notify(1, notification.build())
            }
        }

    }

    private fun getMatchingOptionsFromPref(): HashMap<Any?, Any?>? {
        if (!::sp.isInitialized) {
            sp = PreferenceManager.getDefaultSharedPreferences(this)
            MATCHING_MODE_PREF_KEY = getString(R.string.settings_algorithm_key)
        }
        val matchingMode: HashMap<Any?, Any?>? = HashMap()
        val fastMatchingMode: Boolean = sp.getBoolean(MATCHING_MODE_PREF_KEY, true)

        if (fastMatchingMode) {
            matchingMode?.set(
                getString(R.string.algorithm_key_server),
                getString(R.string.algorithm_fast_mode_name_server)
            )
        } else {
            matchingMode?.set(
                getString(R.string.algorithm_key_server),
                getString(R.string.algorithm_accurate_mode_name_server)
            )
        }
        return matchingMode
    }

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


    fun queryDataColumn(uri: Uri): String? {
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

    fun startFileObserver(pathToWatch: File) {
        observer = object : FileObserver(pathToWatch) {
            // set up a file observer to watch this directory on sd card
            override fun onEvent(event: Int, fileName: String?) {
                Log.d("NPS", (event and ALL_EVENTS).toString())
                if (fileName != null) {
                    Log.d("NPS", fileName)
                }
                when {
                    // bitwise AND operation needed to check
                    CREATE and event != 0 -> {
                        Log.d(
                            "NPS",
                            "File created [" + pathToWatch.toString() + fileName.toString() + "]"
                        )
                    }
                    MODIFY and event != 0 -> {
                        Log.d("NPS", "modified")
                    }
                    CLOSE_WRITE and event != 0 -> {
                        Log.d("NPS", "close write")
                    }
                    CLOSE_NOWRITE and event != 0 -> {
                        Log.d("NPS", "Took photo: ${pathToWatch}$fileName")
                    }
                    OPEN and event != 0 -> {
                        Log.d("NPS", "start writing camera image OR opened dir in file exporer")
                    }
                    event == FileObserver.CREATE -> Log.d("NPS", "CREATE")
                }
            }
        }
        observer.startWatching() //START OBSERVING
    }

    fun acquireDozeLock() {
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NewPhotoService::lock").apply {
                    acquire(10 * 60 * 1000L /*10 minutes*/)
                }
            }
    }

    fun releaseDozeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

}