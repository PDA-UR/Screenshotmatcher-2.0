package com.pda.screenshotmatcher2

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.core.app.ActivityCompat
import net.gotev.uploadservice.UploadServiceConfig
import java.io.File

class MainActivity : AppCompatActivity() {
    val TEST_FILE_PATH = "/storage/emulated/0/Download/test.jpg"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        verifyPermissions(this)

        createNotificationChannel()
        UploadServiceConfig.initialize(
                context = this.application,
                defaultNotificationChannel = notificationChannelID,
                debug = BuildConfig.DEBUG
        )

        findViewById<Button>(R.id.button).setOnClickListener {
            funcTest()
        }
    }

    private fun funcTest(){
        Log.v("TEST", "button pressed")
        var serverUrl : String = ""
        Thread{
            serverUrl = discoverServerOnNetwork(this, 49050, "")
            onServerURLget(serverUrl)
        }.start()
//            val httpClient = HTTPClient()
    }

    private fun onServerURLget(serverURL : String){
        Thread {
            val greyImg = savePhotoToDisk(null, TEST_FILE_PATH, 512)
            onFileConverted(greyImg, serverURL)
        }.start()
    }

    private fun onFileConverted(file : File, serverURL: String){
        val httpClient = HTTPClient(serverURL, this, this)
        Thread{
            httpClient.sendFileToServer(file.absolutePath)
        }.start()
    }

    companion object {
        const val notificationChannelID = "Screenshotmatcher Channel"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(notificationChannelID, "Screenshot Matcher", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
    }
}