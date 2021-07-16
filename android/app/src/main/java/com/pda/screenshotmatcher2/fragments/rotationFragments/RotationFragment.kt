package com.pda.screenshotmatcher2.fragments.rotationFragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.activities.CameraActivity
import com.pda.screenshotmatcher2.fragments.GalleryPreviewFragment
import com.pda.screenshotmatcher2.fragments.removeForRotation
import java.io.File

abstract class RotationFragment : Fragment() {

    lateinit var containerView: FrameLayout
    lateinit var ca: CameraActivity
    var rotation: Int = 0
    private lateinit var mView: View
    private lateinit var subclassName: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (this::mView.isInitialized) return mView

        ca = activity as? CameraActivity ?: throw IllegalArgumentException("No CameraActivity provided")
        subclassName = this.javaClass.simpleName

        containerView = container as FrameLayout
        containerView.visibility = View.VISIBLE

        when(subclassName){
            SelectDeviceFragment::class.simpleName -> {
                mView = inflater.inflate(R.layout.fragment_select_device, container, false)
            }
            GalleryFragment::class.simpleName -> {
                mView = inflater.inflate(R.layout.fragment_gallery, container, false)
            }
        }

        rotation = ca.phoneOrientation

        return when(rotation) {
            0, 2 -> mView
            else -> rotateView(rotation * 90, mView)
        }
    }

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

        mView.layoutParams.apply {
            height = w
            width = h
        }

        mRotatedView.requestLayout()
        return mRotatedView
    }

    open fun removeThisFragmentForRotation(): ArrayList<File?>? {
        removeForRotation = true
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.commit()
        return null
    }

    open fun removeThisFragment(removeBackground: Boolean = true) {
        removeForRotation = !removeBackground
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }

}