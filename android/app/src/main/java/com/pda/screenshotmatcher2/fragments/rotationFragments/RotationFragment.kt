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

    private var rotation: Int = 0
    private lateinit var mView: View
    private lateinit var subclassName: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (this::mView.isInitialized) return mView

        if (activity is CameraActivity){
            ca = activity as CameraActivity
        }

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


    fun removeThisFragmentForRotation(): ArrayList<File?>? {
        removeForRotation = true

        if (subclassName == GalleryFragment::class.simpleName){
            val pFrag: GalleryPreviewFragment? =
                activity?.supportFragmentManager?.findFragmentByTag(GalleryPreviewFragment::class.simpleName) as GalleryPreviewFragment?
            if (pFrag != null && pFrag.isVisible){
                var savedImageFiles =  pFrag.removeThisFragmentForRotation()
                activity?.supportFragmentManager?.beginTransaction()?.remove(this)
                    ?.commit()
                return savedImageFiles
            }
        }

        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.commit()
        return null
    }

    fun removeThisFragment(removeBackground: Boolean = true) {
        when(subclassName){
            SelectDeviceFragment::class.simpleName -> {
                ca.onCloseSelectDeviceFragment()
            }
            GalleryFragment::class.simpleName -> {
                removeForRotation = !removeBackground
                containerView.visibility = View.INVISIBLE
                if (removeBackground) {
                    var mFragmentBackground: FrameLayout = activity?.findViewById(R.id.ca_dark_background)!!
                    mFragmentBackground.visibility = View.INVISIBLE
                }
            }
        }

        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }



}