package com.pda.screenshotmatcher2.views.fragments

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import androidx.fragment.app.FragmentTransaction
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.views.interfaces.GarbageView

/**
 * A fragment allowing users to change application settings.
 *
 * @property mFragmentBackground The dark background behind the fragment, calls [removeThisFragment] on click
 * @property containerView The container view for the fragment
 */
class SettingsFragment : GarbageView, PreferenceFragmentCompat() {

    private lateinit var mFragmentBackground: FrameLayout
    private lateinit var containerView: CardView
    private var aboutButton: Preference? = null

    /**
     * Called when the fragment is created, calls [setPreferencesFromResource] to set the preferences
     */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    /**
     * Called on fragment view creation, returns an inflated view of this fragment.
     * @return Inflated view of this fragment
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        containerView = container as CardView
        containerView.visibility = View.VISIBLE
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    /**
     * Called when the fragment view has been created, initializes the views and listeners.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mFragmentBackground = activity?.findViewById(R.id.ca_dark_background)!!
        mFragmentBackground.apply {
            setOnClickListener{removeThisFragment()}
            visibility = View.VISIBLE
        }
        aboutButton = findPreference(getString(R.string.settings_about_button))
        aboutButton?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val uri: Uri =
                Uri.parse("https://github.com/PDA-UR/Screenshotmatcher-2.0")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            true
        }
    }

    /**
     * Removes this fragment
     */
    private fun removeThisFragment() {
        containerView.visibility = View.INVISIBLE
        mFragmentBackground.visibility = View.INVISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
        clearGarbage()
    }

    override fun clearGarbage() {
        mFragmentBackground.setOnClickListener(null)
        aboutButton?.onPreferenceClickListener = null
    }
}