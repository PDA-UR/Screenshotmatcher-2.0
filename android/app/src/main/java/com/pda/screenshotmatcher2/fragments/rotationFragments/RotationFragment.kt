package com.pda.screenshotmatcher2.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.activities.CameraActivity

abstract class RotationFragment : Fragment() {

    private lateinit var containerView: FrameLayout
    private lateinit var mView: View
    private lateinit var ca: CameraActivity
    private var rotation: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        if (this::mView.isInitialized) return mView

        if (activity is CameraActivity){
            ca = activity as CameraActivity
        }

        containerView = container as FrameLayout
        mView = inflater.inflate(R.layout.fragment_select_device, container, false)

        rotation = ca.phoneOrientation
        if (rotation == 0 || rotation == 2) {
            return mView
        }
        return rotateView(rotation * 90, mView)
    }

    private fun rotateView(rotationDeg: Int, v: View): View {
        var mRotatedView: View = v

        val container = containerView as ViewGroup
        val w = container.width
        val h = container.height
        mRotatedView.rotation = rotationDeg.toFloat();
        mRotatedView.translationX = ((w - h) / 2).toFloat();
        mRotatedView.translationY = ((h - w) / 2).toFloat();

        val lp = mView.layoutParams
        lp.height = w
        lp.width = h
        mRotatedView.requestLayout()
        return mRotatedView
    }

    public fun getOrientation(): Int {
        return rotation
    }

    fun removeThisFragment() {
        var ca: CameraActivity = requireActivity() as CameraActivity
        ca.onSelectDeviceFragmentClosed()
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }
    public fun removeThisFragmentForRotation() {
        removeForRotation = true
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.commit()
    }

    override fun onDestroy() {
        if(!removeForRotation){
            var ca: CameraActivity = requireActivity() as CameraActivity
            ca.onSelectDeviceFragmentClosed()
        }
        super.onDestroy()
    }

}