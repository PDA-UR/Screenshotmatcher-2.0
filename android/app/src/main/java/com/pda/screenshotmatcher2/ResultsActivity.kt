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
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.FileProvider.getUriForFile
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
    private lateinit var mCroppedImageFile: File
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
        supportActionBar?.hide()
        setViewListeners()

        mServerURL = intent.getStringExtra("ServerURL")!!

        val imgByteArray = intent.getByteArrayExtra("img")!!
        mCroppedScreenshot = BitmapFactory.decodeByteArray(imgByteArray, 0, imgByteArray.size)
        val matchID = intent.getStringExtra("matchID")!!
        mScreenshotImageView.setImageBitmap(mCroppedScreenshot)
        Log.v("TIMING", "Result screen shown.")
        StudyLogger.hashMap["tc_result_shown"] = System.currentTimeMillis()
        lastDateTime = getDateString()
        Thread{downloadFullScreenshot(matchID, lastDateTime, mServerURL, applicationContext)}.start()
        this.registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    override fun onStop() {
        super.onStop()
        //TODO: send log data to server
        sendLog(mServerURL, this)
        StudyLogger.hashMap.clear()
    }

    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("RESULT", "Check Receive")
            //Fetching the download id received with the broadcast
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadID == id) {
                Log.v("TIMING", "full screenshot downloaded")
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
        if(mPillNavigationState == -1){
            MediaStore.Images.Media.insertImage(
                contentResolver,
                mCroppedScreenshot,
                getString(R.string.cropped_screenshot_title_en),
                getString(R.string.screenshot_description_en)
            )
            StudyLogger.hashMap["save_match"] = true
            Toast.makeText(this, getText(R.string.result_activity_saved_cropped_en), Toast.LENGTH_SHORT).show()
        }   else {
            MediaStore.Images.Media.insertImage(
                contentResolver,
                mFullScreenshot,
                getString(R.string.full_screenshot_title_en),
                getString(R.string.screenshot_description_en)
            )
            StudyLogger.hashMap["save_full"] = true
            Toast.makeText(this, getText(R.string.result_activity_saved_full_en), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBothImages() {
        MediaStore.Images.Media.insertImage(
            contentResolver,
            mCroppedScreenshot,
            getString(R.string.cropped_screenshot_title_en),
            getString(R.string.screenshot_description_en)
        )

        MediaStore.Images.Media.insertImage(
            contentResolver,
            mFullScreenshot,
            getString(R.string.full_screenshot_title_en),
            getString(R.string.screenshot_description_en)
        )
        StudyLogger.hashMap["save_match"] = true
        StudyLogger.hashMap["save_full"] = true
        Toast.makeText(this, getText(R.string.result_activity_saved_both_en), Toast.LENGTH_SHORT).show()
    }

    private fun shareImage() {
        if(mPillNavigationState == -1){
            StudyLogger.hashMap["share_match"] = true
            mCroppedImageFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), lastDateTime + "_Cropped.png")
            saveBitmapToFile(mCroppedImageFile, mCroppedScreenshot)
            val contentUri =
                getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", mCroppedImageFile)
            val intent = Intent().apply {
                this.action = Intent.ACTION_SEND
                this.putExtra(Intent.EXTRA_STREAM, contentUri)
                this.type = "image/png"
                this.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }   else {
            StudyLogger.hashMap["share_full"] = true
            val contentUri =
                getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", mFullImageFile)
            val intent = Intent().apply {
                this.action = Intent.ACTION_SEND
                this.putExtra(Intent.EXTRA_STREAM, contentUri)
                this.type = "image/png"
                this.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }
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
        Log.v("TEST", StudyLogger.hashMap.toString())
        sendLog(mServerURL, this)
        StudyLogger.hashMap.clear()
        //TODO: send log data to server
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