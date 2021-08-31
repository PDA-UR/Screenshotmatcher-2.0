package com.pda.screenshotmatcher2.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.network.sendFeedbackToServer


//Views
private lateinit var layout: LinearLayout
private lateinit var containerView: FrameLayout
private lateinit var mFragmentBackground: FrameLayout
private lateinit var mSendFeedbackButton: Button
private lateinit var mTextInputField: EditText

//Server info
private lateinit var uid: String
private lateinit var url: String

var removeForRotation: Boolean = false

class FeedbackFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bundle = this.arguments
        if (bundle != null) {
            uid = bundle.getString(UID_KEY, "undefined")
            url = bundle.getString(URL_KEY, "undefined")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        containerView = container as FrameLayout
        containerView.visibility = View.VISIBLE
        return inflater.inflate(R.layout.fragment_feedback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    fun onFeedbackPosted(){
        removeThisFragment()
        StudyLogger.hashMap["feedback_sent"] = true
        Toast.makeText(context, getText(R.string.ff_submit_success_en), Toast.LENGTH_LONG).show()
    }

    private fun dismissKeyboard() {
        val imm =
            context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(mTextInputField.windowToken, 0)
        mTextInputField.clearFocus()
    }

    private fun isKeyboardActive(): Boolean {
        return mTextInputField.hasFocus()
    }

    private fun initViews(){
        layout = activity?.findViewById(
            R.id.ff_linearLayout
        )!!
        layout.setOnClickListener {
            if (isKeyboardActive()){
                dismissKeyboard()
            }
        }

        mFragmentBackground = activity?.findViewById(
            R.id.ca_dark_background
        )!!
        mFragmentBackground.apply {
            setOnClickListener {
                if (isKeyboardActive()){
                    dismissKeyboard()
                } else{
                    removeThisFragment()
                }
            }
            visibility = View.VISIBLE
        }
        mTextInputField = activity?.findViewById(
            R.id.ff_text_input
        )!!
        mSendFeedbackButton = activity?.findViewById(
            R.id.ff_send_feedback_button
        )!!
        mSendFeedbackButton.setOnClickListener {
            sendFeedbackToServer(
                this,
                requireActivity().applicationContext,
                url,
                uid,
                hasResult = false,
                hasScreenshot = false,
                comment = getInputTextFieldText()
            )
            removeThisFragment()
        }
    }

    private fun getInputTextFieldText(): String {
        return mTextInputField.text.toString()
    }

    private fun removeThisFragment() {
        containerView.visibility = View.INVISIBLE
        mFragmentBackground.visibility = View.INVISIBLE
        activity?.supportFragmentManager
            ?.beginTransaction()
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
            ?.remove(this)?.commit()
    }
}