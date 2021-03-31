package com.pda.screenshotmatcher2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction


const val UID_KEY: String = "UID"
const val URL_KEY: String = "URL"

class ErrorFragment : Fragment() {
    //Views
    lateinit var mBackButton: Button
    lateinit var mFeedbackButton: Button
    lateinit var mFullImageButton: Button
    lateinit var containerView: FrameLayout
    lateinit var mFragmentBackground: FrameLayout

    //Full screenshot info
    lateinit var uid: String
    lateinit var url: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bundle = this.arguments
        if (bundle != null) {
            uid = bundle.getString(UID_KEY, "undefined")
            url = bundle.getString(URL_KEY, "undefined")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    private fun initViews() {
        mBackButton = requireView().findViewById(R.id.ef_back_button)
        mBackButton.setOnClickListener { removeThisFragment(true) }
        mFeedbackButton = requireView().findViewById(R.id.ef_send_feedback_button)
        mFeedbackButton.setOnClickListener { openFeedbackFragment() }
        mFullImageButton = requireView().findViewById(R.id.ef_full_image_button)
        mFullImageButton.setOnClickListener { openResultsActivity() }
        mFragmentBackground = activity?.findViewById(R.id.ca_dark_background)!!
        mFragmentBackground.setOnClickListener { removeThisFragment(true) }
        mFragmentBackground.visibility = View.VISIBLE
    }

    private fun openResultsActivity() {
        val intent = Intent(activity, ResultsActivity::class.java)
        intent.putExtra("matchID", uid)
        intent.putExtra("ServerURL", url)
        activity?.startActivityForResult(intent, RESULT_ACTIVITY_REQUEST_CODE)
        removeThisFragment()
    }

    private fun openFeedbackFragment() {
        val feedbackFragment = FeedbackFragment()

        val bundle = Bundle()
        bundle.putString(UID_KEY, uid)
        bundle.putString(URL_KEY, url)
        feedbackFragment.arguments = bundle

        activity?.supportFragmentManager
            ?.beginTransaction()
            ?.replace(R.id.feedback_fragment_container_view, feedbackFragment, "feedbackFragment")
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            ?.commit()
        removeThisFragment(false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        containerView = container as FrameLayout
        containerView.visibility = View.VISIBLE
        return inflater.inflate(R.layout.fragment_error, container, false)
    }

    private fun removeThisFragment(removeBackground: Boolean = true) {
        containerView.visibility = View.INVISIBLE
        if (removeBackground) {
            mFragmentBackground.visibility = View.INVISIBLE
        }
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }

    override fun onStop() {
        super.onStop()
        sendLog(url, requireContext())
        StudyLogger.hashMap.clear()
    }
}