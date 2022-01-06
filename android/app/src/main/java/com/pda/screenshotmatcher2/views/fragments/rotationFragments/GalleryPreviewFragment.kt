package com.pda.screenshotmatcher2.views.fragments.rotationFragments

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.pda.screenshotmatcher2.BuildConfig
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.views.activities.CameraActivity
import com.pda.screenshotmatcher2.viewModels.GalleryViewModel
import java.io.File

const val FIRST_IMAGE_KEY: String = "FIRST_IMAGE"
const val SECOND_IMAGE_KEY: String = "SECOND_IMAGE"

/**
 * [RotationFragment] that displays a capture result image pair.
 *
 * Displayed in [GalleryFragment] when the user clicks on a capture result image pair.
 *
 * @property mPillNavigationButton1 The first pill navigation button, used to navigate to the previous image
 * @property mPillNavigationButton2 The second pill navigation button, used to navigate to the next image
 * @property mImagePreviewNextButton The next image button (right), used to navigate to the next image
 * @property mImagePreviewPreviousButton The previous image button (left), used to navigate to the previous image
 * @property mScreenshotImageView The image view displaying the screenshot (either cropped or full)
 * @property mShareButton The share button, calls [shareImage] when clicked
 * @property mDeleteBoth The delete both button, calls [deleteBothImages] when clicked
 * @property mSaveOneButton The save one button, calls [saveCurrentPreviewImage] when clicked
 * @property mShareButtonText The text view displaying the text beneath [mShareButton]
 * @property mSaveOneButtonText The text view displaying the text beneath [mSaveOneButton]
 *
 * @property mFullImageFile The file containing the full screenshot
 * @property mCroppedImageFile The file containing the cropped screenshot
 *
 * @property mPillNavigationState The current state of the pill navigation buttons, used to determine which pill navigation button is currently highlighted (0 = pill 1 = cropped screenshot, 1 = pill 2 = full screenshot)
 * @property StudyLogger The [StudyLogger] used to log the events of this activity
 *
 * @property mFragmentBackground The dark background behind the fragment
 * @property galleryViewModel The [GalleryViewModel] used to obtain old capture result image pairs
 * @property numberOfAvailableImages The number of available images in the capture result image pair (1 or 2)
 */
class GalleryPreviewFragment : RotationFragment() {

    private var mPillNavigationButton1: AppCompatButton? = null
    private var mPillNavigationButton2: AppCompatButton? = null
    private var mImagePreviewPreviousButton: AppCompatImageButton? = null
    private var mImagePreviewNextButton: AppCompatImageButton? = null
    private var mScreenshotImageView: ImageView? = null
    private var mShareButton: AppCompatImageButton? = null
    private var mDeleteBoth: AppCompatImageButton? = null
    private var mSaveOneButton: AppCompatImageButton? = null
    private var mShareButtonText: TextView? = null
    private var mSaveOneButtonText: TextView? = null
    private var mFragmentBackground: FrameLayout? = null

    private var mPillNavigationState: Int = 1

    private var mFullImageFile: File? = null
    private var mCroppedImageFile: File? = null
    private var numberOfAvailableImages: Int = 0
    private lateinit var oldBundle: Bundle
    private var galleryViewModel: GalleryViewModel? = null

    /**
     * Returns an inflated [View] for this fragment.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val bundle: Bundle? = this.arguments

        if (savedInstanceState != null) {
            getFilesFromBundle(savedInstanceState)
        } else if (bundle != null) {
            getFilesFromBundle(bundle)
        }

        containerView = container as FrameLayout
        return inflater.inflate(R.layout.fragment_gallery_preview, container, false)
    }

    /**
     * Called when the fragment is created, initializes all views, listeners and the [galleryViewModel].
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        setViewListeners()
        galleryViewModel = ViewModelProvider(requireActivity(), GalleryViewModel.Factory(requireActivity().application))
        .get(GalleryViewModel::class.java)

    }

    /**
     * Receives all image [File]s from a [bundle], and saves them in [mFullImageFile] and [mCroppedImageFile].
     */
    private fun getFilesFromBundle(bundle: Bundle) {
        oldBundle = bundle
        val keys = arrayOf(FIRST_IMAGE_KEY, SECOND_IMAGE_KEY)
        keys.forEach { key ->
            if (bundle.getSerializable(key) != null) {
                val retrievedFile: File? = bundle.getSerializable(key) as File?
                when (retrievedFile?.absolutePath?.split("_".toRegex())?.last()) {
                    "Full.png" -> {
                        mFullImageFile = retrievedFile
                        mPillNavigationState = 1
                    }
                    "Cropped.png" -> {
                        mCroppedImageFile = retrievedFile
                        mPillNavigationState = -1
                    }
                }
                numberOfAvailableImages++
            }
         }
    }

    /**
     * Toggles the [mPillNavigationState] between 0 and 1.
     *
     * If [numberOfAvailableImages] is 1, the pill navigation buttons are disabled.
     */
    private fun togglePillNavigationSelection() {
        if (numberOfAvailableImages > 1) {
            mPillNavigationState *= -1
            updatePillNavigation()
        } else {
            Toast.makeText(
                requireActivity(),
                getText(R.string.preview_fragment_only_one_available_en),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Updates views to match the [mPillNavigationState].
     *
     * If [mPillNavigationState] is 1, the [mCroppedImageFile] is shown.
     * If [mPillNavigationState] is -1, the [mFullImageFile] is shown.
     */
    private fun updatePillNavigation() {
        when (mPillNavigationState) {
            -1 -> {
                //Switch to cropped screenshot
                mPillNavigationButton2?.setBackgroundColor(requireActivity().getColor(
                    R.color.invisible
                ))
                mPillNavigationButton1?.background  =
                    resources.getDrawable(R.drawable.pill_navigation_selected_item)
                if (numberOfAvailableImages == 2) {
                    mImagePreviewNextButton?.visibility = View.VISIBLE
                    mImagePreviewPreviousButton?.visibility = View.INVISIBLE
                }
                mShareButtonText?.text = getString(R.string.result_activity_shareButtonText1_en)
                mSaveOneButtonText?.text =
                    getString(R.string.result_activity_saveOneButtonText1_en)
                setImage()
            }
            1 -> {
                //Switch to full screenshot
                mPillNavigationButton1?.setBackgroundColor(requireActivity().getColor(
                    R.color.invisible
                ))
                mPillNavigationButton2?.background =
                    resources.getDrawable(R.drawable.pill_navigation_selected_item)
                if (numberOfAvailableImages == 2) {
                    mImagePreviewPreviousButton?.visibility = View.VISIBLE
                    mImagePreviewNextButton?.visibility = View.INVISIBLE
                }

                mShareButtonText?.text = getString(R.string.result_activity_shareButtonText2_en)
                mSaveOneButtonText?.text = getString(R.string.result_activity_saveOneButtonText2_en)
                setImage()
            }
        }
    }

    /**
     * Sets the [mScreenshotImageView] to the [mFullImageFile] or [mCroppedImageFile] depending on the [mPillNavigationState].
     */
    private fun setImage() {
        when (mPillNavigationState) {
            -1 -> {
                mScreenshotImageView?.let {
                    Glide.with(requireActivity())
                        .load(mCroppedImageFile)
                        .into(it)
                }
            }
            1 -> {
                mScreenshotImageView?.let {
                    Glide.with(requireActivity())
                        .load(mFullImageFile)
                        .into(it)
                }
            }
        }
    }

    /**
     * Saves either [mFullImageFile] or [mCroppedImageFile] to the phone gallery, depending on the [mPillNavigationState].
     */
    private fun saveCurrentPreviewImage() {
        when (mPillNavigationState) {
            -1 -> {
                if (mCroppedImageFile != null) {
                    MediaStore.Images.Media.insertImage(
                        requireContext().contentResolver,
                        mCroppedImageFile?.absolutePath,
                        mCroppedImageFile?.name,
                        getString(R.string.screenshot_description_en)
                    )
                    Toast.makeText(
                        requireContext(),
                        getText(R.string.result_activity_saved_cropped_en),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            1 -> {
                if (mFullImageFile != null) {
                    MediaStore.Images.Media.insertImage(
                        requireContext().contentResolver,
                        mFullImageFile?.absolutePath,
                        mFullImageFile?.name,
                        getString(R.string.screenshot_description_en)
                    )
                    Toast.makeText(
                        requireContext(),
                        getText(R.string.result_activity_saved_full_en),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Deletes both [mFullImageFile] and [mCroppedImageFile] from the external app directory and closes this fragment.
     */
    private fun deleteBothImages() {
        val images = ArrayList<File>()
        images.apply {
            mCroppedImageFile?.let {
                Log.d("GF", "del cropped")
                this.add(it)
            }
            mFullImageFile?.let {
                Log.d("GF", "del full")
                this.add(it)
            }
        }
        galleryViewModel?.deleteImagePair(images)
        removeThisFragment(true)
    }

    /**
     * Opens the share intent to share the current preview image.
     */
    private fun shareImage() {
        if (mPillNavigationState == -1 && mCroppedImageFile != null) {
            //Start sharing
            val contentUri =
                FileProvider.getUriForFile(
                    requireContext(),
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    mCroppedImageFile!!
                )
            val sendIntent = ShareCompat.IntentBuilder.from(requireContext() as CameraActivity)
                .setType("image/png")
                .setStream(contentUri)
                .createChooserIntent()


            val resInfoList: List<ResolveInfo> = requireContext().packageManager
                .queryIntentActivities(sendIntent, PackageManager.MATCH_DEFAULT_ONLY)

            for (resolveInfo in resInfoList) {
                val packageName: String = resolveInfo.activityInfo.packageName
                requireContext().grantUriPermission(
                    packageName,
                    contentUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val shareIntent = Intent.createChooser(sendIntent,null)
            startActivity(shareIntent)


            //val shareIntent = Intent.createChooser(sendIntent,"Share")
            startActivity(shareIntent)

        } else {
            if (mFullImageFile != null) {
                val contentUri =
                    FileProvider.getUriForFile(
                        requireContext(),
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        mFullImageFile!!
                    )
                val sendIntent = Intent().apply {
                    this.action = Intent.ACTION_SEND
                    this.putExtra(Intent.EXTRA_STREAM, contentUri)
                    this.type = "image/png"
                    this.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val shareIntent = Intent.createChooser(sendIntent,null)
                startActivity(shareIntent)
            }
        }
    }

    /**
     * Initiates all views and calls [updatePillNavigation] to initiate the pill navigation.
     */
    private fun initViews() {
        mFragmentBackground = activity?.findViewById(R.id.gallery_fragment_preview_background)!!
        mFragmentBackground?.setOnClickListener { removeThisFragment(true) }
        mFragmentBackground?.visibility = View.VISIBLE
        mPillNavigationButton1 = activity?.findViewById(R.id.pf_pillNavigation_button1)!!
        mPillNavigationButton2 = activity?.findViewById(R.id.pf_pillNavigation_button2)!!
        mImagePreviewPreviousButton = activity?.findViewById(R.id.pf_imagePreview_previousButton)!!
        mImagePreviewNextButton = activity?.findViewById(R.id.pf_imagePreview_nextButton)!!
        mScreenshotImageView = activity?.findViewById(R.id.pf_imagePreview_imageView)!!
        mShareButton = activity?.findViewById(R.id.pf_shareButton)!!
        mDeleteBoth = activity?.findViewById(R.id.pf_deleteImages)!!
        mSaveOneButton = activity?.findViewById(R.id.pf_saveOneButton)!!
        mShareButtonText = activity?.findViewById(R.id.pf_shareButtonText)!!
        mShareButtonText?.text = getString(R.string.result_activity_shareButtonText1_en)
        mSaveOneButtonText = activity?.findViewById(R.id.pf_saveOneButtonText)!!
        mSaveOneButtonText?.text = getString(R.string.result_activity_saveOneButtonText1_en)
        updatePillNavigation()
    }

    /**
     * Sets all view listeners.
     */
    private fun setViewListeners() {
        mPillNavigationButton1?.setOnClickListener {
            if (mPillNavigationState != -1) {
                togglePillNavigationSelection()
            }
        }
        mPillNavigationButton2?.setOnClickListener {
            if (mPillNavigationState != 1) {
                togglePillNavigationSelection()
            }
        }
        mImagePreviewPreviousButton?.setOnClickListener { togglePillNavigationSelection() }
        mImagePreviewNextButton?.setOnClickListener { togglePillNavigationSelection() }
        mShareButton?.setOnClickListener { shareImage() }
        mDeleteBoth?.setOnClickListener { deleteBothImages() }
        mSaveOneButton?.setOnClickListener { saveCurrentPreviewImage() }
    }


    /**
     * Removes this fragment and returns the displayed image pair.
     *
     * @return The displayed image pair
     */
    override fun removeThisFragmentForRotation(): ArrayList<File?> {
        super.removeThisFragmentForRotation()
        return arrayListOf(mCroppedImageFile, mFullImageFile)
    }

    /**
     * Remove this fragment to rotate it.
     *
     * @param removeBackground Whether to remove the dark background behind the fragment or not
     */
    override fun removeThisFragment(removeBackground: Boolean) {
        requireActivity().window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        if (removeBackground) mFragmentBackground?.visibility = View.INVISIBLE
        super.removeThisFragment(removeBackground)
        clearGarbage()
    }

    override fun clearGarbage() {
        galleryViewModel = null
        containerView = null
        mPillNavigationButton1?.setOnClickListener(null)
        mPillNavigationButton2?.setOnClickListener(null)
        mImagePreviewPreviousButton?.setOnClickListener(null)
        mImagePreviewNextButton?.setOnClickListener(null)
        mSaveOneButton?.setOnClickListener(null)
        mShareButton?.setOnClickListener(null)
        mDeleteBoth?.setOnClickListener(null)
        mFragmentBackground?.setOnClickListener(null)
        mFragmentBackground = null
    }

}