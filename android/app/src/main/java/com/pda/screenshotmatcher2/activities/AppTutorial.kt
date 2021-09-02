package com.pda.screenshotmatcher2.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import com.github.appintro.AppIntroPageTransformerType
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.fragments.AnimatedIntroFragment

class AppTutorial : AppIntro() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTransformer(AppIntroPageTransformerType.Parallax(
            titleParallaxFactor = 1.0,
            imageParallaxFactor = -1.0,
            descriptionParallaxFactor = 2.0
        ))

        addSlide(AnimatedIntroFragment.newInstance(R.layout.fragment_animated_intro))

        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.app_intro_header_2),
            description = getString(R.string.app_intro_description_2),
            imageDrawable = R.drawable.intro_qr_code,
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
        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.app_intro_header_5),
            description = getString(R.string.app_intro_description_5),
            imageDrawable = R.drawable.intro_picture_5,
            titleColor = Color.BLACK,
            descriptionColor = Color.BLACK,
            backgroundColor = Color.WHITE
        ))
       askForPermissions(
            permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET,
                Manifest.permission.CAMERA
            ),
            slideNumber = 5,
            required = true)
        setBackArrowColor(Color.BLACK)
        setNextArrowColor(Color.BLACK)
        showStatusBar(false)
        setIndicatorColor(Color.BLACK, Color.LTGRAY)
        setColorDoneText(Color.BLACK)
        isWizardMode = true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            this.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            savePerfAndStartCamera()
        }
    }

    override fun onUserDeniedPermission(permissionName: String) {
        // User pressed "Deny"
        Toast.makeText(this, getString(R.string.app_intro_denied_permission), Toast.LENGTH_SHORT).show()
    }
    override fun onUserDisabledPermission(permissionName: String) {
        // User pressed "Deny" + "Don't ask again" on the permission dialog
        Toast.makeText(this, getString(R.string.app_intro_disabled_permission), Toast.LENGTH_SHORT)
    }
    override fun onSlideChanged(oldFragment: Fragment?, newFragment: Fragment?) {
        super.onSlideChanged(oldFragment, newFragment)
        window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }
    private fun savePerfAndStartCamera() {
        // debug: val FIRST_RUN_KEY = "r"
        val FIRST_RUN_KEY = getString(R.string.FIRST_RUN_KEY)
        window.decorView.performHapticFeedback(HapticFeedbackConstants.GESTURE_END, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        sp.edit().putBoolean(FIRST_RUN_KEY, false).apply()
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
    }
}
