package com.pda.screenshotmatcher2.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.activities.CameraActivity
import com.pda.screenshotmatcher2.activities.RESULT_ACTIVITY_REQUEST_CODE
import com.pda.screenshotmatcher2.activities.ResultsActivity
import com.pda.screenshotmatcher2.fragments.*
import com.pda.screenshotmatcher2.fragments.rotationFragments.GalleryFragment
import com.pda.screenshotmatcher2.fragments.rotationFragments.RotationFragment
import com.pda.screenshotmatcher2.fragments.rotationFragments.SelectDeviceFragment
import java.io.File

class FragmentHandler(context: Context, activity: Activity) {

    private lateinit var ca: CameraActivity
    private val context = context
    private val activity = activity
    private var mFragmentDarkBackground: FrameLayout
    private var fm: FragmentManager

    init {
        if (activity is CameraActivity) ca = activity
        mFragmentDarkBackground = ca.findViewById(R.id.ca_dark_background)
        fm = ca.supportFragmentManager
    }

    fun removeAllFragments(): Int {
        val frags: List<Fragment> = fm.fragments
        var numOfRemovedFragments = 0
        if (frags != null) {
            for (frag: Fragment in frags) {
                when(frag){
                    is RotationFragment -> {
                        frag.removeThisFragment(true)
                    }
                    else -> {
                        fm.beginTransaction().remove(frag).commit()
                    }
                }
                numOfRemovedFragments += 1
            }
        }
        return numOfRemovedFragments
    }

    fun rotateAllRotatableFragments() {
        rotateGalleryFragment()
        rotateSelectDeviceFragment()
    }

    private fun rotateGalleryFragment() {
        val gFrag: GalleryFragment? =
            fm.findFragmentByTag("GalleryFragment") as GalleryFragment?
        if (gFrag != null && gFrag.isVisible &&gFrag.getOrientation() != ca.phoneOrientation){
            var savedImageFiles = gFrag.removeThisFragmentForRotation()
            openGalleryFragment(false)
            if (savedImageFiles != null) {
                openGalleryPreviewFragment(savedImageFiles[0], savedImageFiles[1])
            }
        }
    }

    private fun rotateSelectDeviceFragment() {
        val sdFrag: SelectDeviceFragment? =
            fm.findFragmentByTag("SelectDeviceFragment") as SelectDeviceFragment?
        if (sdFrag != null && sdFrag.isVisible &&sdFrag.getOrientation() != ca.phoneOrientation){
            sdFrag.removeThisFragmentForRotation()
            openSelectDeviceFragment(false)
        }
    }

    fun startResultsActivity(matchID: String, img: ByteArray) {
        val intent = Intent(context, ResultsActivity::class.java).apply {
            putExtra("matchID", matchID)
            putExtra("img", img)
            putExtra("ServerURL", ca.getServerUrl())
            // putExtra("DownloadID", downloadID)
        }
        ca.startActivityForResult(intent,
            RESULT_ACTIVITY_REQUEST_CODE
        )
    }

    fun openSelectDeviceFragment(withTransition: Boolean = true) {
        ca.onOpenSelectDeviceFragment()

        val selectDeviceFragment =
            SelectDeviceFragment()
        if (withTransition) {
            fm
                .beginTransaction()
                .add(R.id.camera_activity_frameLayout, selectDeviceFragment, "SelectDeviceFragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit()
        } else {
            fm
                .beginTransaction()
                .add(R.id.camera_activity_frameLayout, selectDeviceFragment, "SelectDeviceFragment")
                .commit()
        }
    }

    fun openErrorFragment(uid: String, extractedImage: Bitmap) {
        ca.onOpenErrorFragment()
        val EXTRACTED_IMAGE_KEY = "bmp"
        val errorFragment = ErrorFragment()
        val bundle = Bundle()
        bundle.putString(UID_KEY, uid)
        bundle.putString(URL_KEY, ca.getServerUrl())
        bundle.putParcelable(EXTRACTED_IMAGE_KEY, extractedImage)
        errorFragment.arguments = bundle

        fm
            .beginTransaction()
            .add(R.id.fragment_container_view, errorFragment, "ErrorFragment")
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
    }

    fun openSettingsFragment() {
        val settingsFragment =
            SettingsFragment()

        fm
            .beginTransaction()
            .add(R.id.settings_fragment_container_view, settingsFragment, "SettingsFragment")
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
        mFragmentDarkBackground.visibility = View.VISIBLE
    }

    fun openGalleryFragment(withTransition: Boolean = true, savedImageFiles: File? = null) {
        val galleryFragment =
            GalleryFragment()

        if (withTransition){
            fm
                .beginTransaction()
                .add(R.id.fragment_container_view, galleryFragment, "GalleryFragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit()
        } else {
            fm
                .beginTransaction()
                .add(R.id.fragment_container_view, galleryFragment, "GalleryFragment")
                .commit()
        }

        mFragmentDarkBackground.visibility = View.VISIBLE
    }


    fun openGalleryPreviewFragment(firstImage: File?, secondImage: File?, withTransition: Boolean = true) {
        val previewFragment =
            GalleryPreviewFragment()

        val bundle = Bundle()
        bundle.putSerializable(FIRST_IMAGE_KEY, firstImage)
        bundle.putSerializable(SECOND_IMAGE_KEY, secondImage)
        previewFragment.arguments = bundle

        if (withTransition){
            fm
                .beginTransaction()
                .add(R.id.gallery_fragment_body_layout, previewFragment, "PreviewFragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit()
        } else {
            fm
                .beginTransaction()
                .add(R.id.gallery_fragment_body_layout, previewFragment, "PreviewFragment")
                .commit()
        }
    }

}