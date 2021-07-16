package com.pda.screenshotmatcher2.fragments.rotationFragments

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import com.pda.screenshotmatcher2.views.GridBaseAdapter
import com.pda.screenshotmatcher2.R

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

}