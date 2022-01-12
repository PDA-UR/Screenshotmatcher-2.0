package com.pda.screenshotmatcher2.views.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.pda.screenshotmatcher2.*
import com.pda.screenshotmatcher2.background.BackgroundMatchingService
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.network.sendLog
import com.pda.screenshotmatcher2.utils.*
import com.pda.screenshotmatcher2.viewModels.CaptureViewModel
import java.io.File

/**
 * Activity for displaying the results of a capture request.
 *
 * @property mBackButton The back button, used to return to the previous activity
 * @property mPillNavigationButton1 The first pill navigation button, used to navigate to the previous image
 * @property mPillNavigationButton2 The second pill navigation button, used to navigate to the next image
 * @property mImagePreviewNextButton The next image button (right), used to navigate to the next image
 * @property mImagePreviewPreviousButton The previous image button (left), used to navigate to the previous image
 * @property mScreenshotImageView The image view displaying the screenshot (either cropped or full)
 * @property mShareButton The share button, calls [shareImage] when clicked
 * @property mSaveBothButton The save both button, calls [saveBothImages] when clicked
 * @property mSaveOneButton The save one button, calls [saveCurrentPreviewImage] when clicked
 * @property mShareButtonText The text view displaying the text beneath [mShareButton]
 * @property mSaveOneButtonText The text view displaying the text beneath [mSaveOneButton]
 * @property mRetakeImageButton The retake image button, used to return to the previous activity
 *
 * @property mFullImageFile The file containing the full screenshot
 * @property mCroppedImageFile The file containing the cropped screenshot
 * @property lastDateTime The date and time when this activity was created, used as a prefix for the file names when saving images
 *
 * @property displayFullScreenshotOnly Whether to only allow the user to view the full screenshot or both the cropped and full screenshots. Set to true if no cropped screenshot is available.
 * @property hasSharedImage Whether the user has shared/saved the image via [mShareButton], [mSaveOneButton] or [mSaveBothButton]
 * @property fullScreenshotDownloaded Whether the full screenshot has been downloaded or not (can be downloaded using [downloadFullScreenshot])
 * @property croppedScreenshotDownloaded Whether the cropped screenshot has been downloaded or not (can't be downloaded if false, because no matching cropped screenshot is available)
 * @property shareIntentIsActive Whether a share intent is currently being processed or not
 *
 * @property captureViewModel The [CaptureViewModel], used to access data of the current matching process
 * @property didRegisterObservers Whether the observers for [captureViewModel] have been registered or not. Necessary to prevent calling evens when registering the observers.
 *
 * @property mPillNavigationState The current state of the pill navigation buttons, used to determine which pill navigation button is currently highlighted (0 = pill 1 = cropped screenshot, 1 = pill 2 = full screenshot)
 * @property StudyLogger The [StudyLogger] used to log the events of this activity
 *
 * @property isReturningToCameraActivity Whether this activity is returning to the camera activity or not, set to true in [goBackToCameraActivity]
 * @property wasStartedFromCameraActivity Whether this activity was started from the camera activity or not
 *
 * @property Consts Constants relevant to this activity
 */
class ResultsActivity : AppCompatActivity() {

    companion object Consts {
        const val RESULT_ACTIVITY_REQUEST_CODE = 20
        const val EXTRA_STARTED_FROM_CAMERA_ACTIVITY = "extra_started_from_camera_activity"
    }

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

    private val lastDateTime: String = getDateString()
    private lateinit var mFullImageFile: File
    private lateinit var mCroppedImageFile: File

    private var displayFullScreenshotOnly: Boolean = false
    private var hasSharedImage: Boolean = false
    private var isReturningToCameraActivity: Boolean = false
    private var wasStartedFromCameraActivity: Boolean = false

    private var fullScreenshotDownloaded = false
    private var croppedScreenshotDownloaded = false
    private var shareIntentIsActive = false

    private lateinit var captureViewModel: CaptureViewModel
    private var didRegisterObservers = false

    private var mPillNavigationState: Int = -1

    /**
     * Initializes the activity and sets up the views as well as the [CaptureViewModel].
     *
     * @param savedInstanceState The saved instance state of the activity
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StudyLogger.hashMap["tc_result_shown"] = System.currentTimeMillis()
        setContentView(R.layout.activity_results)
        initViews()
        setViewListeners()
        initiateCaptureViewModel()

        mFullImageFile = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            lastDateTime + "_Full.png"
        )

        mCroppedImageFile = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            lastDateTime + "_Cropped.png"
        )

        wasStartedFromCameraActivity = intent.getBooleanExtra(EXTRA_STARTED_FROM_CAMERA_ACTIVITY, false)
    }

    /**
     * Initiates [captureViewModel].
     *
     * Retrieves [mCroppedImageFile] and [mFullImageFile] from the [CaptureViewModel] if available.
     * If no [mFullImageFile] is available, [displayFullScreenshotOnly] is set to true.
     * If [mCroppedImageFile] is available, it is displayed in [mScreenshotImageView].
     *
     * @see CaptureViewModel
     */
    private fun initiateCaptureViewModel() {
        captureViewModel = ViewModelProvider(this, CaptureViewModel.Factory(application)).get(
            CaptureViewModel::class.java
        )
        if (captureViewModel.getCroppedScreenshot() == null) {
            // no cropped image available
            Log.d("RA", "cropped = null")
            displayFullScreenshotOnly = true
            activateFullScreenshotOnlyMode()
        } else {
            // cropped image available
            mScreenshotImageView.setImageBitmap(captureViewModel.getCroppedScreenshot())
            croppedScreenshotDownloaded = true
        }
        // register observers
        captureViewModel.getLiveDataFullScreenshot().observe(this) { fullScreenshot ->
            run {
                // prevent calling the event when registering the observer
                if (didRegisterObservers)
                    onFullScreenshotDownloaded(fullScreenshot)
                else didRegisterObservers = true
            }
        }
    }

    /**
     * Activates full screenshot only mode.
     *
     * Downloads the full screenshot from the server and displays it in [mScreenshotImageView].
     * Updates [mPillNavigationState].
     * Updates [mShareButtonText] and [mSaveOneButton] to display the correct text.
     *
     * @return true
     */
    private fun activateFullScreenshotOnlyMode(): Boolean {
        downloadFullScreenshot()
        mImagePreviewPreviousButton.visibility = View.INVISIBLE
        mPillNavigationButton1.setBackgroundColor(getColor(R.color.invisible))
        mPillNavigationButton2.background =
            ContextCompat.getDrawable(this,  R.drawable.pill_navigation_selected_item)
        mImagePreviewNextButton.visibility = View.INVISIBLE
        mShareButtonText.text = getString(R.string.result_activity_shareButtonText2_en)
        mSaveOneButtonText.text = getString(R.string.result_activity_saveOneButtonText2_en)
        mPillNavigationState *= -1
        return true
    }

    /**
     * Initiates all views.
     */
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

    /**
     * Sets all view listeners.
     */
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

    /**
     * Saves the current preview image to the phone gallery.
     */
    private fun saveCurrentPreviewImage() {
        saveToAppDir(captureViewModel.getCroppedScreenshot(), captureViewModel.getFullScreenshot())
        hasSharedImage = true
        //Check if cropped image is available as bitmap, if so save it as a file to app directory. This is necessary so the user can see the the screenshot when browsing older screenshots
        when (mPillNavigationState) {
            -1 -> {
                //Save cropped screenshot to gallery
                saveImageFileToGallery(mCroppedImageFile, getString(R.string.screenshot_description_en), this)
                StudyLogger.hashMap["save_match"] = true
                Toast.makeText(
                    this,
                    getText(R.string.result_activity_saved_cropped_en),
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {
                //Save full screenshot to gallery
                if (fullScreenshotDownloaded) {
                    saveImageFileToGallery(mFullImageFile, getString(R.string.screenshot_description_en), this)
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
                    downloadFullScreenshot()
                }
            }
        }
    }

    /**
     * Saves both the cropped and full screenshot to the phone gallery.
     *
     * Downloads the full screenshot if it is not available.
     */
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
                captureViewModel.getCroppedScreenshot()?.let { saveImage(it, mCroppedImageFile) }

                if (fullScreenshotDownloaded) {
                    captureViewModel.getFullScreenshot()?.let { saveImage(it, mFullImageFile) }
                    Toast.makeText(
                        this,
                        getText(R.string.result_activity_saved_both_en),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    //Full screenshot needs to be downloaded, gets saved to gallery on download complete
                    shareIntentIsActive = true
                    downloadFullScreenshot()
                }
                StudyLogger.hashMap["save_match"] = true
                StudyLogger.hashMap["save_full"] = true
            }
        }
    }


    /**
     * Opens the share intent to share the current preview image.
     */
    private fun shareImage() {
        saveToAppDir(captureViewModel.getCroppedScreenshot(), captureViewModel.getFullScreenshot())
        hasSharedImage = true
        when (mPillNavigationState) {
            -1 -> {
                //Cropped screenshot needs to be shared
                StudyLogger.hashMap["share_match"] = true
                saveToAppDir(captureViewModel.getCroppedScreenshot(), null)
                //Start sharing
                createSharingChooser(mCroppedImageFile, MimeType(MimeTypes.PNG), this)
            }
            else -> {
                if (fullScreenshotDownloaded) {
                    //Full screenshot needs to be shared
                    StudyLogger.hashMap["share_full"] = true
                    saveToAppDir(null, captureViewModel.getFullScreenshot())
                    createSharingChooser(mFullImageFile, MimeType(MimeTypes.PNG), this)
                } else {
                    Toast.makeText(
                        this,
                        getText(R.string.http_download_full_error_en),
                        Toast.LENGTH_LONG
                    ).show()
                    downloadFullScreenshot()
                }
            }
        }
    }



    /**
     * Toggles the pill navigation state and changes the current preview image.
     */
    private fun togglePillNavigationSelection() {
        //Only toggle if cropped screenshot is available
        if (!displayFullScreenshotOnly) {
            mPillNavigationState *= -1
            when (mPillNavigationState) {
                -1 -> {
                    //Switch to cropped screenshot
                    mPillNavigationButton2.setBackgroundColor(getColor(R.color.invisible))
                    mPillNavigationButton1.background =
                        ContextCompat.getDrawable(this,  R.drawable.pill_navigation_selected_item)
                    mImagePreviewPreviousButton.visibility = View.INVISIBLE
                    mImagePreviewNextButton.visibility = View.VISIBLE
                    mShareButtonText.text = getString(R.string.result_activity_shareButtonText1_en)
                    mSaveOneButtonText.text =
                        getString(R.string.result_activity_saveOneButtonText1_en)
                    mScreenshotImageView.setImageBitmap(captureViewModel.getCroppedScreenshot())

                }
                1 -> {
                    //Switch to full screenshot
                    mPillNavigationButton1.setBackgroundColor(getColor(R.color.invisible))
                    mPillNavigationButton2.background =
                        ContextCompat.getDrawable(this,  R.drawable.pill_navigation_selected_item)
                    mImagePreviewPreviousButton.visibility = View.VISIBLE
                    mImagePreviewNextButton.visibility = View.INVISIBLE
                    mShareButtonText.text = getString(R.string.result_activity_shareButtonText2_en)
                    mSaveOneButtonText.text =
                        getString(R.string.result_activity_saveOneButtonText2_en)
                    if (captureViewModel.getFullScreenshot() != null) {
                        mScreenshotImageView.setImageBitmap(captureViewModel.getFullScreenshot())
                    } else {
                        downloadFullScreenshot()
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

    /**
     * Saves an [image] to a [file] and adds it to the gallery.
     *
     * @param image The image to save.
     * @param file The file to save the image to.
     */
    private fun saveImage(image: Bitmap, file: File) {
        if (!file.exists()) saveBitmapToFile(file, image)
        saveImageFileToGallery(file, getString(R.string.screenshot_description_en), this)
    }

    /**
     * Save the screenshots to the app directory.
     *
     * @param croppedScreenshot The cropped screenshot to save.
     * @param fullScreenshot The full screenshot to save.
     */
    private fun saveToAppDir(croppedScreenshot: Bitmap?, fullScreenshot: Bitmap?) {
        if (croppedScreenshot != null) {
            Log.d("ResultActivity", "Saving cropped screenshot")
            saveBitmapToFile(
                mCroppedImageFile,
                croppedScreenshot
            )
        }
        if (fullScreenshot != null) {
            Log.d("ResultActivity", "Saving full screenshot")
            saveBitmapToFile(
                mFullImageFile,
                fullScreenshot
            )
        }
    }


    /**
     * Downloads the  full screenshot from the server via the [captureViewModel]
     */
    private fun downloadFullScreenshot() {
        captureViewModel.loadFullScreenshot()
    }

    /**
     * Callback for the [captureViewModel] to notify the this activity that the full screenshot has been loaded.
     *
     * Calls either [onFullScreenshotDenied] or [onFullScreenshotSuccess] depending on whether [screenshot] is null or not.
     *
     * @param screenshot The full screenshot as a [Bitmap]. Null if the full screenshot could not be loaded.
     */
    private fun onFullScreenshotDownloaded(screenshot: Bitmap?) {
            when (screenshot) {
                null -> onFullScreenshotDenied()
                else -> onFullScreenshotSuccess()
        }

    }

    /**
     * Callback for [onFullScreenshotDownloaded] to notify the user that the full screenshot could not be loaded.
     */
    private fun onFullScreenshotDenied() {
        shareIntentIsActive = false
        Toast.makeText(this, "Full screenshots not allowed by this PC.", Toast.LENGTH_LONG).show()
    }

    /**
     * Callback for [onFullScreenshotDownloaded] to set the full screenshot as the image view save it to the phone gallery if [shareIntentIsActive].
     */
    private fun onFullScreenshotSuccess() {
        fullScreenshotDownloaded = true
        if (displayFullScreenshotOnly || mPillNavigationState == 1) {
            mScreenshotImageView.setImageBitmap(captureViewModel.getFullScreenshot())
        } else if (shareIntentIsActive) {
            //Full screenshot has been requested by the user pressing "save both", save downloaded screenshot to gallery
            captureViewModel.getFullScreenshot()?.let { saveImage(it, mFullImageFile) }
            Toast.makeText(
                applicationContext,
                getText(R.string.result_activity_saved_both_en),
                Toast.LENGTH_SHORT
            ).show()
            shareIntentIsActive = false
        }
    }

    /**
     * Finishes this activity.
     *
     * Sets the result code to [Activity.RESULT_OK] if [hasSharedImage] is true.
     * Otherwise sets the result code to [Activity.RESULT_CANCELED].
     */
    private fun goBackToCameraActivity() {
        if (wasStartedFromCameraActivity) {
            val intent = Intent()
            isReturningToCameraActivity = true
            if (!hasSharedImage) {
                setResult(Activity.RESULT_CANCELED, intent)
            } else {
                if (hasSharedImage) saveToAppDir(captureViewModel.getCroppedScreenshot(), captureViewModel.getFullScreenshot())
                setResult(Activity.RESULT_OK, intent)
            }
        }
        finish()
    }

    /**
     * Sends a log using [sendLog] and clears [StudyLogger.hashMap]
     */
    override fun onStop() {
        super.onStop()
        val doStartBackgroundService  = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.settings_bgMode_key), false)
        if (!isReturningToCameraActivity && doStartBackgroundService) BackgroundMatchingService.startBackgroundService(applicationContext)
        captureViewModel.getServerUrl()?.let {
            sendLog(it, this)
        }
        StudyLogger.hashMap.clear()
    }

    override fun onResume() {

        BackgroundMatchingService.stopBackgroundService(this)
        super.onResume()
    }
}