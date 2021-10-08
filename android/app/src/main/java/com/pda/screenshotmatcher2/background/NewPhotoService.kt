package com.pda.screenshotmatcher2.background

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.views.activities.CameraActivity
import java.io.File


class NewPhotoService : Service() {

        private var wakeLock: PowerManager.WakeLock? = null
        private var isServiceStarted = false
        private lateinit var observer: FileObserver
        private lateinit var contentObserver: ContentObserver

        private fun startService() {
            Log.d("NPS", "started fg")
            if (isServiceStarted) return
            isServiceStarted = true
            // Prevent Doze mode
            //acquireDozeLock()
            val pathToWatch = File(Environment.getExternalStorageDirectory().toString() + "/" + Environment.DIRECTORY_DCIM + "/")
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
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
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

        val pendingIntent: PendingIntent = Intent(this, CameraActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            notificationChannelId
        ) else Notification.Builder(this)

        return builder
            .setContentTitle("Endless Service")
            .setContentText("This is your favorite endless service working")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
            .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }


    fun startContentObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri != null) {
                    getPathFromObserverUri(uri)?.let { Log.d("NPS", it) }
                }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
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
                val name = cursor.getString(displayNameColumn)
                relativePath = cursor.getString(relativePathColumn) + "/" + name
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
                        Log.d("NPS", "File created [" + pathToWatch.toString() + fileName.toString() + "]")
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
                    acquire(10*60*1000L /*10 minutes*/)
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