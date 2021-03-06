package com.pda.screenshotmatcher2

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import java.io.File


class ResultsActivity : AppCompatActivity() {
    //Views
    private lateinit var mBackButton: AppCompatImageButton
    private lateinit var mPillNavigationButton1: AppCompatButton
    private lateinit var mPillNavigationButton2: AppCompatButton
    private lateinit var mImagePreviewPreviousButton: AppCompatImageButton
    private lateinit var mImagePreviewNextButton: AppCompatImageButton
    private lateinit var mScreenshotImageView: ImageView
    private lateinit var mShareButton: AppCompatImageButton
    private lateinit var mSaveBothButton: AppCompatImageButton
    private lateinit var mSaveOneButton: AppCompatImageButton
    private lateinit var mShareButtonText: TextView
    private lateinit var mSaveOneButtonText: TextView
    private lateinit var mRetakeImageButton: AppCompatButton

    private lateinit var mFullImageFile: File
    private lateinit var mServerURL: String
    private lateinit var lastDateTime : String
    private lateinit var mCroppedScreenshot : Bitmap
    private lateinit var mFullScreenshot : Bitmap

    // -1 = cropped page, 1 = full page
    private var mPillNavigationState: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)
        initViews()
        setViewListeners()

        mServerURL = intent.getStringExtra("ServerURL")!!

        val imgByteArray = intent.getByteArrayExtra("img")!!
        mCroppedScreenshot = BitmapFactory.decodeByteArray(imgByteArray, 0, imgByteArray.size)
        val matchID = intent.getStringExtra("matchID")!!
        mScreenshotImageView.setImageBitmap(mCroppedScreenshot)

        lastDateTime = getDateString()
        Thread{downloadFullScreenshot(matchID, lastDateTime, mServerURL, applicationContext)}.start()
        this.registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("RESULT", "Check Receive")

            //Fetching the download id received with the broadcast
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadID == id) {
                mFullImageFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), lastDateTime + "_Full.png")
                mFullScreenshot = BitmapFactory.decodeFile(mFullImageFile.absolutePath)
            }
        }
    }

    private fun setViewListeners() {
        mBackButton.setOnClickListener { goBackToCameraActivity() }
        mRetakeImageButton.setOnClickListener { goBackToCameraActivity() }
        mPillNavigationButton1.setOnClickListener { if (mPillNavigationState != -1){togglePillNavigationSelection()} }
        mPillNavigationButton2.setOnClickListener { if (mPillNavigationState != 1){togglePillNavigationSelection()} }
        mImagePreviewPreviousButton.setOnClickListener { togglePillNavigationSelection() }
        mImagePreviewNextButton.setOnClickListener { togglePillNavigationSelection() }
        mShareButton.setOnClickListener { shareImage() }
        mSaveBothButton.setOnClickListener { saveBothImages() }
        mSaveOneButton.setOnClickListener { saveCurrentPreviewImage() }
    }

    private fun saveCurrentPreviewImage() {
        TODO("Not yet implemented")
    }

    private fun saveBothImages() {
        TODO("Not yet implemented")
    }

    private fun shareImage() {
        TODO("Not yet implemented")
    }

    private fun togglePillNavigationSelection() {
        mPillNavigationState *= -1

        when (mPillNavigationState){
            -1 -> {
                mPillNavigationButton2.setBackgroundColor(getColor(R.color.invisible))
                mPillNavigationButton1.background = resources.getDrawable(R.drawable.pill_navigation_selected_item)
                mImagePreviewPreviousButton.visibility = View.INVISIBLE
                mImagePreviewNextButton.visibility = View.VISIBLE
                mShareButtonText.text = getString(R.string.result_activity_shareButtonText1_en)
                mSaveOneButtonText.text = getString(R.string.result_activity_saveOneButtonText1_en)
                mScreenshotImageView.setImageBitmap(mCroppedScreenshot)
            }
            1 -> {
                mPillNavigationButton1.setBackgroundColor(getColor(R.color.invisible))
                mPillNavigationButton2.background = resources.getDrawable(R.drawable.pill_navigation_selected_item)
                mImagePreviewPreviousButton.visibility = View.VISIBLE
                mImagePreviewNextButton.visibility = View.INVISIBLE
                mShareButtonText.text = getString(R.string.result_activity_shareButtonText2_en)
                mSaveOneButtonText.text = getString(R.string.result_activity_saveOneButtonText2_en)
                mScreenshotImageView.setImageBitmap(mFullScreenshot)
            }
        }
    }

    private fun goBackToCameraActivity() {
        unregisterReceiver(onDownloadComplete)
        finish()
    }

    private fun initViews() {
        mBackButton = findViewById(R.id.ra_backButton)
        mPillNavigationButton1 = findViewById(R.id.ra_pillNavigation_button1)
        mPillNavigationButton2 = findViewById(R.id.ra_pillNavigation_button2)
        mImagePreviewPreviousButton = findViewById(R.id.ra_imagePreview_previousButton)
        mImagePreviewPreviousButton.visibility = View.INVISIBLE
        mImagePreviewNextButton = findViewById(R.id.ra_imagePreview_nextButton)
        mImagePreviewNextButton.visibility = View.VISIBLE
        mScreenshotImageView = findViewById(R.id.ra_imagePreview_imageView)
        mShareButton = findViewById(R.id.ra_shareButton)
        mSaveBothButton = findViewById(R.id.ra_saveBothButton)
        mSaveOneButton = findViewById(R.id.ra_saveOneButton)
        mShareButtonText = findViewById(R.id.ra_shareButtonText)
        mShareButtonText.text = getString(R.string.result_activity_shareButtonText1_en)
        mSaveOneButtonText = findViewById(R.id.ra_saveOneButtonText)
        mSaveOneButtonText.text = getString(R.string.result_activity_saveOneButtonText1_en)
        mRetakeImageButton = findViewById(R.id.ra_retakeImageButton)
    }
}