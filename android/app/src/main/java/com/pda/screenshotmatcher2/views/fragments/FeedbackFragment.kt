package com.pda.screenshotmatcher2.views.fragments

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.network.sendFeedbackToServer
import com.pda.screenshotmatcher2.viewModels.CaptureViewModel
import com.pda.screenshotmatcher2.views.interfaces.GarbageView

/**
 * Fragment that lets the user send feedback to a feedback server.
 *
 * TODO: Deploy a real feedback server, currently this fragment does not actually send feedback to a server.
 *
 * @property layout The layout of the fragment
 * @property containerView The container view holding this fragment
 * @property mFragmentBackground The dark background behind the fragment, calls [removeThisFragment] on click
 * @property mSendFeedbackButton Send feedback button, sends the feedback to the feedback server on click
 * @property mTextInputField The text input field for the user to enter feedback
 * @property captureViewModel The [CaptureViewModel], used to access the server url and match id of the last match request
 *
 */
class FeedbackFragment : Fragment(), GarbageView {
    //Views
    private lateinit var layout: LinearLayout
    private var containerView: FrameLayout? = null
    private var mFragmentBackground: FrameLayout? = null
    private lateinit var mSendFeedbackButton: Button
    private lateinit var mTextInputField: EditText

    private var captureViewModel: CaptureViewModel? = null

    /**
     * Called when the fragment is created, sets the match result id and the matching server url.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.captureViewModel = ViewModelProvider(this.viewModelStore, CaptureViewModel.Factory(this.requireActivity().application)).get(CaptureViewModel::class.java)
    }

    /**
     * Called on view creation, returns an inflated view of this fragment.
     * @return The inflated view of this fragment
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        containerView = container as FrameLayout
        containerView?.visibility = View.VISIBLE
        return inflater.inflate(R.layout.fragment_feedback, container, false)
    }

    /**
     * Called when the view of this fragment is created, calls [initViews].
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    /**
     * Callback for when feedback has been sent to the server.
     *
     * Calls [removeThisFragment] and updates [StudyLogger].
     */
    fun onFeedbackPosted(){
        removeThisFragment()
        StudyLogger.hashMap["feedback_sent"] = true
        Toast.makeText(context, getText(R.string.ff_submit_success_en), Toast.LENGTH_LONG).show()
    }

    /**
     * Dismisses the virtual keyboard.
     */
    private fun dismissKeyboard() {
        val imm =
            context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(mTextInputField.windowToken, 0)
        mTextInputField.clearFocus()
    }

    /**
     * Whether or not the virtual keyboard is visible.
     *
     * @return true if the virtual keyboard is visible, false otherwise
     */
    private fun isKeyboardActive(): Boolean {
        return mTextInputField.hasFocus()
    }

    /**
     * Initializes the views of this fragment and sets their listeners.
     */
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
        mFragmentBackground?.apply {
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
                this.captureViewModel?.getServerUrl()!!,
                this.captureViewModel?.getMatchID()!!,
                hasResult = false,
                hasScreenshot = false,
                comment = getInputTextFieldText()
            )
            removeThisFragment()
        }
    }

    /**
     * Returns the text in [mTextInputField].
     * @return The text in [mTextInputField]
     */
    private fun getInputTextFieldText(): String {
        return mTextInputField.text.toString()
    }

    /**
     * Removes this fragment.
     */
    private fun removeThisFragment() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            requireActivity().window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }
        containerView?.visibility = View.INVISIBLE
        mFragmentBackground?.visibility = View.INVISIBLE
        activity?.supportFragmentManager
            ?.beginTransaction()
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
            ?.remove(this)?.commit()
    }


    override fun clearGarbage() {
        mFragmentBackground?.setOnClickListener(null)
        mSendFeedbackButton.setOnClickListener(null)
        mTextInputField.setOnClickListener(null)
        layout.setOnClickListener(null)

        captureViewModel = null
        containerView = null
        mFragmentBackground = null

    }
}