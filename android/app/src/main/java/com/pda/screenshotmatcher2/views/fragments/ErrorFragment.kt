package com.pda.screenshotmatcher2.views.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.views.activities.ResultsActivity
import com.pda.screenshotmatcher2.network.sendLog
import com.pda.screenshotmatcher2.viewModels.CaptureViewModel
import com.pda.screenshotmatcher2.views.interfaces.GarbageView

/**
 * Fragment displaying an error message to the user when a match request results in no matches.
 *
 * @property mBackButton The back button, calls [removeThisFragment] on click
 * @property mFeedbackButton The feedback button, calls [openFeedbackFragment] on click
 * @property mFullImageButton The full image button, calls [openResultsActivity] on click
 * @property containerView The container view holding this fragment
 * @property mFragmentBackground The dark background behind this fragment, calls [removeThisFragment] on click
 * @property mErrorImageView The image view displaying the image taken with the camera
 * @property captureViewModel The [CaptureViewModel], used to access the server url and full camera image of the last match request
 * @property resultsOpened Whether the results activity was opened by this fragment or not
 */
class ErrorFragment : Fragment(), GarbageView {
    private lateinit var mBackButton: Button
    private lateinit var mFeedbackButton: Button
    private lateinit var mFullImageButton: Button
    private var containerView: FrameLayout? = null
    private var mFragmentBackground: FrameLayout? = null
    private lateinit var mErrorImageView: ImageView

    private var captureViewModel: CaptureViewModel? = null

    private var resultsOpened = false

    /**
     * Called when the fragment is created, initiates [captureViewModel].
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.captureViewModel = ViewModelProvider(this.viewModelStore, CaptureViewModel.Factory(this.requireActivity().application)).get(CaptureViewModel::class.java)
    }

    /**
     * On view creation, returns an inflated view of this fragment.
     * @return An inflated view of this fragment
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        containerView = container as FrameLayout
        containerView?.visibility = View.VISIBLE
        return inflater.inflate(R.layout.fragment_error, container, false)
    }

    /**
     * Called after the view of this fragment has been created, calls [initViews].
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    /**
     * Initiates the views of this fragment and loads the image taken with the camera into [mErrorImageView].
     */
    private fun initViews() {
        mBackButton = requireView().findViewById(R.id.ef_back_button)
        mBackButton.setOnClickListener { removeThisFragment() }
        mFeedbackButton = requireView().findViewById(R.id.ef_send_feedback_button)
        mFeedbackButton.setOnClickListener { openFeedbackFragment() }
        mFullImageButton = requireView().findViewById(R.id.ef_full_image_button)
        mFullImageButton.setOnClickListener { openResultsActivity() }
        mFragmentBackground = activity?.findViewById(R.id.ca_dark_background)!!
        mFragmentBackground?.setOnClickListener { removeThisFragment() }
        mFragmentBackground?.visibility = View.VISIBLE
        mErrorImageView = requireView().findViewById(R.id.errorFragmentImage)
        Glide.with(requireActivity())
            .load(captureViewModel?.getCameraImage())
            .centerCrop()
            .into(mErrorImageView)
    }

    /**
     * Opens [ResultsActivity].
     */
    private fun openResultsActivity() {
        val intent = Intent(activity, ResultsActivity::class.java)
        resultsOpened = true
        activity?.startActivityForResult(intent,
            ResultsActivity.RESULT_ACTIVITY_REQUEST_CODE
        )
        removeThisFragment()
    }

    /**
     * Opens [FeedbackFragment].
     */
    private fun openFeedbackFragment() {
        val feedbackFragment = FeedbackFragment()

        activity?.supportFragmentManager
            ?.beginTransaction()
            ?.replace(R.id.feedback_fragment_container_view, feedbackFragment, "feedbackFragment")
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            ?.commit()
        removeThisFragment()
    }


    /**
     * Removes this fragment.
     */
    private fun removeThisFragment() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            requireActivity().window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }
        mFragmentBackground?.visibility = View.INVISIBLE
        containerView?.visibility = View.INVISIBLE
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }

    override fun onStop() {
        super.onStop()
        if (!resultsOpened) {
            sendLog(captureViewModel?.getServerUrl()!!, requireContext())
            StudyLogger.hashMap.clear()
        }
    }

    override fun clearGarbage() {
        mBackButton.setOnClickListener(null)
        mFeedbackButton.setOnClickListener(null)
        mFullImageButton.setOnClickListener(null)
        mFragmentBackground?.setOnClickListener(null)

        containerView = null
        mFragmentBackground = null
        captureViewModel = null
    }
}