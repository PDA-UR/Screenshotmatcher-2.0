package com.pda.screenshotmatcher2.views.activities

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import com.github.appintro.AppIntroPageTransformerType
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.views.fragments.AnimatedIntroFragment

/**
 * Carousel intro that introduces new users to the app. Shown when launching the app for the first time.
 * @see <a href="https://github.com/AppIntro/AppIntro">AppIntro</a> for more information
 */
class AppTutorial : AppIntro() {

    /**
     * Sets display options ([setTransformer]), adds all slides to the carousel ([addSlide]) and requests specified app permissions upon reaching the last slide ([askForPermissions])
	 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTransformer(AppIntroPageTransformerType.Parallax(
            titleParallaxFactor = 1.0,
            imageParallaxFactor = -1.0,
            descriptionParallaxFactor = 2.0
        ))

        //addSlide(AnimatedIntroFragment.newInstance(R.layout.fragment_animated_intro))

        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.app_intro_header_2),
            description = getString(R.string.app_intro_description_2),
            titleColor = Color.BLACK,
            descriptionColor = Color.BLACK,
            //imageDrawable = R.drawable.ic_download_24px_outlined,
            backgroundColor = Color.WHITE
        ))
        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.app_intro_header_3),
            description = getString(R.string.app_intro_description_3),
            //imageDrawable = R.drawable.intro_picture_3,
            titleColor = Color.BLACK,
            descriptionColor = Color.BLACK,
            backgroundColor = Color.WHITE
        ))
        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.app_intro_header_4),
            description = getString(R.string.app_intro_description_4),
            //imageDrawable = R.drawable.intro_picture_4,
            titleColor = Color.BLACK,
            descriptionColor = Color.BLACK,
            backgroundColor = Color.WHITE
        ))
        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.app_intro_header_5),
            description = getString(R.string.app_intro_description_5),
            //imageDrawable = R.drawable.intro_picture_5,
            titleColor = Color.BLACK,
            descriptionColor = Color.BLACK,
            backgroundColor = Color.WHITE
        ))

        setBackArrowColor(Color.BLACK)
        setNextArrowColor(Color.BLACK)
        showStatusBar(false)
        setIndicatorColor(Color.BLACK, Color.LTGRAY)
        setColorDoneText(Color.BLACK)
        isWizardMode = true
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        savePrefsAndStartCameraActivity()
    }
    /**
     * Callback that's being called when a new slide is displayed
	 * @param oldFragment The previously displayed slide
	 * @param newFragment The new slide
	 */
    override fun onSlideChanged(oldFragment: Fragment?, newFragment: Fragment?) {
        super.onSlideChanged(oldFragment, newFragment)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    /**
     * Updates shared preferences (on-boarding complete) and launches [CameraActivity]
     */
    private fun savePrefsAndStartCameraActivity() {
        val firstRunKey = getString(R.string.FIRST_RUN_KEY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        sp.edit().putBoolean(firstRunKey, false).apply()
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
    }
}
