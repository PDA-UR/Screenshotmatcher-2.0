package com.pda.screenshotmatcher2.views.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.viewHelpers.CameraActivityFragmentHandler
import com.pda.screenshotmatcher2.utils.createDeviceID
import com.pda.screenshotmatcher2.utils.rescale
import com.pda.screenshotmatcher2.utils.verifyPermissions
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.network.sendBitmap
import com.pda.screenshotmatcher2.viewModels.GalleryViewModel
import com.pda.screenshotmatcher2.viewModels.ServerConnectionViewModel
import com.pda.screenshotmatcher2.viewHelpers.CameraInstance


class CameraActivity : AppCompatActivity(), SensorEventListener {
   //Sensors
    private lateinit var mSensorManager : SensorManager
    private lateinit var mAccelerometer : Sensor
    var phoneOrientation : Int = 0

    //Permission ID
    private var surfaceTextureHeight: Int = 0
    private var surfaceTextureWidth: Int = 0

    //Other UI Views
    private lateinit var mSelectDeviceButton: ImageButton
    private lateinit var mCaptureButton: ImageButton
    private lateinit var mCaptureButtonListener: View.OnClickListener
    private lateinit var mFragmentDarkBackground: FrameLayout
    private lateinit var mSelectDeviceButtonText: TextView
    private lateinit var mSelectDeviceButtonListener: View.OnClickListener
    private lateinit var mSettingsButton: ImageButton
    private lateinit var mGalleryButton: ImageButton

    //Shared preferences
    private lateinit var sp: SharedPreferences
    private lateinit var MATCHING_MODE_PREF_KEY: String

    private lateinit var galleryViewModel: GalleryViewModel
    private lateinit var serverConnectionViewModel: ServerConnectionViewModel

    //custom helper classes for server connection, fragment management and camera preview
    lateinit var cameraActivityFragmentHandler: CameraActivityFragmentHandler
    var cameraInstance: CameraInstance =
        CameraInstance(this)


    // Activity lifecycle
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusAndActionBars()
        setupSharedPref()
        checkForFirstRun(this)
        setContentView(R.layout.activity_camera)
        verifyPermissions(this)
        createDeviceID(this)
        initViews()
        setViewListeners()

        cameraActivityFragmentHandler =
            CameraActivityFragmentHandler(
                this
            )
        cameraInstance.start()

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        savedInstanceState?.let { restoreFromSavedInstance(it) }


        initViewModels()
    }

    private fun initViewModels() {
        val context = this
        galleryViewModel = ViewModelProvider(this, GalleryViewModel.Factory(application)).get(GalleryViewModel::class.java).apply {
            getImages().observe(context, Observer {
                    images ->
                Log.d("CA", "images updated, new size: ${images.size}")
            })
        }
        serverConnectionViewModel = ViewModelProvider(this, ServerConnectionViewModel.Factory(application)).get(ServerConnectionViewModel::class.java).apply {
            getServerUrlLiveData().observe(context, Observer {
                url ->
                run {
                    Log.d("CA", "New URL: $url")
                }
            })
            isConnectedToServer.observe(context, Observer {
                isConnected -> updateConnectionStatus(isConnected)
            })
        }

    }

    private fun checkForFirstRun(context: Context) {
        val FIRST_RUN_KEY = getString(R.string.FIRST_RUN_KEY)
        // debug: val FIRST_RUN_KEY = "d"
        val isFirstRun: Boolean = sp.getBoolean(FIRST_RUN_KEY, true)
        if(isFirstRun) {
        // debug: if(true){
            val intent = Intent(context, AppTutorial::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun restoreFromSavedInstance(savedInstanceState: Bundle) {
        val urlList = savedInstanceState.getStringArrayList(getString(R.string.ca_saved_instance_url_list_key))
        val hostList = savedInstanceState.getStringArrayList(getString(R.string.ca_saved_instance_host_list_key))
        val l = mutableListOf<Pair<String, String>>()
        for (i in 0 until urlList?.size!!) {
            l.add(Pair(urlList[i], hostList?.get(i)) as Pair<String, String>)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val serverUrlList: ArrayList<String> = ArrayList()
        val serverHostList: ArrayList<String> = ArrayList()

        outState.apply {
            putStringArrayList(getString(R.string.ca_saved_instance_url_list_key), serverUrlList)
            putStringArrayList(getString(R.string.ca_saved_instance_host_list_key), serverHostList)
        }
    }

    override fun onResume() {
        super.onResume()

        // due to a bug in Android, the list of sensors returned by the SensorManager can be empty
        // it will stay that way until reboot.
        // make sure we tell the user about it.
        if (mAccelerometer.name.isNotEmpty()) {
            mAccelerometer.also { accel ->
                mSensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
            }
        }
        else {
            Toast.makeText(this, "Failed to get sensor data. Please restart your phone.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
    }


    // Views
    private fun initViews() {
        if (!::mCaptureButton.isInitialized) {
            mCaptureButton = findViewById(R.id.capture_button)
            mSelectDeviceButton = findViewById(R.id.select_device_button)
            mSelectDeviceButton.background =
                resources.getDrawable(R.drawable.select_device_disconnected)
            mSelectDeviceButtonText = findViewById(R.id.camera_activity_select_device_text)
            mSelectDeviceButtonText.text = getText(R.string.select_device_button_notConnected_en)
            mFragmentDarkBackground = findViewById(R.id.ca_dark_background)
            mSettingsButton = findViewById(R.id.camera_activity_settings_button)
            mSettingsButton.setOnClickListener { cameraActivityFragmentHandler.openSettingsFragment() }
            mGalleryButton = findViewById(R.id.camera_activity_gallery_button)
            mGalleryButton.setOnClickListener { cameraActivityFragmentHandler.openGalleryFragment() }
        }
    }

    private fun setViewListeners() {
        mCaptureButton.apply {
            if (!hasOnClickListeners()){
                mCaptureButtonListener = View.OnClickListener {
                    if (!cameraInstance.isCapturing){
                        StudyLogger.hashMap["tc_button_pressed"] = System.currentTimeMillis()
                        capturePhoto()
                    }
                }
            }
            mCaptureButton.setOnClickListener(mCaptureButtonListener)
        }

        mSelectDeviceButton.apply {
           if (!hasOnClickListeners()) {
               mSelectDeviceButtonListener = View.OnClickListener {
                   cameraActivityFragmentHandler.openSelectDeviceFragment()
               }
               setOnClickListener(mSelectDeviceButtonListener)
           }
        }
    }

    private fun hideStatusAndActionBars() {
        supportActionBar?.hide()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

    }

    // Functionality
    private fun capturePhoto(){
        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val mBitmap = cameraInstance.captureImageWithPreviewExtraction()
        val mServerURL = serverConnectionViewModel.getServerUrl()
        if (mBitmap != null) {
            if (mServerURL != ""){
                StudyLogger.hashMap["tc_image_captured"] = System.currentTimeMillis()   // image is in memory
                StudyLogger.hashMap["long_side"] = cameraInstance.IMG_TARGET_SIZE
                val greyImg =
                    rescale(
                        mBitmap,
                        cameraInstance.IMG_TARGET_SIZE
                    )
                val matchingOptions: java.util.HashMap<Any?, Any?>? = getMatchingOptionsFromPref()
                sendBitmap(
                    greyImg,
                    mServerURL,
                    this,
                    matchingOptions
                )
            } else {
                onMatchRequestError()
            }
        } else {
            onMatchRequestError()
        }
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        when (isConnected){
            true -> {
                mSelectDeviceButton.background =
                    resources.getDrawable(R.drawable.select_device_connected)
                if (phoneOrientation == Surface.ROTATION_0) {
                    mSelectDeviceButtonText.text = serverConnectionViewModel.getConnectedServerName()
                }
            }
            false -> {
                mSelectDeviceButton.background =
                    resources.getDrawable(R.drawable.select_device_disconnected)
                if (phoneOrientation == Surface.ROTATION_0) {
                    mSelectDeviceButtonText.text =
                        getString(R.string.select_device_button_notConnected_en)
                }
                mSelectDeviceButtonText.requestLayout()
            }
        }
    }

    private fun startResultsActivity(matchID: String, img: ByteArray) {
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra("matchID", matchID)
            putExtra("img", img)
            putExtra("ServerURL", serverConnectionViewModel.getServerUrl())
        }
        startActivityForResult(intent,
            RESULT_ACTIVITY_REQUEST_CODE
        )
    }

    // Orientation changes
    private fun changeOrientation() {
            when (phoneOrientation) {
                Surface.ROTATION_0 -> {
                    mSelectDeviceButton.setImageResource(android.R.color.transparent)
                    mGalleryButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_image_24))
                    updateConnectionStatus(serverConnectionViewModel.getConnectionStatus())
                    mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_photo_camera_24))

                }
                Surface.ROTATION_90 -> {
                    mSelectDeviceButtonText.text = ""
                    mSelectDeviceButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_link_24_landscape))
                    mGalleryButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_image_24_landscape))
                    mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_photo_camera_24_landscape))
                }
                Surface.ROTATION_180 -> {
                    //same as normal portrait
                    mSelectDeviceButton.setImageResource(android.R.color.transparent)
                    mGalleryButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_image_24))
                    updateConnectionStatus(serverConnectionViewModel.getConnectionStatus())
                    mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_photo_camera_24))
                }
                Surface.ROTATION_270 -> {
                    mSelectDeviceButtonText.text = ""
                    mSelectDeviceButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_link_24_landscape))
                    mGalleryButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_image_24_landscape2))
                    mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_photo_camera_24_landscape2))
                }
            }
            cameraActivityFragmentHandler.rotateAllRotatableFragments()

    }

    // Permissions
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    cameraInstance.openCamera(surfaceTextureHeight, surfaceTextureWidth)
                } else {
                    Toast.makeText(this,getString(R.string.permission_request_en), Toast.LENGTH_LONG).show()
                    verifyPermissions(this)
                }
            }
        }
    }

    //Callbacks
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            StudyLogger.hashMap["tc_button_pressed"] = System.currentTimeMillis()
            capturePhoto()
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed()
        }
        return true
    }

    fun onMatchResult(matchID: String, img: ByteArray) {
        cameraInstance.isCapturing = false
        window.decorView.performHapticFeedback(HapticFeedbackConstants.REJECT)
        startResultsActivity(matchID, img)
    }

    fun onMatchRequestError(){
        cameraInstance.isCapturing = false
        window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        Toast.makeText(this, getString(R.string.match_request_error_en), Toast.LENGTH_LONG).show()
    }

    fun onPermissionDenied() {
        cameraInstance.isCapturing = false
        Toast.makeText(this, "Permission denied from server.", Toast.LENGTH_LONG).show()
    }

    fun onOpenSelectDeviceFragment(){
        mSettingsButton.visibility = View.INVISIBLE
        mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_close_48))
        mCaptureButton.setOnClickListener {
            cameraActivityFragmentHandler.closeSelectDeviceFragment()
        }
    }

    fun onCloseSelectDeviceFragment() {
        mCaptureButton.setImageResource(R.drawable.ic_baseline_photo_camera_24)
        mCaptureButton.setOnClickListener {
            if (!cameraInstance.isCapturing){
                StudyLogger.hashMap["tc_button_pressed"] = System.currentTimeMillis()
                capturePhoto()
            }
        }
        mSelectDeviceButton.setOnClickListener(mSelectDeviceButtonListener)
        mSettingsButton.visibility = View.VISIBLE
    }

    fun onOpenErrorFragment() {
        cameraInstance.isCapturing = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event!!.values[1] > 0 && event.values[0].toInt() == 0) {
            if (phoneOrientation != Surface.ROTATION_0){
                phoneOrientation = Surface.ROTATION_0 //portrait
                changeOrientation()
            }
        }

        else if (event.values[1] < 0 && event.values[0].toInt() == 0) {
            if (phoneOrientation != Surface.ROTATION_180) {
                phoneOrientation = Surface.ROTATION_180 //landscape
                changeOrientation()
            }
        }

        else if (event.values[0] > 0 && event.values[1].toInt() == 0) {
            if (phoneOrientation != Surface.ROTATION_90) {
                phoneOrientation = Surface.ROTATION_90 //landscape
                changeOrientation()
            }
        }

        else if (event.values[0] < 0 && event.values[1].toInt() == 0) {
            if (phoneOrientation != Surface.ROTATION_270) {
                phoneOrientation = Surface.ROTATION_270 //landscape
                changeOrientation()
            }
        }
    }

    override fun onBackPressed() {
        if (cameraActivityFragmentHandler.removeAllFragments() == 0){
            super.onBackPressed()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RESULT_ACTIVITY_REQUEST_CODE -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        galleryViewModel.reloadImages()
                    }
                }
            }
        }
    }

    // Preferences
    private fun setupSharedPref() {
        if (!::sp.isInitialized) {
            sp = PreferenceManager.getDefaultSharedPreferences(this)
            MATCHING_MODE_PREF_KEY = getString(R.string.settings_algorithm_key)
        }
    }

    private fun getMatchingOptionsFromPref(): HashMap<Any?, Any?>? {
        val matchingMode: HashMap<Any?, Any?>? = HashMap()
        val fastMatchingMode: Boolean = sp.getBoolean(MATCHING_MODE_PREF_KEY, true)

        if (fastMatchingMode) {
            matchingMode?.set(
                getString(R.string.algorithm_key_server),
                getString(R.string.algorithm_fast_mode_name_server)
            )
        } else {
            matchingMode?.set(
                getString(R.string.algorithm_key_server),
                getString(R.string.algorithm_accurate_mode_name_server)
            )
        }
        return matchingMode
    }


}