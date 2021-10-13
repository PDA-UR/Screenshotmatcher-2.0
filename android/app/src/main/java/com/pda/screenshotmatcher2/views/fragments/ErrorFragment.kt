package com.pda.screenshotmatcher2.views.fragments

import android.content.Intent
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
import com.pda.screenshotmatcher2.views.activities.RESULT_ACTIVITY_REQUEST_CODE
import com.pda.screenshotmatcher2.views.activities.ResultsActivity
import com.pda.screenshotmatcher2.network.sendLog
import com.pda.screenshotmatcher2.viewModels.CaptureViewModel


class ErrorFragment : Fragment() {
    //Views
    private lateinit var mBackButton: Button
    private lateinit var mFeedbackButton: Button
    private lateinit var mFullImageButton: Button
    private lateinit var containerView: FrameLayout
    private lateinit var mFragmentBackground: FrameLayout
    private lateinit var mErrorImageView: ImageView

    private lateinit var captureViewModel: CaptureViewModel

    private var resultsOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureViewModel = ViewModelProvider(requireActivity(), CaptureViewModel.Factory(requireActivity().application)).get(CaptureViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        containerView = container as FrameLayout
        containerView.visibility = View.VISIBLE
        return inflater.inflate(R.layout.fragment_error, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    private fun initViews() {
        mBackButton = requireView().findViewById(R.id.ef_back_button)
        mBackButton.setOnClickListener { removeThisFragment() }
        mFeedbackButton = requireView().findViewById(R.id.ef_send_feedback_button)
        mFeedbackButton.setOnClickListener { openFeedbackFragment() }
        mFullImageButton = requireView().findViewById(R.id.ef_full_image_button)
        mFullImageButton.setOnClickListener { openResultsActivity() }
        mFragmentBackground = activity?.findViewById(R.id.ca_dark_background)!!
        mFragmentBackground.setOnClickListener { removeThisFragment() }
        mFragmentBackground.visibility = View.VISIBLE
        mErrorImageView = requireView().findViewById(R.id.errorFragmentImage)
        Glide.with(requireActivity())
            .load(captureViewModel.getCameraImage())
            .centerCrop()
            .into(mErrorImageView)
    }

    private fun openResultsActivity() {
        val intent = Intent(activity, ResultsActivity::class.java)
        resultsOpened = true
        activity?.startActivityForResult(intent,
            RESULT_ACTIVITY_REQUEST_CODE
        )
        removeThisFragment()
    }

    private fun openFeedbackFragment() {
        val feedbackFragment = FeedbackFragment()
        feedbackFragment.arguments = Bundle().apply {
        }

        activity?.supportFragmentManager
            ?.beginTransaction()
            ?.replace(R.id.feedback_fragment_container_view, feedbackFragment, "feedbackFragment")
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            ?.commit()
        removeThisFragment()
    }


    private fun removeThisFragment() {
        requireActivity().window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        mFragmentBackground.visibility = View.INVISIBLE
        containerView.visibility = View.INVISIBLE
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }

    override fun onStop() {
        super.onStop()
        if (!resultsOpened) {
            captureViewModel.getServerURL()?.let { sendLog(it, requireContext()) }
            StudyLogger.hashMap.clear()
        }
    }
}