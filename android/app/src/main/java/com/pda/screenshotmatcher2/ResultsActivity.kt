package com.pda.screenshotmatcher2

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

var croppedScreenshotImageView: ImageView? = null

class ResultsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)
        croppedScreenshotImageView = findViewById(R.id.croppedScreenshotImage)

        val intent = intent
        val croppedFilename: String? = intent.getStringExtra("ScreenshotFilename")
            val imageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val imgFile = File("$imageDir/$croppedFilename")
            val myBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
            if (myBitmap != null){
                croppedScreenshotImageView?.setImageBitmap(myBitmap)
            }
    }
}