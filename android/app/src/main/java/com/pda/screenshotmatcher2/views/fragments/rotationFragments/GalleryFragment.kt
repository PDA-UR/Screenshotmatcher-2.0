package com.pda.screenshotmatcher2.views.fragments.rotationFragments

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.viewModels.GalleryViewModel
import com.pda.screenshotmatcher2.viewHelpers.GridBaseAdapter
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
        // Observe Live-Data changes to refresh adapter
        ViewModelProvider(requireActivity(), GalleryViewModel.Factory(requireActivity().application))
            .get(GalleryViewModel::class.java)
            .apply {
                getImages().observe(viewLifecycleOwner, Observer {
                    // Log.d("GF", "Refreshing")
                    if (::adapter.isInitialized) adapter.notifyDataSetInvalidated()
            })
        }
    }

    private fun initViews() {
        mFragmentBackground = activity?.findViewById(R.id.ca_dark_background)!!
        mFragmentBackground.setOnClickListener { removeThisFragment(true) }
        mFragmentBackground.visibility = View.VISIBLE
        mGridView = activity?.findViewById(R.id.gallery_fragment_gridview)!!
        adapter = GridBaseAdapter(this)
        mGridView.adapter = adapter
        mBackButton = activity?.findViewById(R.id.gallery_fragment_back_button)!!
        mBackButton.setOnClickListener {
            removeThisFragment()
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            ca.window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }
        if (removeBackground) {
            val mFragmentBackground: FrameLayout = activity?.findViewById(R.id.ca_dark_background)!!
            mFragmentBackground.visibility = View.INVISIBLE
        }
        super.removeThisFragment(removeBackground)
    }

}