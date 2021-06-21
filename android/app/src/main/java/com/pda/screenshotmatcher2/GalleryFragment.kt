package com.pda.screenshotmatcher2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import java.io.File

class GalleryFragment : Fragment() {
    //Views
    private lateinit var containerView: FrameLayout
    private lateinit var mFragmentBackground: FrameLayout
    private lateinit var mBackButton: ImageButton
    private lateinit var mGridView: GridView
    private lateinit var adapter: GridBaseAdapter
    private lateinit var mView: View
    private var rotation: Int = 0
    private lateinit var ca: CameraActivity
    private var removeForRotation: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        containerView = container as FrameLayout
        containerView.visibility = View.VISIBLE

        if (this::mView.isInitialized) return mView

        if (activity is CameraActivity){
            ca = activity as CameraActivity
        }

        containerView = container as FrameLayout
        mView = inflater.inflate(R.layout.fragment_gallery, container, false)

        rotation = ca.phoneOrientation
        if (rotation == 0 || rotation == 2) {
            return mView
        }

        return rotateView(rotation * 90, mView)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    private fun initViews() {
        mFragmentBackground = activity?.findViewById(R.id.ca_dark_background)!!
        mFragmentBackground.setOnClickListener { removeThisFragment(true) }
        mFragmentBackground.visibility = View.VISIBLE
        mGridView = activity?.findViewById(R.id.gallery_fragment_gridview)!!
        adapter = GridBaseAdapter(requireContext())
        mGridView.adapter = adapter
        mBackButton = activity?.findViewById(R.id.gallery_fragment_back_button)!!
        mBackButton.setOnClickListener {
            removeThisFragment()
        }
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

    private fun removeThisFragment(removeBackground: Boolean = true) {
        this.removeForRotation = !removeBackground
        containerView.visibility = View.INVISIBLE
        if (removeBackground) {
            mFragmentBackground.visibility = View.INVISIBLE
        }
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }

    fun removeThisFragmentForRotation(): ArrayList<File?>? {
        removeForRotation = true
        val pFrag: GalleryPreviewFragment? =
            activity?.supportFragmentManager?.findFragmentByTag("PreviewFragment") as GalleryPreviewFragment?
        if (pFrag != null && pFrag.isVisible){
            var savedImageFiles =  pFrag.removeThisFragmentForRotation()
            activity?.supportFragmentManager?.beginTransaction()?.remove(this)
                ?.commit()
            return savedImageFiles
        }

        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.commit()
        return null
    }

    override fun onDestroy() {
        if (!removeForRotation){
            containerView.visibility = View.INVISIBLE
            mFragmentBackground.visibility = View.INVISIBLE
        }
        super.onDestroy()
    }

}