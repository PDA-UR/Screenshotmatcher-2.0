package com.pda.screenshotmatcher2.views.fragments

import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.pda.screenshotmatcher2.R

class AnimatedIntroFragment : Fragment(){
    private var layoutResId = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        layoutResId = arguments?.getInt(ARG_LAYOUT_RES_ID) ?: 0
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_animated_intro, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var introAnimation: AnimationDrawable
        view.findViewById<ImageView>(R.id.image).apply {
            setImageResource(R.drawable.intro_animation)
            introAnimation =  drawable as AnimationDrawable
            introAnimation.start()
        }
        view.findViewById<TextView>(R.id.description).apply {
            setText(R.string.app_intro_description_1)
            setTextColor(Color.BLACK)
        }
        view.findViewById<TextView>(R.id.title).apply {
            setText(R.string.app_intro_welcomeHeader)
            setTextColor(Color.BLACK)
        }
    }

    companion object {
        private const val ARG_LAYOUT_RES_ID = "layoutResId"

        @JvmStatic
        fun newInstance(layoutResId: Int): AnimatedIntroFragment {
            val customSlide = AnimatedIntroFragment()
            val args = Bundle()
            args.putInt(ARG_LAYOUT_RES_ID, layoutResId)
            customSlide.arguments = args
            return customSlide
        }
    }
}