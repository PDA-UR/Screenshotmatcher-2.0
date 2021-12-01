package com.pda.screenshotmatcher2.views.fragments

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
import com.pda.screenshotmatcher2.views.activities.RESULT_ACTIVITY_REQUEST_CODE
import com.pda.screenshotmatcher2.views.activities.ResultsActivity
import com.pda.screenshotmatcher2.network.sendLog

const val UID_KEY: String = "UID"
const val URL_KEY: String = "URL"

/**
 * Fragment displaying an error message to the user when a match request results in no matches.
 *
 * @property mBackButton The back button, calls [removeThisFragment] on click
 * @property mFeedbackButton The feedback button, calls [openFeedbackFragment] on click
 * @property mFullImageButton The full image button, calls [openResultsActivity] on click
 * @property containerView The container view holding this fragment
 * @property mFragmentBackground The dark background behind this fragment, calls [removeThisFragment] on click
 * @property mErrorImageView The image view displaying the image taken with the camera
 *
 * @property uid The id of the match request
 * @property url The matching server url
 * @property bmp The bitmap of the image taken with the camera
 *
 * @property resultsOpened Whether the results activity was opened by this fragment or not
 */
class ErrorFragment : Fragment() {
    private lateinit var mBackButton: Button
    private lateinit var mFeedbackButton: Button
    private lateinit var mFullImageButton: Button
    private lateinit var containerView: FrameLayout
    private lateinit var mFragmentBackground: FrameLayout
    private lateinit var mErrorImageView: ImageView

    private lateinit var uid: String
    private lateinit var url: String
    private lateinit var bmp: Bitmap

    private var resultsOpened = false

    /**
     * Called when the fragment is created, sets the uid, url, and bitmap.
     *
     * TODO: Use CaptureViewModel to get the uid, url and bmp
     */
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

    /**
     * On view creation, returns an inflated view of this fragment.
     * @return An inflated view of this fragment
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        containerView = container as FrameLayout
        containerView.visibility = View.VISIBLE
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
        mFragmentBackground.setOnClickListener { removeThisFragment() }
        mFragmentBackground.visibility = View.VISIBLE
        mErrorImageView = requireView().findViewById(R.id.errorFragmentImage)
        Glide.with(requireActivity())
            .load(bmp)
            .centerCrop()
            .into(mErrorImageView)
    }

    /**
     * Opens [ResultsActivity].
     *
     * TODO: Remove extras
     */
    private fun openResultsActivity() {
        val intent = Intent(activity, ResultsActivity::class.java)
        intent.putExtra("matchID", uid)
        intent.putExtra("ServerURL", url)
        resultsOpened = true
        activity?.startActivityForResult(intent,
            RESULT_ACTIVITY_REQUEST_CODE
        )
        removeThisFragment()
    }

    /**
     * Opens [FeedbackFragment].
     *
     * TODO: Remove extras
     */
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


    /**
     * Removes this fragment.
     */
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
            sendLog(url, requireContext())
            StudyLogger.hashMap.clear()
        }
    }
}