package com.pda.screenshotmatcher2

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"


lateinit var containerView: FrameLayout
lateinit var backgroundDarkening: FrameLayout
lateinit var mSendFeedbackButton: Button

/**
 * A simple [Fragment] subclass.
 * Use the [FeedbackFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class FeedbackFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("FF", "created")
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
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

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment FeedbackFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            FeedbackFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
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
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit();
    }
}