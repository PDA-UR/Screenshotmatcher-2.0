package com.pda.screenshotmatcher2.fragments

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.bumptech.glide.Glide
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.activities.RESULT_ACTIVITY_REQUEST_CODE
import com.pda.screenshotmatcher2.activities.ResultsActivity
import com.pda.screenshotmatcher2.network.sendLog

const val UID_KEY: String = "UID"
const val URL_KEY: String = "URL"

class ErrorFragment : Fragment() {
    //Views
    lateinit var mBackButton: Button
    lateinit var mFeedbackButton: Button
    lateinit var mFullImageButton: Button
    lateinit var containerView: FrameLayout
    lateinit var mFragmentBackground: FrameLayout
    lateinit var mErrorImageView: ImageView

    //Full screenshot info
    lateinit var uid: String
    lateinit var url: String
    lateinit var bmp: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bundle = this.arguments
        if (bundle != null) {
            uid = bundle.getString(UID_KEY, "undefined")
            url = bundle.getString(URL_KEY, "undefined")
            bmp = if (bundle.getParcelable<Bitmap>("bmp") == null){
                BitmapFactory.decodeResource(context?.resources,
                    R.drawable.ic_comic_characters_sad
                )
            } else {
                bundle.getParcelable("bmp")!!
            }
        }
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
            .load(bmp)
            .centerCrop()
            .into(mErrorImageView)
    }

    private fun openResultsActivity() {
        val intent = Intent(activity, ResultsActivity::class.java)
        intent.putExtra("matchID", uid)
        intent.putExtra("ServerURL", url)
        activity?.startActivityForResult(intent,
            RESULT_ACTIVITY_REQUEST_CODE
        )
        removeThisFragment()
    }

    private fun openFeedbackFragment() {
        val feedbackFragment = FeedbackFragment()
        feedbackFragment.arguments = Bundle().apply {
            putString(UID_KEY, uid)
            putString(URL_KEY, url)
        }

        activity?.supportFragmentManager
            ?.beginTransaction()
            ?.replace(R.id.feedback_fragment_container_view, feedbackFragment, "feedbackFragment")
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            ?.commit()
        removeThisFragment()
    }


    private fun removeThisFragment() {
        requireActivity().window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        mFragmentBackground.visibility = View.INVISIBLE
        containerView.visibility = View.INVISIBLE
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }

    override fun onStop() {
        super.onStop()
        sendLog(url, requireContext())
        StudyLogger.hashMap.clear()
    }
}