package com.pda.screenshotmatcher2

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

//Views
var mBackButton: AppCompatImageButton? = null
var mPillNavigationButton1: AppCompatButton? = null
var mPillNavigationButton2: AppCompatButton? = null
var mImagePreviewPreviousButton: AppCompatImageButton? = null
var mImagePreviewNextButton: AppCompatImageButton? = null
var mScreenshotImageView: ImageView? = null
var mShareButton: AppCompatImageButton? = null
var mSaveBothButton: AppCompatImageButton? = null
var mSaveOneButton: AppCompatImageButton? = null
var mShareButtonText: TextView? = null
var mSaveOneButtonText: TextView? = null
var mRetakeImageButton: AppCompatButton? = null

// -1 = cropped page, 1 = full page
var mPillNavigationState: Int = -1


var imageDir: File? = null
var mCroppedImageFile: File? = null
var mScreenshotsFileDirectory: String? = null
var mFullImageFile: File? = null
var mServerURL: String? = null
var mFullScreenshotFilename: String? = null
var downloadID: Long? = null



class ResultsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)
        initViews()
        setViewListeners()

        val intent = intent
        mServerURL = intent.getStringExtra("ServerURL")
        downloadID = intent.getLongExtra("DownloadID", 0)

        mScreenshotsFileDirectory = "/storage/emulated/0/${intent.getStringExtra("ScreenshotsDirectoryName")}"
        imageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

        mCroppedImageFile = File("$mScreenshotsFileDirectory/croppedScreenshot.jpg")
        setImageViewBitmapWithFile(mCroppedImageFile)
        this.registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) // TODO>: call this in the onCreate() function of the fragment, that displays the result

    }


    val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("RESULT", "Check Recieve")
            //Fetching the download id received with the broadcast
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            //Checking if the received broadcast is for our enqueued download by matching download id
            if (downloadID == id) {
                mFullImageFile = File("$mScreenshotsFileDirectory/fullScreenshot.png")
            }
        }
    }
    private fun setImageViewBitmapWithFile(mFile: File?) {
        val mBitmap = BitmapFactory.decodeFile(mFile!!.absolutePath)
        if (mBitmap != null) {
            mScreenshotImageView?.setImageBitmap(mBitmap)
        }
    }

    private fun setViewListeners() {
        mBackButton?.setOnClickListener { goBackToCameraActivity() }
        mRetakeImageButton?.setOnClickListener { goBackToCameraActivity() }
        mPillNavigationButton1?.setOnClickListener { if (mPillNavigationState != -1){togglePillNavigationSelection()} }
        mPillNavigationButton2?.setOnClickListener { if (mPillNavigationState != 1){togglePillNavigationSelection()} }
        mImagePreviewPreviousButton?.setOnClickListener { togglePillNavigationSelection() }
        mImagePreviewNextButton?.setOnClickListener { togglePillNavigationSelection() }
        mShareButton?.setOnClickListener { shareImage() }
        mSaveBothButton?.setOnClickListener { saveBothImages() }
        mSaveOneButton?.setOnClickListener { saveCurrentPreviewImage() }
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

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun togglePillNavigationSelection() {
        mPillNavigationState *= -1

        when (mPillNavigationState){
            -1 -> {
                mPillNavigationButton2?.setBackgroundColor(getColor(R.color.invisible))
                mPillNavigationButton1?.background = resources.getDrawable(R.drawable.pill_navigation_selected_item)
                mImagePreviewPreviousButton?.visibility = View.INVISIBLE
                mImagePreviewNextButton?.visibility = View.VISIBLE
                mShareButtonText?.text = getString(R.string.result_activity_shareButtonText1_en)
                mSaveOneButtonText?.text = getString(R.string.result_activity_saveOneButtonText1_en)
                setImageViewBitmapWithFile(mCroppedImageFile)
            }
            1 -> {
                mPillNavigationButton1?.setBackgroundColor(getColor(R.color.invisible))
                mPillNavigationButton2?.background = resources.getDrawable(R.drawable.pill_navigation_selected_item)
                mImagePreviewPreviousButton?.visibility = View.VISIBLE
                mImagePreviewNextButton?.visibility = View.INVISIBLE
                mShareButtonText?.text = getString(R.string.result_activity_shareButtonText2_en)
                mSaveOneButtonText?.text = getString(R.string.result_activity_saveOneButtonText2_en)
                if (mFullImageFile == null){
                    mScreenshotImageView?.setImageDrawable(resources.getDrawable(R.drawable.ic_baseline_downloading_24));
                }   else{
                    setImageViewBitmapWithFile(mFullImageFile)
                }


            }
        }
    }

    private fun goBackToCameraActivity() {
        mCroppedImageFile?.parentFile?.deleteRecursively()
        unregisterReceiver(onDownloadComplete)
        finish()
    }

    private fun initViews() {
        mBackButton = findViewById(R.id.ra_backButton)
        mPillNavigationButton1 = findViewById(R.id.ra_pillNavigation_button1)
        mPillNavigationButton2 = findViewById(R.id.ra_pillNavigation_button2)
        mImagePreviewPreviousButton = findViewById(R.id.ra_imagePreview_previousButton)
        mImagePreviewPreviousButton?.visibility = View.INVISIBLE
        mImagePreviewNextButton = findViewById(R.id.ra_imagePreview_nextButton)
        mImagePreviewNextButton?.visibility = View.VISIBLE
        mScreenshotImageView = findViewById(R.id.ra_imagePreview_imageView)
        mShareButton = findViewById(R.id.ra_shareButton)
        mSaveBothButton = findViewById(R.id.ra_saveBothButton)
        mSaveOneButton = findViewById(R.id.ra_saveOneButton)
        mShareButtonText = findViewById(R.id.ra_shareButtonText)
        mShareButtonText?.text = getString(R.string.result_activity_shareButtonText1_en)
        mSaveOneButtonText = findViewById(R.id.ra_saveOneButtonText)
        mSaveOneButtonText?.text = getString(R.string.result_activity_saveOneButtonText1_en)
        mRetakeImageButton = findViewById(R.id.ra_retakeImageButton)
    }
}