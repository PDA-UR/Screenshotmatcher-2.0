package com.pda.screenshotmatcher2.background

import android.R.attr.path
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.util.Log
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.views.activities.CameraActivity
import java.io.File


class NewPhotoService : Service() {

        private var wakeLock: PowerManager.WakeLock? = null
        private var isServiceStarted = false
        private lateinit var observer: FileObserver

        fun startService() {
            Log.d("NPS", "started fg")
            if (isServiceStarted) return
            isServiceStarted = true
            wakeLock =
                (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                    newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NewPhotoService::lock").apply {
                        acquire()
                    }
                }
            val pathToWatch = File(Environment.getExternalStorageDirectory().toString() + "/" + Environment.DIRECTORY_DCIM + "/")
            observer = object : FileObserver(pathToWatch) {
                // set up a file observer to watch this directory on sd card
                override fun onEvent(event: Int, file: String?) {
                    Log.d("NPS", (event and ALL_EVENTS).toString())
                    when {
                        CREATE and event != 0 -> {
                            Log.d("NPS", "File created [" + pathToWatch.toString() + file.toString() + "]")
                        }
                        MODIFY and event != 0 -> {
                            Log.d("NPS", "modified")
                        }
                        CLOSE_WRITE and event != 0 -> {
                            Log.d("NPS", "close write")
                        }
                        CLOSE_NOWRITE and event != 0 -> {
                            Log.d("NPS", "wrote camera image to file OR Someone had a file or directory open read-only, and closed it")
                        }
                        OPEN and event != 0 -> {
                            Log.d("NPS", "start writing camera image OR opened dir in file exporer")
                        }
                    }
                    //if(event == FileObserver.CREATE && !file.equals(".probe")){ // check if its a "create" and not equal to .probe because thats created every time camera is launched
                    //}
                }
            }
            observer.startWatching() //START OBSERVING
        }
        fun stopService(context: Context) {
            try {
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }
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
        var notification = createNotification()
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

}