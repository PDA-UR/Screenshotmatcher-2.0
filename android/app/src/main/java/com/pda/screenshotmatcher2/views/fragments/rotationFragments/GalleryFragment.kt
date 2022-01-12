package com.pda.screenshotmatcher2.views.fragments.rotationFragments

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import androidx.lifecycle.ViewModelProvider
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.viewHelpers.GridBaseAdapter
import com.pda.screenshotmatcher2.viewModels.GalleryViewModel
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

    private var mFragmentBackground: FrameLayout? = null
    private var mBackButton: ImageButton? = null
    private var mGridView: GridView? = null
    private var adapter: GridBaseAdapter? = null
    private var galleryViewModel: GalleryViewModel? = null

    /**
     * Initializes the fragment.
     *
     * Calls [initViews] to initialize the views
     * Registers an observer to [GalleryViewModel.images], which invalidates the adapter when new images are available
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        initAdapter()
        // Observe Live-Data changes to refresh adapter
        galleryViewModel = ViewModelProvider(requireActivity(), GalleryViewModel.Factory(requireActivity().application))
            .get(GalleryViewModel::class.java)
            .apply {
                getImages().observe(viewLifecycleOwner) {
                    Log.d("GF", "Refreshing")
                    if (adapter !== null) adapter!!.notifyDataSetInvalidated()
                }
            }
    }

    private fun initAdapter() {
        adapter = GridBaseAdapter(this)
        mGridView?.adapter = adapter
    }

    /**
     * Initializes all views and the adapter.
     */
    private fun initViews() {
        mFragmentBackground = activity?.findViewById(R.id.ca_dark_background)!!
        mFragmentBackground?.setOnClickListener { removeThisFragment(true) }
        mFragmentBackground?.visibility = View.VISIBLE
        mGridView = activity?.findViewById(R.id.gallery_fragment_gridview)!!
        mBackButton = activity?.findViewById(R.id.gallery_fragment_back_button)!!
        mBackButton?.setOnClickListener {
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
        containerView?.visibility = View.INVISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            ca?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }
        if (removeBackground) {
            val mFragmentBackground: FrameLayout = activity?.findViewById(R.id.ca_dark_background)!!
            mFragmentBackground.visibility = View.INVISIBLE
        }
        super.removeThisFragment(removeBackground)
        clearGarbage()
    }

    override fun clearGarbage() {
        super.clearGarbage()
        galleryViewModel = null
        mBackButton?.setOnClickListener(null)
        mGridView?.onItemClickListener = null
        mGridView = null
        mFragmentBackground?.setOnClickListener(null)
        adapter?.clear()
        adapter = null
    }
}