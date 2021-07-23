package com.pda.screenshotmatcher2.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.pda.screenshotmatcher2.*
import com.pda.screenshotmatcher2.helpers.*
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.network.ServerConnection
import com.pda.screenshotmatcher2.network.sendBitmap
import java.io.File
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.thread


class CameraActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var cameraInstance: CameraInstance
   //Sensors
    private lateinit var mSensorManager : SensorManager
    private lateinit var mAccelerometer : Sensor
    var phoneOrientation : Int = 0;

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

    private var isCapturing: Boolean = false

    //Shared preferences
    private lateinit var sp: SharedPreferences
    private lateinit var MATCHING_MODE_PREF_KEY: String

    //Old images gallery
    lateinit var imageDirectory: File
    lateinit var files: Array<File>
    lateinit var imageArray: ArrayList<ArrayList<File>>

    var serverConnection = ServerConnection(this)

    private lateinit var fragmentHandler: FragmentHandler

    //Boolean for checking the orientation
    var checkSensor: Boolean = true
    var isFirstBoot: Boolean = true

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusAndActionBars()
        setContentView(R.layout.activity_camera)
        verifyPermissions(this)
        setupSharedPref()
        createDeviceID(this)
        fragmentHandler = FragmentHandler(this)

        initViews()
        setViewListeners()
        if (!serverConnection.isDiscovering && !serverConnection.isSendingHeartbeat) serverConnection.start()


        cameraInstance = CameraInstance(this)
        cameraInstance.start()

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (savedInstanceState != null) {
            restoreFromSavedInstance(savedInstanceState)
        }
        if (!this::imageArray.isInitialized) {
            thread { fillUpImageList() }
        }
    }

    private fun restoreFromSavedInstance(savedInstanceState: Bundle) {
        imageArray = savedInstanceState.getSerializable(getString(R.string.ca_saved_instance_image_list_key)) as ArrayList<ArrayList<File>>
        serverConnection.mServerURL = savedInstanceState.getString(getString(R.string.ca_saved_instance_url_key)).toString()
        var urlList = savedInstanceState.getStringArrayList(getString(R.string.ca_saved_instance_url_list_key))
        var hostList = savedInstanceState.getStringArrayList(getString(R.string.ca_saved_instance_host_list_key))
        var l = mutableListOf<Pair<String, String>>()
        for (i in 0 until urlList?.size!!) {
            l.add(Pair(urlList[i], hostList?.get(i)) as Pair<String, String>)
        }
        //initNetworkHandler()
        if (!serverConnection.isDiscovering && !serverConnection.isSendingHeartbeat) serverConnection.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {

        super.onSaveInstanceState(outState)
        outState.putString(getString(R.string.ca_saved_instance_url_key), serverConnection.mServerURL)
        outState.putSerializable(getString(R.string.ca_saved_instance_image_list_key), imageArray)

        var serverUrlList: ArrayList<String> = ArrayList()
        var serverHostList: ArrayList<String> = ArrayList()

        serverConnection.mServerUrlList.forEach {
            serverHostList.add(it.second)
            serverUrlList.add(it.first)
        }
        outState.putStringArrayList(getString(R.string.ca_saved_instance_url_list_key), serverUrlList)
        outState.putStringArrayList(getString(R.string.ca_saved_instance_host_list_key), serverHostList)
    }

    override fun onResume() {
        super.onResume()
        //initNetworkHandler()
        if (!serverConnection.isDiscovering && !serverConnection.isSendingHeartbeat) serverConnection.start()

        mAccelerometer?.also { accel ->
            mSensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
        }
        Handler().postDelayed(object : Runnable {
            override fun run() {
                checkSensor = true
                Handler().postDelayed(this, 1000)
            }
        }, 1000)
    }

    override fun onPause() {
        super.onPause()
        serverConnection.stop()
        mSensorManager.unregisterListener(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RESULT_ACTIVITY_REQUEST_CODE -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        var resultData: ArrayList<File> = data?.getSerializableExtra(
                            RESULT_ACTIVITY_RESULT_CODE
                        ) as ArrayList<File>
                        imageArray.add(0, resultData)
                    }
                }
            }
        }
    }

    private fun hideStatusAndActionBars() {
        supportActionBar?.hide()
        when (Build.VERSION.SDK_INT) {
            in 0..15 -> {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
            }
            in 16..29 -> {
                val decorView = window.decorView
                val uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                decorView.systemUiVisibility = uiOptions
            }
        }
    }

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
            mSettingsButton.setOnClickListener { fragmentHandler.openSettingsFragment() }
            mGalleryButton = findViewById(R.id.camera_activity_gallery_button)
            mGalleryButton.setOnClickListener { fragmentHandler.openGalleryFragment() }
        }
    }

    private fun setViewListeners() {

        if (!mCaptureButton.hasOnClickListeners()) {
            if (!this::mCaptureButtonListener.isInitialized){
                mCaptureButtonListener = View.OnClickListener {
                    if (!isCapturing){
                        StudyLogger.hashMap["tc_button_pressed"] = System.currentTimeMillis()
                        capturePhoto()
                    }
                }
            }
            mCaptureButton.setOnClickListener(mCaptureButtonListener)
        }


        if (!mSelectDeviceButton.hasOnClickListeners()) {
            mSelectDeviceButtonListener = View.OnClickListener {
                fragmentHandler.openSelectDeviceFragment()
            }
            mSelectDeviceButton.setOnClickListener(mSelectDeviceButtonListener)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            StudyLogger.hashMap["tc_button_pressed"] = System.currentTimeMillis()
            capturePhoto()
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed()
        }
        return true
    }

    private fun capturePhoto(){
        var mBitmap = cameraInstance.captureImageWithPreviewExtraction()
        var mServerURL = serverConnection.mServerURL
        if (mBitmap != null && mServerURL != null) {
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

    override fun onStop() {
        super.onStop()
        serverConnection.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        serverConnection.stop()
    }

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



    fun updateConnectionStatus() {
        if (serverConnection.isConnectedToServer){
            mSelectDeviceButton.background =
                resources.getDrawable(R.drawable.select_device_connected)
            if (phoneOrientation == Surface.ROTATION_0) {
                serverConnection.mServerUrlList.forEach {
                    if (it.first == serverConnection.mServerURL){
                        mSelectDeviceButtonText.text = it.second
                    }
                }
            }
        } else{
            runOnUiThread {
                mSelectDeviceButton.background =
                    resources.getDrawable(R.drawable.select_device_disconnected)
                if (phoneOrientation == Surface.ROTATION_0) {
                    mSelectDeviceButtonText.text =
                        getString(R.string.select_device_button_notConnected_en)
                }
            }
        }
    }

    fun onMatchResult(matchID: String, img: ByteArray) {
        isCapturing = false
        startResultsActivity(matchID, img)
    }

    fun onMatchRequestError(){
        isCapturing = false
        Toast.makeText(this, getString(R.string.match_request_error_en), Toast.LENGTH_LONG).show()
    }

    fun onPermissionDenied() {
        isCapturing = false
        Toast.makeText(this, "Permission denied from server.", Toast.LENGTH_LONG).show()
    }

    fun onOpenSelectDeviceFragment(){
        mSettingsButton.visibility = View.INVISIBLE
        mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_close_48))
    }

    fun onCloseSelectDeviceFragment() {
        mCaptureButton.setImageResource(R.drawable.ic_baseline_photo_camera_24)
        mCaptureButton.setOnClickListener(mCaptureButtonListener)
        mSelectDeviceButton.setOnClickListener(mSelectDeviceButtonListener)
        mSettingsButton.visibility = View.VISIBLE
    }


    fun onOpenErrorFragment() {
        isCapturing = false
    }

    fun getFragmentHandler(): FragmentHandler {
        return fragmentHandler
    }

    private fun setupSharedPref() {
        if (!::sp.isInitialized) {
            sp = PreferenceManager.getDefaultSharedPreferences(this)
            MATCHING_MODE_PREF_KEY = getString(R.string.settings_algorithm_key)
        }
    }

    private fun startResultsActivity(matchID: String, img: ByteArray) {
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra("matchID", matchID)
            putExtra("img", img)
            putExtra("ServerURL", serverConnection.mServerURL)
        }
        startActivityForResult(intent,
            RESULT_ACTIVITY_REQUEST_CODE
        )
    }

    fun getMatchingOptionsFromPref(): HashMap<Any?, Any?>? {
        var matchingMode: HashMap<Any?, Any?>? = HashMap()
        var fastMatchingMode: Boolean = sp.getBoolean(MATCHING_MODE_PREF_KEY, true)
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

    private fun fillUpImageList() {
        imageDirectory = this.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        files = imageDirectory.listFiles()
        files.sortByDescending { it.name }
        imageArray = ArrayList()


        files.forEach outer@{ file ->
            if (imageArray.isNotEmpty()) {
                imageArray.forEach inner@{ item ->
                    if (fileBelongsToImageArrayItem(file, item)) {
                        if (file.name.split("_").last() == "Cropped.png") {
                            item.add(file)
                        } else {
                            item.add(0, file)
                        }
                        return@outer
                    }
                }
            }
            var fileCouple: ArrayList<File> = ArrayList()
            fileCouple.add(file)
            imageArray.add(fileCouple)
        }
    }

    private fun fileBelongsToImageArrayItem(file: File, item: ArrayList<File>): Boolean {
        //Item has already 2 entries
        var filename: String = file.name.split("_".toRegex()).first()
        val itemName: String = item[0].name.split("_".toRegex()).first()

        if (item.size == 2) {
            return false
        }

        return filename == itemName
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

    private fun changeOrientation() {
        if (checkSensor){
            when (phoneOrientation) {
                Surface.ROTATION_0 -> {
                    mSelectDeviceButton.setImageResource(android.R.color.transparent)
                    mGalleryButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_image_24))
                    updateConnectionStatus()
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
                    updateConnectionStatus()
                    mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_photo_camera_24))
                }
                Surface.ROTATION_270 -> {
                    mSelectDeviceButtonText.text = ""
                    mSelectDeviceButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_link_24_landscape))
                    mGalleryButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_image_24_landscape2))
                    mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_photo_camera_24_landscape2))
                }
            }
            fragmentHandler.rotateAllRotatableFragments()
            checkSensor = false
        }

    }
    override fun onBackPressed() {
        if (fragmentHandler.removeAllFragments() == 0){
            super.onBackPressed()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }
}