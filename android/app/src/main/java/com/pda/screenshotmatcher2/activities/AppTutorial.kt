package com.pda.screenshotmatcher2.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import com.github.appintro.AppIntroPageTransformerType
import com.pda.screenshotmatcher2.R

class AppTutorial : AppIntro() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTransformer(AppIntroPageTransformerType.Parallax(
            titleParallaxFactor = 1.0,
            imageParallaxFactor = -1.0,
            descriptionParallaxFactor = 2.0
        ))
        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.app_intro_welcomeHeader),
            description = getString(R.string.app_intro_description_1),
            imageDrawable = R.drawable.intro_icon_fade,
            titleColor = Color.BLACK,
            descriptionColor = Color.BLACK,
            backgroundColor = Color.WHITE
        ))
        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.app_intro_header_2),
            description = getString(R.string.app_intro_description_2),
            imageDrawable = R.drawable.placeholder_qr_code,
            titleColor = Color.BLACK,
            descriptionColor = Color.BLACK,
            backgroundColor = Color.WHITE
        ))
        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.app_intro_header_3),
            description = getString(R.string.app_intro_description_3),
            imageDrawable = R.drawable.intro_picture_3,
            titleColor = Color.BLACK,
            descriptionColor = Color.BLACK,
            backgroundColor = Color.WHITE
        ))
        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.app_intro_header_4),
            description = getString(R.string.app_intro_description_4),
            imageDrawable = R.drawable.intro_picture_4,
            titleColor = Color.BLACK,
            descriptionColor = Color.BLACK,
            backgroundColor = Color.WHITE
        ))
        setColorSkipButton(Color.BLACK)
        setNextArrowColor(Color.BLACK)
        showStatusBar(false)
        setIndicatorColor(Color.BLACK, Color.LTGRAY)
        setColorDoneText(Color.BLACK)
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        savePerfAndStartCamera()
        finish()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        savePerfAndStartCamera()
        finish()
    }
    private fun savePerfAndStartCamera() {
        val FIRST_RUN_KEY = getString(R.string.FIRST_RUN_KEY)
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        sp.edit().putBoolean(FIRST_RUN_KEY, false).apply()
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
    }
}
