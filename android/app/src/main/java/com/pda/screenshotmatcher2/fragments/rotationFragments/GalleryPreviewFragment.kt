package com.pda.screenshotmatcher2.fragments.rotationFragments

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
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
import com.pda.screenshotmatcher2.BuildConfig
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.activities.CameraActivity
import java.io.File

const val FIRST_IMAGE_KEY: String = "FIRST_IMAGE"
const val SECOND_IMAGE_KEY: String = "SECOND_IMAGE"

class GalleryPreviewFragment : RotationFragment() {
    //Views
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

    lateinit var mFragmentBackground: FrameLayout

    // -1 = cropped page, 1 = full page
    private var mPillNavigationState: Int = 1

    //Files
    private var mFullImageFile: File? = null
    private var mCroppedImageFile: File? = null
    private var numberOfAvailableImages: Int = 0
    private lateinit var oldBundle: Bundle

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        var bundle: Bundle? = this.arguments

        if (savedInstanceState != null) {
            getFilesFromBundle(savedInstanceState)
        } else if (bundle != null) {
            getFilesFromBundle(bundle)
        }

        containerView = container as FrameLayout
        return inflater.inflate(R.layout.fragment_gallery_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        setViewListeners()
    }

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

    private fun togglePillNavigationSelection() {
        if (numberOfAvailableImages > 1) {
            mPillNavigationState *= -1
            setupPillNavigationState()
        } else {
            Toast.makeText(
                requireActivity(),
                getText(R.string.preview_fragment_only_one_available_en),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupPillNavigationState() {
        when (mPillNavigationState) {
            -1 -> {
                //Switch to cropped screenshot
                mPillNavigationButton2.setBackgroundColor(requireActivity().getColor(
                    R.color.invisible
                ))
                mPillNavigationButton1.background =
                    resources.getDrawable(R.drawable.pill_navigation_selected_item)
                if (numberOfAvailableImages == 2) {
                    mImagePreviewNextButton.visibility = View.VISIBLE
                    mImagePreviewPreviousButton.visibility = View.INVISIBLE
                }
                mShareButtonText.text = getString(R.string.result_activity_shareButtonText1_en)
                mSaveOneButtonText.text =
                    getString(R.string.result_activity_saveOneButtonText1_en)
                setImage()
            }
            1 -> {
                //Switch to full screenshot
                mPillNavigationButton1.setBackgroundColor(requireActivity().getColor(
                    R.color.invisible
                ))
                mPillNavigationButton2.background =
                    resources.getDrawable(R.drawable.pill_navigation_selected_item)
                if (numberOfAvailableImages == 2) {
                    mImagePreviewPreviousButton.visibility = View.VISIBLE
                    mImagePreviewNextButton.visibility = View.INVISIBLE
                }

                mShareButtonText.text = getString(R.string.result_activity_shareButtonText2_en)
                mSaveOneButtonText.text = getString(R.string.result_activity_saveOneButtonText2_en)
                setImage()
            }
        }
    }

    private fun setImage() {
        when (mPillNavigationState) {
            -1 -> {
                mScreenshotImageView.setImageBitmap(BitmapFactory.decodeFile(mCroppedImageFile?.absolutePath))
            }
            1 -> {
                mScreenshotImageView.setImageBitmap(BitmapFactory.decodeFile(mFullImageFile?.absolutePath))
            }
        }
    }

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

    private fun saveBothImages() {
        val cameraActivity: CameraActivity = requireActivity() as CameraActivity
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
        cameraActivity.deleteImagesFromInternalGallery(images)
        removeThisFragment(true)
    }

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

    private fun initViews() {
        mFragmentBackground = activity?.findViewById(R.id.gallery_fragment_preview_background)!!
        mFragmentBackground.setOnClickListener { removeThisFragment(true) }
        mFragmentBackground.visibility = View.VISIBLE
        mPillNavigationButton1 = activity?.findViewById(R.id.pf_pillNavigation_button1)!!
        mPillNavigationButton2 = activity?.findViewById(R.id.pf_pillNavigation_button2)!!
        mImagePreviewPreviousButton = activity?.findViewById(R.id.pf_imagePreview_previousButton)!!
        mImagePreviewNextButton = activity?.findViewById(R.id.pf_imagePreview_nextButton)!!
        mScreenshotImageView = activity?.findViewById(R.id.pf_imagePreview_imageView)!!
        mShareButton = activity?.findViewById(R.id.pf_shareButton)!!
        mSaveBothButton = activity?.findViewById(R.id.pf_deleteImages)!!
        mSaveOneButton = activity?.findViewById(R.id.pf_saveOneButton)!!
        mShareButtonText = activity?.findViewById(R.id.pf_shareButtonText)!!
        mShareButtonText.text = getString(R.string.result_activity_shareButtonText1_en)
        mSaveOneButtonText = activity?.findViewById(R.id.pf_saveOneButtonText)!!
        mSaveOneButtonText.text = getString(R.string.result_activity_saveOneButtonText1_en)
        setupPillNavigationState()
    }

    private fun setViewListeners() {
        mPillNavigationButton1.setOnClickListener {
            if (mPillNavigationState != -1) {
                togglePillNavigationSelection()
            }
        }
        mPillNavigationButton2.setOnClickListener {
            if (mPillNavigationState != 1) {
                togglePillNavigationSelection()
            }
        }
        mImagePreviewPreviousButton.setOnClickListener { togglePillNavigationSelection() }
        mImagePreviewNextButton.setOnClickListener { togglePillNavigationSelection() }
        mShareButton.setOnClickListener { shareImage() }
        mSaveBothButton.setOnClickListener { saveBothImages() }
        mSaveOneButton.setOnClickListener { saveCurrentPreviewImage() }
    }

    override fun removeThisFragment(removeBackground: Boolean) {
        super.removeThisFragment(removeBackground)
        if (removeBackground) mFragmentBackground.visibility = View.INVISIBLE
    }
    override fun removeThisFragmentForRotation(): ArrayList<File?> {
        super.removeThisFragmentForRotation()
        return arrayListOf(mCroppedImageFile, mFullImageFile)
    }
}