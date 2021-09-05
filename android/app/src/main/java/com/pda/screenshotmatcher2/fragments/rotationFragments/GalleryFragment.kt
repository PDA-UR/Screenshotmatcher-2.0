package com.pda.screenshotmatcher2.fragments.rotationFragments

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import com.pda.screenshotmatcher2.views.GridBaseAdapter
import com.pda.screenshotmatcher2.R
import java.io.File

class GalleryFragment : RotationFragment() {

    //Views
    private lateinit var mFragmentBackground: FrameLayout
    private lateinit var mBackButton: ImageButton
    private lateinit var mGridView: GridView
    private lateinit var adapter: GridBaseAdapter

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
    fun refreshAdapter() {
        adapter.notifyDataSetChanged()
    }

    override fun removeThisFragmentForRotation(): ArrayList<File?>? {
        val pFrag: GalleryPreviewFragment? =
            activity?.supportFragmentManager?.findFragmentByTag(GalleryPreviewFragment::class.simpleName) as GalleryPreviewFragment?

        pFrag?.let {
            if (it.isVisible){
                val savedImageFiles =  it.removeThisFragmentForRotation()
                activity?.supportFragmentManager?.beginTransaction()?.remove(this)
                    ?.commit()
                return savedImageFiles
            }
        }
        return super.removeThisFragmentForRotation()
    }

    override fun removeThisFragment(removeBackground: Boolean) {
        containerView.visibility = View.INVISIBLE
        ca.window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        if (removeBackground) {
            var mFragmentBackground: FrameLayout = activity?.findViewById(R.id.ca_dark_background)!!
            mFragmentBackground.visibility = View.INVISIBLE
        }
        super.removeThisFragment(removeBackground)
    }

}