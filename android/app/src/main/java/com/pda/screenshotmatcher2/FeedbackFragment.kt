package com.pda.screenshotmatcher2

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.FragmentTransaction

lateinit var containerView: FrameLayout
lateinit var backgroundDarkening: FrameLayout
lateinit var mSendFeedbackButton: Button

class FeedbackFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        containerView = container as FrameLayout
        containerView?.visibility = View.VISIBLE
        return inflater.inflate(R.layout.fragment_feedback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backgroundDarkening = activity?.findViewById(R.id.ca_dark_background)!!
        backgroundDarkening.setOnClickListener { removeThisFragment(true) }
        mSendFeedbackButton = activity?.findViewById(R.id.ef_send_feedback_button)!!
        mSendFeedbackButton.setOnClickListener { removeThisFragment(true) }
    }

    private fun removeThisFragment(removeBackground: Boolean = true) {
        containerView.visibility = View.INVISIBLE
        if (removeBackground){backgroundDarkening.visibility = View.INVISIBLE}
        activity?.supportFragmentManager
            ?.beginTransaction()
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
            ?.remove(this)?.commit();
    }
}