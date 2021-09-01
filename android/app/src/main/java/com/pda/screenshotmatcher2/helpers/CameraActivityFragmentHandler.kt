package com.pda.screenshotmatcher2.helpers

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.activities.CameraActivity
import com.pda.screenshotmatcher2.fragments.*
import com.pda.screenshotmatcher2.fragments.rotationFragments.*
import java.io.File

class CameraActivityFragmentHandler(a: Activity) {

    private val activity = a as? CameraActivity
        ?: throw IllegalArgumentException("No CameraActivity provided")
    private var mFragmentDarkBackground: FrameLayout = activity.findViewById(R.id.ca_dark_background)
    private var fm: FragmentManager = activity.supportFragmentManager

    private fun openFragment(fragment: Fragment, containerID: Int, transition: Int? = null){
        fm
            .beginTransaction()
            .add(containerID, fragment, fragment::class.simpleName)
            .apply {
                transition?.let {
                    setTransition(it)
                }
            }
            .commit()
    }

    fun openSelectDeviceFragment(withTransition: Boolean = true) {
        activity.onOpenSelectDeviceFragment()
        when(withTransition){
            true -> openFragment(SelectDeviceFragment(), R.id.camera_activity_frameLayout, FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            false -> openFragment(SelectDeviceFragment(), R.id.camera_activity_frameLayout)
        }
    }

    fun openErrorFragment(uid: String, extractedImage: Bitmap) {
        activity.onOpenErrorFragment()

        val bundle = Bundle().apply {
            putString(UID_KEY, uid)
            putString(URL_KEY, activity.serverConnection.mServerURL)
            putParcelable(activity.getString(R.string.EXTRACTED_IMAGE_KEY), extractedImage)
        }

        openFragment(ErrorFragment().apply { arguments = bundle }, R.id.fragment_container_view, FragmentTransaction.TRANSIT_FRAGMENT_OPEN)

    }

    fun openSettingsFragment() {
        openFragment(SettingsFragment(), R.id.settings_fragment_container_view, FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        mFragmentDarkBackground.visibility = View.VISIBLE
    }

    fun openGalleryFragment(withTransition: Boolean = true) {
        when(withTransition){
            true -> openFragment(GalleryFragment(), R.id.fragment_container_view, FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            false -> openFragment(GalleryFragment(), R.id.fragment_container_view)
        }

        mFragmentDarkBackground.visibility = View.VISIBLE
    }

    fun refreshGalleryFragment() {
        val gFrag: GalleryFragment? =
            fm.findFragmentByTag(GalleryFragment::class.simpleName) as GalleryFragment?
        gFrag?.refreshAdapter()
    }

    fun openGalleryPreviewFragment(firstImage: File?, secondImage: File?, withTransition: Boolean = true) {
        val bundle = Bundle().apply {
            putSerializable(FIRST_IMAGE_KEY, firstImage)
            putSerializable(SECOND_IMAGE_KEY, secondImage)
        }

        when(withTransition){
            true -> openFragment(
                GalleryPreviewFragment()
                    .apply { arguments = bundle }, R.id.gallery_fragment_body_layout, FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            false -> openFragment(
                GalleryPreviewFragment()
                    .apply { arguments = bundle }, R.id.camera_activity_frameLayout)
        }
    }

    fun removeAllFragments(): Int {
        val frags: List<Fragment> = fm.fragments
        var numOfRemovedFragments = 0
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
        return numOfRemovedFragments
    }

    fun rotateAllRotatableFragments() {
        rotateGalleryFragment()
        rotateSelectDeviceFragment()
    }

    private fun rotateGalleryFragment() {
        val gFrag: GalleryFragment? =
            fm.findFragmentByTag(GalleryFragment::class.simpleName) as GalleryFragment?

        gFrag?.let {
            if (gFrag.isVisible && gFrag.rotation != activity.phoneOrientation){
                //Remove GalleryFragment, returns images from GalleryPreviewFragment if it is open
                val savedImageFiles = gFrag.removeThisFragmentForRotation()
                //Open GalleryFragment
                openGalleryFragment(false)
                //Open GalleryPreviewFragment if it was open
                savedImageFiles?.let {
                    openGalleryPreviewFragment(it[0], it[1])
                }
            }
        }
    }

    private fun rotateSelectDeviceFragment() {
        val sdFrag: SelectDeviceFragment? =
            fm.findFragmentByTag(SelectDeviceFragment::class.simpleName) as SelectDeviceFragment?

        if (sdFrag != null && sdFrag.isVisible && hasDifferentRotation(sdFrag)){
            sdFrag.removeThisFragmentForRotation()
            openSelectDeviceFragment(false)
        }
    }

    private fun hasDifferentRotation(rotationFragment: RotationFragment): Boolean {
        return rotationFragment.rotation != activity.phoneOrientation
    }
}