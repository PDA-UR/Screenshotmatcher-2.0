package com.pda.screenshotmatcher2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import androidx.fragment.app.FragmentTransaction
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {

    lateinit var mFragmentBackground: FrameLayout
    lateinit var containerView: CardView
    private var removeForRotation: Boolean = true

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        containerView = container as CardView
        containerView.visibility = View.VISIBLE
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mFragmentBackground = activity?.findViewById(R.id.ca_dark_background)!!
        mFragmentBackground.setOnClickListener { removeThisFragment(true) }
        mFragmentBackground.visibility = View.VISIBLE
    }

    private fun removeThisFragment(removeBackground: Boolean = true) {
        removeForRotation = removeBackground

        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }

    override fun onDestroy() {
        if (removeForRotation) {
            containerView.visibility = View.INVISIBLE
            mFragmentBackground.visibility = View.INVISIBLE
        }
        super.onDestroy()
    }

}