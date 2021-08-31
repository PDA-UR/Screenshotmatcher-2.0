package com.pda.screenshotmatcher2.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider.getUriForFile
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.pda.screenshotmatcher2.*
import com.pda.screenshotmatcher2.helpers.base64ToBitmap
import com.pda.screenshotmatcher2.helpers.getDateString
import com.pda.screenshotmatcher2.helpers.saveBitmapToFile
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.network.SCREENSHOT_DEST
import com.pda.screenshotmatcher2.network.sendLog
import org.json.JSONObject
import java.io.File

const val RESULT_ACTIVITY_REQUEST_CODE = 20
const val RESULT_ACTIVITY_RESULT_CODE = "Result_Result"

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
    private lateinit var lastDateTime: String
    private lateinit var mCroppedScreenshot: Bitmap
    private lateinit var mFullScreenshot: Bitmap
    private lateinit var matchID: String

    private var displayFullScreenshotOnly: Boolean = false
    private var hasSharedImage: Boolean = false

    private var fullScreenshotDownloaded = false
    private var croppedScreenshotDownloaded = false
    private var waitingForFullScreenshot = false

    // -1 = cropped page, 1 = full page
    private var mPillNavigationState: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)
        initViews()
        setViewListeners()

        mServerURL = intent.getStringExtra("ServerURL")!!
        matchID = intent.getStringExtra("matchID")!!

        StudyLogger.hashMap["tc_result_shown"] = System.currentTimeMillis()
        lastDateTime = getDateString()

        //if there is no cropped image, enter full screenshot only mode, not switching between images possible
        if (intent.hasExtra("img")) {
            val imgByteArray = intent.getByteArrayExtra("img")!!
            mCroppedScreenshot = BitmapFactory.decodeByteArray(imgByteArray, 0, imgByteArray.size)
            mScreenshotImageView.setImageBitmap(mCroppedScreenshot)
            saveCroppedImageToAppDir()
            croppedScreenshotDownloaded = true
        } else {
            displayFullScreenshotOnly = activateFullScreenshotOnlyMode()
        }
    }

    private fun initViews() {
        supportActionBar?.hide()
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

    private fun setViewListeners() {
        mBackButton.setOnClickListener { goBackToCameraActivity() }
        mRetakeImageButton.setOnClickListener { goBackToCameraActivity() }
        mPillNavigationButton1.setOnClickListener {
            if (mPillNavigationState != -1) togglePillNavigationSelection()
        }
        mPillNavigationButton2.setOnClickListener {
            if (mPillNavigationState != 1) togglePillNavigationSelection()
        }
        mImagePreviewPreviousButton.setOnClickListener { togglePillNavigationSelection() }
        mImagePreviewNextButton.setOnClickListener { togglePillNavigationSelection() }
        mShareButton.setOnClickListener { shareImage() }
        mSaveBothButton.setOnClickListener { saveBothImages() }
        mSaveOneButton.setOnClickListener { saveCurrentPreviewImage() }
    }

    //Saves the currently displayed screenshot to the gallery
    private fun saveCurrentPreviewImage() {
        hasSharedImage = true
        //Check if cropped image is available as bitmap, if so save it as a file to app directory. This is necessary so the user can see the the screenshot when browsing older screenshots
        if (!displayFullScreenshotOnly) saveCroppedImageToAppDir()

        Log.d("RA", mPillNavigationState.toString())
        when (mPillNavigationState) {
            -1 -> {
                //Save cropped screenshot to gallery
                MediaStore.Images.Media.insertImage(
                    contentResolver,
                    mCroppedScreenshot,
                    mCroppedImageFile.name,
                    getString(R.string.screenshot_description_en)
                )
                StudyLogger.hashMap["save_match"] = true
                Toast.makeText(
                    this,
                    getText(R.string.result_activity_saved_cropped_en),
                    Toast.LENGTH_SHORT
                ).show()
            }
           else -> {
               saveFullImageToAppDir()
               //Save full screenshot to gallery
               if (fullScreenshotDownloaded) {
                   MediaStore.Images.Media.insertImage(
                       contentResolver,
                       mFullScreenshot,
                       mFullImageFile.name,
                       getString(R.string.screenshot_description_en)
                   )
                   StudyLogger.hashMap["save_full"] = true
                   Toast.makeText(
                       this,
                       getText(R.string.result_activity_saved_full_en),
                       Toast.LENGTH_SHORT
                   ).show()
               } else {
                   Toast.makeText(
                       this,
                       getText(R.string.http_download_full_error_en),
                       Toast.LENGTH_LONG
                   ).show()
                   downloadFullScreenshotInThread()
               }
           }
        }
    }

    private fun saveBothImages() {
        when (displayFullScreenshotOnly) {
            true -> {
                //Notify user if no cropped screenshot is available
                Toast.makeText(
                    this,
                    getText(R.string.result_activity_only_full_available_en),
                    Toast.LENGTH_SHORT
                ).show()
            }
            false -> {
                hasSharedImage = true
                //Save cropped screenshot to app directory and then to gallery
                saveCroppedImageToAppDir()
                MediaStore.Images.Media.insertImage(
                    contentResolver,
                    mCroppedScreenshot,
                    mCroppedImageFile.name,
                    getString(R.string.screenshot_description_en)
                )
                if (fullScreenshotDownloaded) {
                    saveFullImageToAppDir()
                    //Save full screenshot if it has been downloaded already
                    MediaStore.Images.Media.insertImage(
                        contentResolver,
                        mFullScreenshot,
                        mFullImageFile.name,
                        getString(R.string.screenshot_description_en)
                    )
                    Toast.makeText(
                        this,
                        getText(R.string.result_activity_saved_both_en),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    //Full screenshot needs to be downloaded, gets saved to gallery on download complete
                    waitingForFullScreenshot = true
                    downloadFullScreenshotInThread()
                }
                StudyLogger.hashMap["save_match"] = true
                StudyLogger.hashMap["save_full"] = true
            }
        }
    }

    private fun shareImage() {
        hasSharedImage = true
        when (mPillNavigationState) {
            -1 -> {
                //Cropped screenshot needs to be shared
                StudyLogger.hashMap["share_match"] = true

                saveCurrentPreviewImage()
                //Start sharing
                val contentUri =
                    getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", mCroppedImageFile)

                val shareIntent = ShareCompat.IntentBuilder.from(this)
                    .setType("image/png")
                    .setStream(contentUri)
                    .createChooserIntent()

                val resInfoList: List<ResolveInfo> = this.packageManager
                    .queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)

                for (resolveInfo in resInfoList) {
                    val packageName: String = resolveInfo.activityInfo.packageName
                    grantUriPermission(
                        packageName,
                        contentUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                //val shareIntent = Intent.createChooser(sendIntent,"Share")
                startActivity(shareIntent)
            }
            else -> {
                if (fullScreenshotDownloaded) {
                    //Full screenshot needs to be shared
                    StudyLogger.hashMap["share_full"] = true

                    saveFullImageToAppDir()
                    //Start sharing
                    val contentUri =
                        getUriForFile(
                            this,
                            BuildConfig.APPLICATION_ID + ".fileprovider",
                            mFullImageFile
                        )
                    val sendIntent = Intent().apply {
                        this.action = Intent.ACTION_SEND
                        this.putExtra(Intent.EXTRA_STREAM, contentUri)
                        this.type = "image/png"
                        this.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val shareIntent = Intent.createChooser(sendIntent,null)
                    startActivity(shareIntent)
                } else {
                    Toast.makeText(
                        this,
                        getText(R.string.http_download_full_error_en),
                        Toast.LENGTH_LONG
                    ).show()
                    downloadFullScreenshotInThread()
                }
            }
        }
    }

    private fun togglePillNavigationSelection() {
        //Only toggle if cropped screenshot is available
        if (!displayFullScreenshotOnly) {
            mPillNavigationState *= -1
            when (mPillNavigationState) {
                -1 -> {
                    //Switch to cropped screenshot
                    mPillNavigationButton2.setBackgroundColor(getColor(R.color.invisible))
                    mPillNavigationButton1.background =
                        resources.getDrawable(R.drawable.pill_navigation_selected_item)
                    mImagePreviewPreviousButton.visibility = View.INVISIBLE
                    mImagePreviewNextButton.visibility = View.VISIBLE
                    mShareButtonText.text = getString(R.string.result_activity_shareButtonText1_en)
                    mSaveOneButtonText.text =
                        getString(R.string.result_activity_saveOneButtonText1_en)
                    mScreenshotImageView.setImageBitmap(mCroppedScreenshot)

                }
                1 -> {
                    //Switch to full screenshot
                    mPillNavigationButton1.setBackgroundColor(getColor(R.color.invisible))
                    mPillNavigationButton2.background =
                        resources.getDrawable(R.drawable.pill_navigation_selected_item)
                    mImagePreviewPreviousButton.visibility = View.VISIBLE
                    mImagePreviewNextButton.visibility = View.INVISIBLE
                    mShareButtonText.text = getString(R.string.result_activity_shareButtonText2_en)
                    mSaveOneButtonText.text =
                        getString(R.string.result_activity_saveOneButtonText2_en)
                    if (::mFullScreenshot.isInitialized) {
                        mScreenshotImageView.setImageBitmap(mFullScreenshot)
                    } else {
                        downloadFullScreenshotInThread()
                    }
                }
            }
        } else {
            //Switching disabled if no cropped screenshot is available
            Toast.makeText(
                this,
                getText(R.string.result_activity_only_full_available_en),
                Toast.LENGTH_SHORT
            ).show()
        }

    }

    private fun saveCroppedImageToAppDir() {
        if (!::mCroppedImageFile.isInitialized && !displayFullScreenshotOnly) {
            mCroppedImageFile = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                lastDateTime + "_Cropped.png"
            )
            saveBitmapToFile(
                mCroppedImageFile,
                mCroppedScreenshot
            )
        }
    }

    private fun saveFullImageToAppDir() {
        if (!::mFullImageFile.isInitialized) {
            mFullImageFile = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                lastDateTime + "_Full.png"
            )
            saveBitmapToFile(
                mFullImageFile,
                mFullScreenshot
            )
        }
    }

    private fun downloadFullScreenshotInThread() {
        Thread {
            downloadFullScreenshot(
                matchID,
                lastDateTime,
                mServerURL,
                applicationContext,
            )
        }.start()
    }

    private fun onScreenshotDownloaded(bitmap: Bitmap){
        fullScreenshotDownloaded = true
        mFullScreenshot = bitmap
        if (displayFullScreenshotOnly || mPillNavigationState == 1) {
            mScreenshotImageView.setImageBitmap(mFullScreenshot)
        }

        else if(waitingForFullScreenshot){
            saveFullImageToAppDir()
            //Full screenshot has been requested by the user pressing "save both", save downloaded screenshot to gallery
            MediaStore.Images.Media.insertImage(
                contentResolver,
                mFullScreenshot,
                getString(R.string.full_screenshot_title_en),
                getString(R.string.screenshot_description_en)
            )
            Toast.makeText(
                applicationContext,
                getText(R.string.result_activity_saved_both_en),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    //full screenshot only mode = when the user clicks "full image" in the error fragment, no cropped screenshot available
    private fun activateFullScreenshotOnlyMode(): Boolean {
        downloadFullScreenshotInThread()
        mImagePreviewPreviousButton.visibility = View.INVISIBLE
        mPillNavigationButton1.setBackgroundColor(getColor(R.color.invisible))
        mPillNavigationButton2.background =
            resources.getDrawable(R.drawable.pill_navigation_selected_item)
        mImagePreviewNextButton.visibility = View.INVISIBLE
        mShareButtonText.text = getString(R.string.result_activity_shareButtonText2_en)
        mSaveOneButtonText.text = getString(R.string.result_activity_saveOneButtonText2_en)
        mPillNavigationState *= -1
        return true
    }

    private fun goBackToCameraActivity() {
        val intent = Intent()
        var resultData: ArrayList<File> = ArrayList()
        if (!hasSharedImage) {
            if (::mFullImageFile.isInitialized) {
                mFullImageFile.delete()
            }
            if (::mCroppedImageFile.isInitialized) {
                mCroppedImageFile.delete()
            }
            setResult(Activity.RESULT_CANCELED, intent)
        } else {
            if (::mCroppedImageFile.isInitialized) {
                resultData.add(mCroppedImageFile)
            }
            if (::mFullImageFile.isInitialized) {
                resultData.add(mFullImageFile)
            }
            intent.putExtra(RESULT_ACTIVITY_RESULT_CODE, resultData)
            setResult(Activity.RESULT_OK, intent)
        }
        finish()
    }

    private fun downloadFullScreenshot(matchID: String, filename: String, serverURL: String, context: Context){
        lateinit var screenshot : Bitmap
        val queue = Volley.newRequestQueue(context)
        val json = JSONObject()
        json.put("match_id", matchID)
        val jsonOR = JsonObjectRequest(
            Request.Method.POST, serverURL + SCREENSHOT_DEST, json,
            { response ->
                if(response.has("error")) {
                    if(response.getString("error") == "disabled_by_host_error") {
                        onFullScreenshotDenied()
                    }
                }
                else {
                    val b64String: String = response.get("result").toString()
                    screenshot =
                        base64ToBitmap(
                            b64String
                        )
                    onScreenshotDownloaded(screenshot)
                }
            },
            { error ->
                error.printStackTrace()
            })

        queue.add(jsonOR)
    }

    private fun onFullScreenshotDenied() {
        Toast.makeText(this, "Full screenshots not allowed by this PC.", Toast.LENGTH_LONG).show()
    }

    override fun onStop() {
        super.onStop()

        sendLog(mServerURL, this)
        StudyLogger.hashMap.clear()
    }
}