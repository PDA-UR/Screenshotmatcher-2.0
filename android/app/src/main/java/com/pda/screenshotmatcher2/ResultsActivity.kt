package com.pda.screenshotmatcher2

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
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
var mCroppedImageFilename: String? = null
var mFullImageFile: File? = null



class ResultsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        initViews()
        setViewListeners()

        val intent = intent
        mCroppedImageFilename = intent.getStringExtra("ScreenshotFilename")
        imageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        mCroppedImageFile = File("$imageDir/$mCroppedImageFilename")

        setImageViewBitmapWithFile(mCroppedImageFile)
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

    private fun togglePillNavigationSelection() {
        mPillNavigationState *= -1

        if (mFullImageFile == null){
            downloadFullImage()
        }

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
                setImageViewBitmapWithFile(mFullImageFile)
            }
        }
    }

    private fun downloadFullImage() {
        mFullImageFile = File("$imageDir/testFullScreenshot.jpg")
    }


    private fun goBackToCameraActivity() {
        mCroppedImageFile?.delete()
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