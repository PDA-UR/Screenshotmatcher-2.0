package com.pda.screenshotmatcher2.viewHelpers

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.views.activities.CameraActivity
import com.pda.screenshotmatcher2.views.fragments.*
import com.pda.screenshotmatcher2.views.fragments.rotationFragments.*
import java.io.File


/**
 * Helper class used to manage fragments of [ResultsActivity][com.pda.screenshotmatcher2.views.activities.CameraActivity].
 *
 * TODO: Implement the class as a general fragment manager, which can also be used by other [Activities][Activity]
 *
 * @constructor Casts the provided [Activity] to a [CameraActivity]; Throws an exception if cast does not succeed.
 * @param a The [Activity], whose fragments should be managed. Must be of type [CameraActivity].
 *
 * @property activity The activity, whose fragments are being managed
 * @property fm The Android [FragmentManager], used to attach/detach/show/hide fragments
 * @property mFragmentDarkBackground The dark background behind a fragment
 */
class CameraActivityFragmentHandler(a: Activity) {

    private val activity = a as? CameraActivity
        ?: throw IllegalArgumentException("No CameraActivity provided")
    private var mFragmentDarkBackground: FrameLayout =
        activity.findViewById(R.id.ca_dark_background)
    private var fm: FragmentManager = activity.supportFragmentManager

    /**
     * Launches a [fragment] in the given [containerID] with a [transition]
     */
    private fun openFragment(fragment: Fragment, containerID: Int, transition: Int? = null) {
        activity.window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
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

    /**
     * Opens [SelectDeviceFragment][com.pda.screenshotmatcher2.views.fragments.rotationFragments.SelectDeviceFragment] in [R.id.camera_activity_frameLayout]
     *
     * @param withTransition Whether a transition animation should be shown or not
     */
    fun openSelectDeviceFragment(withTransition: Boolean = true) {
        activity.onOpenSelectDeviceFragment()
        when (withTransition) {
            true -> openFragment(
                SelectDeviceFragment(),
                R.id.camera_activity_frameLayout,
                FragmentTransaction.TRANSIT_FRAGMENT_OPEN
            )
            false -> openFragment(SelectDeviceFragment(), R.id.camera_activity_frameLayout)
        }
    }

    /**
     * Calls [SelectDeviceFragment.removeThisFragment] to close and remove the open [SelectDeviceFragment].
     */
    fun closeSelectDeviceFragment() {
        fm.findFragmentByTag(SelectDeviceFragment::class.simpleName)?.let {
            val selectDeviceFragment = it as SelectDeviceFragment
            selectDeviceFragment.removeThisFragment(true)
        }
    }

    /**
     * Opens [ErrorFragment][com.pda.screenshotmatcher2.views.fragments.ErrorFragment] in [R.id.fragment_container_view].
     *
     * @param uid The user id of the user
     * @param extractedImage The image that was taken with the camera
     */
    fun openErrorFragment(uid: String, extractedImage: Bitmap) {
        activity.onOpenErrorFragment(uid)
        val bundle = Bundle().apply {
            putString(UID_KEY, uid)
            //TODO: Add real URL
            putString(URL_KEY, "233")
            putParcelable(activity.getString(R.string.EXTRACTED_IMAGE_KEY), extractedImage)
        }

        openFragment(
            ErrorFragment().apply { arguments = bundle },
            R.id.fragment_container_view,
            FragmentTransaction.TRANSIT_FRAGMENT_OPEN
        )

    }


    /**
     * Opens [SettingsFragment][com.pda.screenshotmatcher2.views.fragments.SettingsFragment] in [R.id.settings_fragment_container_view].
     */
    fun openSettingsFragment() {
        openFragment(
            SettingsFragment(),
            R.id.settings_fragment_container_view,
            FragmentTransaction.TRANSIT_FRAGMENT_OPEN
        )
        mFragmentDarkBackground.visibility = View.VISIBLE
    }

    /**
     * Opens [GalleryFragment][com.pda.screenshotmatcher2.views.fragments.rotationFragments.GalleryFragment] in [R.id.fragment_container_view]
     *
     * @param withTransition Whether a transition animation should be shown or not
     */
    fun openGalleryFragment(withTransition: Boolean = true) {
        when (withTransition) {
            true -> openFragment(
                GalleryFragment(),
                R.id.fragment_container_view,
                FragmentTransaction.TRANSIT_FRAGMENT_OPEN
            )
            false -> openFragment(GalleryFragment(), R.id.fragment_container_view)
        }

        mFragmentDarkBackground.visibility = View.VISIBLE
    }

    /**
     * Opens [GalleryPreviewFragment][com.pda.screenshotmatcher2.views.fragments.rotationFragments.GalleryPreviewFragment] in [R.id.gallery_fragment_body_layout].
     *
     * @param firstImage First image to be shown in the image carousel (= **cropped screenshot**)
     * @param secondImage Second image to be shown in the image carousel (= **full screenshot**)
     * @param withTransition Whether a transition animation should be played or not
     */
    fun openGalleryPreviewFragment(
        firstImage: File?,
        secondImage: File?,
        withTransition: Boolean = true
    ) {
        val bundle = Bundle().apply {
            putSerializable(FIRST_IMAGE_KEY, firstImage)
            putSerializable(SECOND_IMAGE_KEY, secondImage)
        }

        when (withTransition) {
            true -> openFragment(
                GalleryPreviewFragment()
                    .apply { arguments = bundle },
                R.id.gallery_fragment_body_layout,
                FragmentTransaction.TRANSIT_FRAGMENT_OPEN
            )
            false -> openFragment(
                GalleryPreviewFragment()
                    .apply { arguments = bundle }, R.id.camera_activity_frameLayout
            )
        }
    }

    /**
     * Closes & removes all currently attached fragments:
     * - [RotationFragment] → [RotationFragment.removeThisFragment]
     * - (Normal) [Fragment] → [fm]
     *
     * @return The total number of removed fragments
     */
    fun removeAllFragments(): Int {
        val frags: List<Fragment> = fm.fragments
        var numOfRemovedFragments = 0
        for (frag: Fragment in frags) {
            when (frag) {
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


    /**
     * Rotates all [RotationFragments][RotationFragment] by calling [rotateGalleryFragment] and [rotateSelectDeviceFragment].
     *
     * Called from [CameraActivity], when the rotation changes.
     * TODO: Convert to a general function that rotates all RotationFragments, no verbose code
     */
    fun rotateAllRotatableFragments() {
        rotateGalleryFragment()
        rotateSelectDeviceFragment()
    }

    /**
     * Updates the rotation of [GalleryFragment] to match the device rotation:
     *
     * 1. Remove the fragment: [GalleryFragment.removeThisFragmentForRotation] → returns currently displayed images
     * 2. Re-open the fragment: [openGalleryFragment]
     */
    private fun rotateGalleryFragment() {
        val gFrag: GalleryFragment? =
            fm.findFragmentByTag(GalleryFragment::class.simpleName) as GalleryFragment?

        gFrag?.let {
            if (gFrag.isVisible && gFrag.rotation != activity.phoneOrientation) {
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

    /**
     * Updates the rotation of [SelectDeviceFragment] to match the device rotation:
     *
     * 1. Remove the fragment: [SelectDeviceFragment.removeThisFragmentForRotation]
     * 2. Re-open the fragment: [openGalleryFragment]
     */
    private fun rotateSelectDeviceFragment() {
        val sdFrag: SelectDeviceFragment? =
            fm.findFragmentByTag(SelectDeviceFragment::class.simpleName) as SelectDeviceFragment?

        if (sdFrag != null && sdFrag.isVisible && hasDifferentRotation(sdFrag)) {
            sdFrag.removeThisFragmentForRotation()
            openSelectDeviceFragment(false)
        }
    }

    /**
     * Helper function to determine whether a [rotationFragment] is in a different orientation than the device
     *
     * @return true = different rotation; false = same rotation
     */

    private fun hasDifferentRotation(rotationFragment: RotationFragment): Boolean {
        return rotationFragment.rotation != activity.phoneOrientation
    }
}