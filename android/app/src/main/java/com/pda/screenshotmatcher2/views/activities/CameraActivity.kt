package com.pda.screenshotmatcher2.views.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import com.pda.screenshotmatcher2.background.NewPhotoService
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.network.sendBitmap
import com.pda.screenshotmatcher2.network.sendBitmapCA
import com.pda.screenshotmatcher2.utils.createDeviceID
import com.pda.screenshotmatcher2.utils.rescale
import com.pda.screenshotmatcher2.utils.verifyPermissions
import com.pda.screenshotmatcher2.viewHelpers.CameraActivityFragmentHandler
import com.pda.screenshotmatcher2.viewHelpers.CameraProvider
import com.pda.screenshotmatcher2.viewModels.CaptureViewModel
import com.pda.screenshotmatcher2.viewModels.GalleryViewModel
import com.pda.screenshotmatcher2.viewModels.ServerConnectionViewModel
import com.pda.screenshotmatcher2.views.activities.interfaces.CameraInstance


/**
 * Main activity that displays a camera preview and allows users to take screenshots of their connected pc.
 *
 * @property mAccelerometer Sensor, used get [mSensorManager]
 * @property mSensorManager SensorManager, used to register [CameraActivity] as a listener for orientation changes
 * @property phoneOrientation The current device orientation. Possible values: [Surface.ROTATION_0], [Surface.ROTATION_90], [Surface.ROTATION_180], [Surface.ROTATION_270]
 *
 * @property mSelectDeviceButton Button that opens [SelectDeviceFragment][com.pda.screenshotmatcher2.views.fragments.rotationFragments.SelectDeviceFragment]
 * @property mCaptureButton Button that captures an image starts a new match request
 * @property mCaptureButtonListener Listener that listens to events of [mCaptureButton]
 * @property mFragmentDarkBackground Dark background, displayed when a [Fragment][com.pda.screenshotmatcher2.views.fragments] is being opened
 * @property mSelectDeviceButtonText Text that's being displayed on [mSelectDeviceButton]. "Disconnected" or "HOSTNAME", depending on [serverConnectionViewModel] connection status.
 * @property mSelectDeviceButtonListener Lister that listens to events of [mSelectDeviceButton]
 * @property mSettingsButton Button that opens [SettingsFragment][com.pda.screenshotmatcher2.views.fragments.SettingsFragment]
 * @property mGalleryButton Button that opens [GalleryFragment][com.pda.screenshotmatcher2.views.fragments.rotationFragments.GalleryFragment]
 *
 * @property isCapturing Whether or no a capture request is currently ongoing or not
 * @property sp The shared preferences of the application
 * @property MATCHING_MODE_PREF_KEY The key to access the type of matching mode via [sp]
 *
 * @property galleryViewModel [GalleryViewModel] that provides two way data bindings to access images of the internal gallery
 * @property serverConnectionViewModel [ServerConnectionViewModel] that provides two way data bindings to access [ServerConnectionModel][com.pda.screenshotmatcher2.models.ServerConnectionModel]
 * @property captureViewModel [CaptureViewModel] that provides two way data bindings to access [CaptureModel][com.pda.screenshotmatcher2.models.CaptureModel]
 *
 * @property cameraActivityFragmentHandler Instance of [CameraActivityFragmentHandler], manages fragments
 */
class CameraActivity : AppCompatActivity(), SensorEventListener, CameraInstance {
    private lateinit var mAccelerometer : Sensor
    private lateinit var mSensorManager : SensorManager
    var phoneOrientation : Int = 0

    private lateinit var mSelectDeviceButton: ImageButton
    private lateinit var mCaptureButton: ImageButton
    private lateinit var mCaptureButtonListener: View.OnClickListener
    private lateinit var mFragmentDarkBackground: FrameLayout
    private lateinit var mSelectDeviceButtonText: TextView
    private lateinit var mSelectDeviceButtonListener: View.OnClickListener
    private lateinit var mSettingsButton: ImageButton
    private lateinit var mGalleryButton: ImageButton

    var isCapturing: Boolean = false

    private lateinit var sp: SharedPreferences
    private lateinit var MATCHING_MODE_PREF_KEY: String

    private lateinit var galleryViewModel: GalleryViewModel
    private lateinit var serverConnectionViewModel: ServerConnectionViewModel
    private lateinit var captureViewModel: CaptureViewModel

    lateinit var cameraActivityFragmentHandler: CameraActivityFragmentHandler
    private var cameraProvider: CameraProvider =
        CameraProvider(this)


    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusAndActionBars()
        setupSharedPref()
        checkForFirstRun(this)
        setContentView(R.layout.activity_camera)
        if(verifyPermissions(this)) cameraProvider.start()
        createDeviceID(this)
        initViews()
        setViewListeners()
        cameraActivityFragmentHandler =
            CameraActivityFragmentHandler(
                this
            )


        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        savedInstanceState?.let { restoreFromSavedInstance(it) }

        initViewModels()
    }

    /**
     * Function to launch the background service via [Intent]
     * TODO: Implement fully
     */
    private fun startBackgroundService() {
        Intent(this, NewPhotoService::class.java).also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("CA","Starting the service in >=26 Mode")
                startForegroundService(it)
                return
            }
            Log.d("CA","Starting the service in < 26 Mode")
            startService(it)
        }
    }

    /**
     * TODO: Implement fully
     */
    private fun stopBackgroundService() {
        Intent(this, NewPhotoService::class.java).also {
            Log.d("CA","Stopping service")
            stopService(it)
        }
    }


    /**
     * Initializes [galleryViewModel], [serverConnectionViewModel], [captureViewModel] and registers Observers
     *
     * Called in [onCreate]
     */
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
        captureViewModel = ViewModelProvider(this, CaptureViewModel.Factory(application)).get(CaptureViewModel::class.java)
    }

    /**
     * Checks whether the app is started for the first time and opens [AppTutorial] if that is the case.
     *
     * Called in [onCreate]
     *
	 * @param context Application context
	 */
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

    /**
     * Resumes state when an instance gets restored
	 * @param savedInstanceState
	 */
    private fun restoreFromSavedInstance(savedInstanceState: Bundle) {
        val urlList = savedInstanceState.getStringArrayList(getString(R.string.ca_saved_instance_url_list_key))
        val hostList = savedInstanceState.getStringArrayList(getString(R.string.ca_saved_instance_host_list_key))
        val l = mutableListOf<Pair<String, String>>()
        for (i in 0 until urlList?.size!!) {
            l.add(Pair(urlList[i], hostList?.get(i)) as Pair<String, String>)
        }
    }

    /**
     * Saves state of an instance when it gets finished
	 * @param outState
	 */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val serverUrlList: ArrayList<String> = ArrayList()
        val serverHostList: ArrayList<String> = ArrayList()

        outState.apply {
            putStringArrayList(getString(R.string.ca_saved_instance_url_list_key), serverUrlList)
            putStringArrayList(getString(R.string.ca_saved_instance_host_list_key), serverHostList)
        }
    }

    /**
     * TODO implement background service behavior
     * Registers [mSensorManager]
     */
    override fun onResume() {
        super.onResume()
        stopBackgroundService()
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

    /**
     * TODO: implement background service behavior
     * Unregisters [mSensorManager]
     */
    override fun onPause() {
        super.onPause()
        //TODO: Enable this when background service is implemented
        //startBackgroundService()
        mSensorManager.unregisterListener(this)
    }


    /**
     * Initiates all views.
     *
     * Called in [onCreate]
     */
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

    /**
     * Sets all view listeners.
     *
     * Called in [onCreate]
     */
    private fun setViewListeners() {
        mCaptureButton.apply {
            if (!hasOnClickListeners()){
                mCaptureButtonListener = View.OnClickListener {
                    if (!isCapturing){
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

    /**
     * Hides the status and action bars.
     *
     * Called in [onCreate].
     */
    private fun hideStatusAndActionBars() {
        supportActionBar?.hide()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

    }

    /**
     * Captures a camera image via [cameraProvider] and sends it to the connected server via [sendBitmap]
     *
     * Called in [mCaptureButtonListener].
     */
    private fun capturePhoto(){
        isCapturing = true
        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        val mBitmap = cameraProvider.captureImageWithPreviewExtraction()
        val mServerURL = serverConnectionViewModel.getServerUrl()
        Log.d("CA", "Setting model url: $mServerURL")
        captureViewModel.setCaptureRequestData(mServerURL, mBitmap!!)
        if (mServerURL != ""){
            StudyLogger.hashMap["tc_image_captured"] = System.currentTimeMillis()   // image is in memory
            StudyLogger.hashMap["long_side"] = CameraProvider.IMG_TARGET_SIZE
            val greyImg =
                rescale(
                    mBitmap,
                    CameraProvider.IMG_TARGET_SIZE
                )
            val matchingOptions: java.util.HashMap<Any?, Any?>? = getMatchingOptionsFromPref()
            sendBitmapCA(
                greyImg,
                mServerURL,
                this,
                matchingOptions,
            )
        } else {
            onMatchRequestError()
        }
    }

    /**
     * Updates [mSelectDeviceButton] and [mSelectDeviceButtonText] to match the new connection status.
     *
     * Called when [serverConnectionViewModel] updates or [onSensorChanged] detects a change in orientation.
     *
	 * @param isConnected Whether or a server is currently connected
	 */
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

    /**
     * Starts [ResultsActivity].
	 */
    private fun startResultsActivity(matchID: String, img: ByteArray) {
        val intent = Intent(this, ResultsActivity::class.java)
        startActivityForResult(intent,
            RESULT_ACTIVITY_REQUEST_CODE
        )
    }

    /**
     * Callback that updates the layout of this activity and rotates all [rotationFragments][com.pda.screenshotmatcher2.views.fragments.rotationFragments].
     *
     * Called when [onSensorChanged] detects change in orientation.
     */
    private fun onOrientationChanged() {
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

    /**
     * Callback for permission request results. Calls [verifyPermissions] if a permission has not been granted.
     *
	 * @param requestCode Request code of the permission(s)
	 * @param permissions The permission(s) requested
	 * @param grantResults Whether or not the permission(s) have been granted
	 */
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
                    cameraProvider.start()
                } else {
                    Toast.makeText(this,getString(R.string.permission_request_en), Toast.LENGTH_LONG).show()
                    verifyPermissions(this)
                }
            }
        }
    }

    /**
     * Callback for listening to key events.
     *
     * Calls [capturePhoto] if the volume down button has been pressed.
     *
	 * @param keyCode The key code of the pressed key
	 * @param event
	 * @return
	 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            StudyLogger.hashMap["tc_button_pressed"] = System.currentTimeMillis()
            capturePhoto()
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed()
        }
        return true
    }


    /**
     * Callback for when the server answers to a match request with a match.
     *
     * Updates [captureViewModel] with [matchID] and [matchName], then starts the [ResultsActivity]
     *
	 * @param matchID The ID of the match
	 * @param img The cropped image of the match
	 */
    fun onMatchResult(matchID: String, img: ByteArray) {
        isCapturing = false
        window.decorView.performHapticFeedback(HapticFeedbackConstants.REJECT)
        captureViewModel.setCaptureResultData(matchID, BitmapFactory.decodeByteArray(img, 0, img.size))
        startResultsActivity(matchID, img)
    }

    /**
     * Opens [ErrorFragment][com/pda/screenshotmatcher2/views/fragments/ErrorFragment.kt]
     *
     * Updates [captureViewModel] with [uid]
     * Called when the server answers to a match request with no match.
     *
     * @param uid The ID of the match
     */
    fun onOpenErrorFragment(uid: String) {
        captureViewModel.setCaptureResultData(uid, null)
        isCapturing = false
    }

    /**
     * Callback for when sending the match request fails.
     *
     * Displays a toast with the error message.
     */
    fun onMatchRequestError(){
        isCapturing = false
        window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        Toast.makeText(this, getString(R.string.match_request_error_en), Toast.LENGTH_LONG).show()
    }

    /**
     * Callback for when the server denies a match request.
     *
     * Displays a toast with the error message.
     */
    fun onPermissionDenied() {
        isCapturing = false
        Toast.makeText(this, "Permission denied from server.", Toast.LENGTH_LONG).show()
        verifyPermissions(this)
    }

    /**
     * Opens [SelectDeviceFragment][com/pda/screenshotmatcher2/views/fragments/rotationFragments/SelectDeviceFragment.kt]
     *
     * Changes the drawable and onClickListener of [mSelectDeviceButton] and hides [mSettingsButton]
     */
    fun onOpenSelectDeviceFragment(){
        mSettingsButton.visibility = View.INVISIBLE
        mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_close_48))
        mCaptureButton.setOnClickListener {
            cameraActivityFragmentHandler.closeSelectDeviceFragment()
        }
    }

    /**
     * Closes [SelectDeviceFragment][com/pda/screenshotmatcher2/views/fragments/rotationFragments/SelectDeviceFragment.kt]
     *
     * Changes the drawable and onClickListener of [mSelectDeviceButton] and shows [mSettingsButton]
     */
    fun onCloseSelectDeviceFragment() {
        mCaptureButton.setImageResource(R.drawable.ic_baseline_photo_camera_24)
        mCaptureButton.setOnClickListener {
            if (!isCapturing){
                StudyLogger.hashMap["tc_button_pressed"] = System.currentTimeMillis()
                capturePhoto()
            }
        }
        mSelectDeviceButton.setOnClickListener(mSelectDeviceButtonListener)
        mSettingsButton.visibility = View.VISIBLE
    }


    /**
     * Listens to [SensorEvents][SensorEvent] and calls [onOrientationChanged] when the device orientation changes.
     *
     * @param event The [SensorEvent]
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event!!.values[1] > 0 && event.values[0].toInt() == 0) {
            if (phoneOrientation != Surface.ROTATION_0){
                phoneOrientation = Surface.ROTATION_0 //portrait
                onOrientationChanged()
            }
        }

        else if (event.values[1] < 0 && event.values[0].toInt() == 0) {
            if (phoneOrientation != Surface.ROTATION_180) {
                phoneOrientation = Surface.ROTATION_180 //landscape
                onOrientationChanged()
            }
        }

        else if (event.values[0] > 0 && event.values[1].toInt() == 0) {
            if (phoneOrientation != Surface.ROTATION_90) {
                phoneOrientation = Surface.ROTATION_90 //landscape
                onOrientationChanged()
            }
        }

        else if (event.values[0] < 0 && event.values[1].toInt() == 0) {
            if (phoneOrientation != Surface.ROTATION_270) {
                phoneOrientation = Surface.ROTATION_270 //landscape
                onOrientationChanged()
            }
        }
    }

    /**
     * Callback for when the back button is pressed.
     *
     * Removes all fragments from the backstack and prevents the back button from closing the app.
     */
    override fun onBackPressed() {
        if (cameraActivityFragmentHandler.removeAllFragments() == 0){
            super.onBackPressed()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }

    /**
     * Callback for when the [ResultsActivity] is finished.
     *
     * Reloads [galleryViewModel] if the activity was started from [CameraActivity] and [resultCode] is [Activity.RESULT_OK].
     *
     * @param requestCode The request code set by [CameraActivity] when [ResultsActivity] was started.
     * @param resultCode The result code set by [ResultsActivity] when it was finished.
     * @param data Extra data set by [ResultsActivity] when it was finished.
     */
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

    /**
     * Initiates the shared preferences.
     *
     * Initiates [sp] if it is null.
     * Retrieves [MATCHING_MODE_PREF_KEY] from [R.string.matching_mode_pref_key]
     */
    private fun setupSharedPref() {
        if (!::sp.isInitialized) {
            sp = PreferenceManager.getDefaultSharedPreferences(this)
            MATCHING_MODE_PREF_KEY = getString(R.string.settings_algorithm_key)
        }
    }

    /**
     * Retrieves the matching mode from [sp] and returns it.
     *
     * @return The matching mode (fast or accurate).
     */
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

    /**
     * Returns this activity
     *
     * Must be implemented because this class implements [CameraInstance].
     *
     * @return This activity
     */
    override fun getActivity(): Activity {
        return this
    }

    /**
     * Returns the preview [TextureView] of the [CameraActivity].
     *
     * Must be implemented because this class implements [CameraInstance].
     *
     * @return The preview [TextureView] of the [CameraActivity]
     */
    override fun getTextureView(): TextureView {
        return findViewById(R.id.preview_view)
    }

    /**
     * Returns the current [phoneOrientation]
     *
     * Must be implemented because this class implements [CameraInstance].
     *
     * @return The current [phoneOrientation]
     */
    override fun getOrientation(): Int {
        return phoneOrientation
    }


}