package com.pda.screenshotmatcher2.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import androidx.fragment.app.FragmentTransaction
import androidx.preference.PreferenceFragmentCompat
import com.pda.screenshotmatcher2.R

class SettingsFragment : PreferenceFragmentCompat() {

    lateinit var mFragmentBackground: FrameLayout
    lateinit var containerView: CardView

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
        mFragmentBackground.apply {
            setOnClickListener{removeThisFragment()}
            visibility = View.VISIBLE
        }
    }

    private fun removeThisFragment() {
        containerView.visibility = View.INVISIBLE
        mFragmentBackground.visibility = View.INVISIBLE
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }
}