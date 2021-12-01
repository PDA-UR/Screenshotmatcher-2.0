package com.pda.screenshotmatcher2.views.fragments.rotationFragments

import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import androidx.lifecycle.ViewModelProvider
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.viewModels.GalleryViewModel
import com.pda.screenshotmatcher2.viewHelpers.GridBaseAdapter
import java.io.File

/**
 * [RotationFragment] displaying all old match results saved in the external app directory as a grid.
 *
 * @property mFragmentBackground The dark background behind the fragment
 * @property mBackButton The back button to close the fragment
 * @property mGridView The grid view to display the old match results
 * @property adapter The adapter to fill the grid view
 */
class GalleryFragment : RotationFragment() {

    private lateinit var mFragmentBackground: FrameLayout
    private lateinit var mBackButton: ImageButton
    private lateinit var mGridView: GridView
    private lateinit var adapter: GridBaseAdapter

    /**
     * Initializes the fragment.
     *
     * Calls [initViews] to initialize the views
     * Registers an observer to [GalleryViewModel.images], which invalidates the adapter when new images are available
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        // Observe Live-Data changes to refresh adapter
        ViewModelProvider(requireActivity(), GalleryViewModel.Factory(requireActivity().application))
            .get(GalleryViewModel::class.java)
            .apply {
                getImages().observe(viewLifecycleOwner) {
                    Log.d("GF", "Refreshing")
                    if (::adapter.isInitialized) adapter.notifyDataSetInvalidated()
                }
            }
    }

    /**
     * Initializes all views and the adapter.
     */
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

    /**
     * Removes this fragment to rotate it.
     *
     * Saves the current state of [GalleryPreviewFragment] if it is open in order to restore it after rotation.
     *
     */
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

    /**
     * Removes this fragment.
     *
     * @param removeBackground Whether to remove the dark background behind the fragment or not
     */
    override fun removeThisFragment(removeBackground: Boolean) {
        containerView.visibility = View.INVISIBLE
        ca.window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        if (removeBackground) {
            val mFragmentBackground: FrameLayout = activity?.findViewById(R.id.ca_dark_background)!!
            mFragmentBackground.visibility = View.INVISIBLE
        }
        super.removeThisFragment(removeBackground)
    }

}