package com.pda.screenshotmatcher2.views.fragments.rotationFragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.views.interfaces.GarbageView
import com.pda.screenshotmatcher2.views.activities.CameraActivity
import java.io.File

/**
 * An abstract class, inherited by fragments that need to be rotated without an extra layout.
 *
 * @property containerView The container of the fragment
 * @property ca The activity that contains this fragment. TODO: Remove [CameraActivity] cast to make this class usable for all activities
 * @property rotation The rotation of the fragment
 * @property mView The view of the fragment
 * @property subclassName The class name of the fragment extending this class
 *
 */
abstract class RotationFragment : GarbageView, Fragment() {

    var containerView: FrameLayout? = null
    var ca: CameraActivity? = null
    var rotation: Int = 0
    var mView: View? = null
    private lateinit var subclassName: String

    /**
     * Initializes the fragment.
     *
     * Sets the [rotation] of the fragment and [subclassName].
     *
     * @return [mView] with the right rotation
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (mView !== null) return mView

        ca = activity as? CameraActivity
            ?: throw IllegalArgumentException("No CameraActivity provided")
        subclassName = this.javaClass.simpleName

        containerView = container as FrameLayout
        containerView?.visibility = View.VISIBLE

        when(subclassName){
            SelectDeviceFragment::class.simpleName -> {
                mView = inflater.inflate(R.layout.fragment_select_device, container, false)
            }
            GalleryFragment::class.simpleName -> {
                mView = inflater.inflate(R.layout.fragment_gallery, container, false)
            }
        }

        rotation = ca!!.phoneOrientation

        return when(rotation) {
            0, 2 -> mView
            else -> rotateView(rotation * 90, mView!!)
        }
    }

    /**
     * Rotates a view [v] by [rotationDeg] degrees and returns it.
     *
     * @param rotationDeg The rotation in degrees
     * @param v The view to rotate
     * @return Rotated version of [v]
     */
    private fun rotateView(rotationDeg: Int, v: View): View {
        val mRotatedView: View = v
        val container = containerView as ViewGroup
        val w = container.width
        val h = container.height

        mRotatedView.apply {
            rotation = rotationDeg.toFloat()
            translationX = ((w - h) / 2).toFloat()
            translationY = ((h - w) / 2).toFloat()
        }

        mView!!.layoutParams.apply {
            height = w
            width = h
        }

        mRotatedView.requestLayout()
        return mRotatedView
    }

    /**
     * Removes this fragment from the container to be able to rotate it.
     *
     * Does not play any animation.
     */
    open fun removeThisFragmentForRotation(): ArrayList<File?>? {
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.commit()
        return null
    }

    /**
     * Remove this fragment from the container to close it.
     *
     * Plays an animation.
     *
     * @param removeBackground Whether to remove the dark background of the fragment or not
     */
    open fun removeThisFragment(removeBackground: Boolean = true) {
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
        clearGarbage()
    }

    override fun clearGarbage() {
        mView = null
        ca = null
        containerView = null
    }

}