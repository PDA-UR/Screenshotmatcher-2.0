package com.pda.screenshotmatcher2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.Size
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentTransaction
import androidx.preference.PreferenceManager
import java.io.File
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.thread


class CameraActivity : AppCompatActivity(), SensorEventListener {
    private val MAX_PREVIEW_WIDTH = 1920
    private val MAX_PREVIEW_HEIGHT = 1080
    private val IMG_TARGET_SIZE = 512
    //Physical camera
    private lateinit var mCameraId: String

    //Preview view
    private lateinit var mTextureView: TextureView

    //Active camera session
    private lateinit var mCaptureSession: CameraCaptureSession

    //opened camera
    private lateinit var mCameraDevice: CameraDevice

    //size of preview
    private lateinit var mPreviewSize: Size

    //Handles still image capture
    private lateinit var mImageReader: ImageReader

    //Camera preview builder
    private lateinit var mPreviewRequestBuilder: CaptureRequest.Builder

    //Sensors
    private lateinit var mSensorManager : SensorManager
    private lateinit var mAccelerometer : Sensor
    public var phoneOrientation : Int = 0;

    //Request from builder
    private lateinit var mPreviewRequest: CaptureRequest

    //Prevent exit before closing cam
    private var mCameraOpenCloseLock: Semaphore = Semaphore(1)

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

    private var mServerURL: String = ""
    private var isConnectedToServer = false
    private lateinit var mServerUrlList: List<Pair<String, String>>
    private var mUserID: String = ""
    private var startTime: Long = 0
    private var isCapturing: Boolean = false

    //Shared preferences
    private lateinit var sp: SharedPreferences
    private lateinit var MATCHING_MODE_PREF_KEY: String

    //Old images gallery
    lateinit var imageDirectory: File
    lateinit var files: Array<File>
    lateinit var imageArray: ArrayList<ArrayList<File>>

    //Handlers for discover/heartbeat thread
    var handlerThread: HandlerThread? = null
    var mHandler: Handler? = null
    var looper: Looper? = null

    //Boolean for checking the orientation
    var checkSensor: Boolean = true

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusAndActionBars()
        setContentView(R.layout.activity_camera)
        verifyPermissions(this)
        setupSharedPref()
        getUserID()
        initViews()
        setViewListeners()
        initNetworkHandler()
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (savedInstanceState != null) {
            restoreFromSavedInstance(savedInstanceState)
        }
        if (!this::imageArray.isInitialized) {
            thread { fillUpImageList() }
        }
    }



    private fun initNetworkHandler() {
        handlerThread = HandlerThread("NetworkThread")
        handlerThread!!.start()
        looper = handlerThread!!.looper

        val a: CameraActivity = this
        val discoverRunnable = object: Runnable {
            override fun run() {
                getServerURL()
                if (handlerThread?.isAlive!!){
                    mHandler?.postDelayed(this, 5000)
                }
            }
        }

        val heartbeatRunnable = object: Runnable {
            override fun run() {
                if (isConnectedToServer && mServerURL != ""){
                    sendHeartbeatRequest(mServerURL, a)
                }
                mHandler?.postDelayed(this, 5000)
            }
        }

        mHandler = object : Handler(looper!!) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    //0 = end all threads
                    0 -> {
                        this.removeCallbacksAndMessages(null)
                    }
                    //1 = start discover, end heartbeat
                    1 -> {
                        this.removeCallbacksAndMessages(null)
                        this.post(discoverRunnable)
                    }
                    //2 = start heartbeat, end discover
                    2 -> {
                        this.removeCallbacksAndMessages(null)
                        this.post(heartbeatRunnable);
                    }
                }
            }
        }
        when(isConnectedToServer){
            true -> startHeartbeatThread()
            false -> startDiscoverThread()
        }
    }

    private fun restoreFromSavedInstance(savedInstanceState: Bundle) {
        imageArray = savedInstanceState.getSerializable(getString(R.string.ca_saved_instance_image_list_key)) as ArrayList<ArrayList<File>>
        mServerURL = savedInstanceState.getString(getString(R.string.ca_saved_instance_url_key)).toString()
        var urlList = savedInstanceState.getStringArrayList(getString(R.string.ca_saved_instance_url_list_key))
        var hostList = savedInstanceState.getStringArrayList(getString(R.string.ca_saved_instance_host_list_key))
        var l = mutableListOf<Pair<String, String>>()
        for (i in 0 until urlList?.size!!) {
            l.add(Pair(urlList[i], hostList?.get(i)) as Pair<String, String>)
        }
        initNetworkHandler()
    }

    override fun onSaveInstanceState(outState: Bundle) {

        super.onSaveInstanceState(outState)
        outState.putString(getString(R.string.ca_saved_instance_url_key), mServerURL)
        outState.putSerializable(getString(R.string.ca_saved_instance_image_list_key), imageArray)

        var serverUrlList: ArrayList<String> = ArrayList()
        var serverHostList: ArrayList<String> = ArrayList()

        mServerUrlList.forEach {
            serverHostList.add(it.second)
            serverUrlList.add(it.first)
        }
        outState.putStringArrayList(getString(R.string.ca_saved_instance_url_list_key), serverUrlList)
        outState.putStringArrayList(getString(R.string.ca_saved_instance_host_list_key), serverHostList)
    }

    override fun onResume() {
        super.onResume()
        initNetworkHandler()
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
        if (!::mTextureView.isInitialized) {
            mTextureView = findViewById(R.id.preview_view)
            mCaptureButton = findViewById(R.id.capture_button)
            mSelectDeviceButton = findViewById(R.id.select_device_button)
            mSelectDeviceButton.background =
                resources.getDrawable(R.drawable.select_device_disconnected)
            mSelectDeviceButtonText = findViewById(R.id.camera_activity_select_device_text)
            mSelectDeviceButtonText.text = getText(R.string.select_device_button_notConnected_en)
            mFragmentDarkBackground = findViewById(R.id.ca_dark_background)
            mSettingsButton = findViewById(R.id.camera_activity_settings_button)
            mSettingsButton.setOnClickListener { openSettings() }
            mGalleryButton = findViewById(R.id.camera_activity_gallery_button)
            mGalleryButton.setOnClickListener { openGallery() }
        }
    }

    private fun setViewListeners() {
        if (!mTextureView.hasOnClickListeners()) {
            mTextureView.surfaceTextureListener =
                object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        texture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        surfaceTextureWidth = width
                        surfaceTextureHeight = height
                        openCamera(width, height)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        texture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        configureTransform(width, height)
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
                }
        }

        if (!mCaptureButton.hasOnClickListeners()) {
            if (!this::mCaptureButtonListener.isInitialized){
                mCaptureButtonListener = View.OnClickListener {
                    if (!isCapturing){
                        StudyLogger.hashMap["client_id"] = mUserID
                        StudyLogger.hashMap["tc_button_pressed"] = System.currentTimeMillis()
                        captureImageWithPreviewExtraction()
                    }
                }
            }
            mCaptureButton.setOnClickListener(mCaptureButtonListener)
        }


        if (!mSelectDeviceButton.hasOnClickListeners()) {
            mSelectDeviceButtonListener = View.OnClickListener {
                openSelectDeviceFragment()
            }
            mSelectDeviceButton.setOnClickListener(mSelectDeviceButtonListener)
        }
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            StudyLogger.hashMap["client_id"] = mUserID
            StudyLogger.hashMap["tc_button_pressed"] = System.currentTimeMillis()
            captureImageWithPreviewExtraction()
        }
        return true
    }

    //Callback for camera changes
    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            mCameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            finish()
        }
    }


    private fun openCamera(width: Int, height: Int) {
        setUpCameraOutputs(width, height)
        configureTransform(width, height)
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
            }

            manager.openCamera(mCameraId, mStateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Camera lock opening interrupted", e)
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val texture = mTextureView.surfaceTexture!!
            texture.setDefaultBufferSize(mPreviewSize.width, mPreviewSize.height)
            val surface = Surface(texture)

            //Capturing for preview
            mPreviewRequestBuilder =
                mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder.addTarget(surface)

            mCameraDevice.createCaptureSession(
                listOf(surface, mImageReader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        mCaptureSession = cameraCaptureSession
                        try {
                            mPreviewRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )

                            mPreviewRequest = mPreviewRequestBuilder.build()
                            mCaptureSession.setRepeatingRequest(
                                mPreviewRequest,
                                null, null
                            )
                            StudyLogger.hashMap["preview_width"] = mPreviewSize.width
                            StudyLogger.hashMap["preview_height"] = mPreviewSize.height
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(
                        cameraCaptureSession: CameraCaptureSession
                    ) {
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun setUpCameraOutputs(width: Int, height: Int) {
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                // Use largest size available
                val largest: Size = listOf(map.getOutputSizes(ImageFormat.JPEG))[0][0]

                mImageReader = ImageReader.newInstance(
                    largest.width, largest.height,
                    ImageFormat.JPEG, 2
                )

                //Callback for receiving images as soon as they are available
//                mImageReader!!.setOnImageAvailableListener({ reader ->
//                    var image: Image? = null
//                    try {
//                        image = reader!!.acquireLatestImage()
//                        val greyImg = savePhotoToDisk(null, image, null, 512)
//                        sendFile(greyImg, mServerURL)
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                    }
//                    image?.close()
//                }, mBackgroundHandler)


                val displaySize = Point()
                windowManager.defaultDisplay.getSize(displaySize)
                var maxPreviewWidth: Int = displaySize.x
                var maxPreviewHeight: Int = displaySize.y

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }


                mPreviewSize = chooseOptimalSize(
                    map.getOutputSizes(SurfaceTexture::class.java),
                    width, height, maxPreviewWidth,
                    maxPreviewHeight, largest
                )!!
                mCameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
        }
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0F, 0F,
            mPreviewSize.height.toFloat(),
            mPreviewSize.width.toFloat()
        )
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / mPreviewSize.height,
                viewWidth.toFloat() / mPreviewSize.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180F, centerX, centerY)
        }
        mTextureView.setTransform(matrix)
    }

    private fun chooseOptimalSize(
        choices: Array<Size>, textureViewWidth: Int,
        textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size
    ): Size? {


        var keyWidth: String = getString(R.string.ca_pref_preview_width)
        var keyHeight: String = getString(R.string.ca_pref_preview_height)

        var keyWidthView: String = getString(R.string.ca_pref_surface_view_width)
        var keyHeightView: String = getString(R.string.ca_pref_surface_view_height)

        val orientation = windowManager.defaultDisplay.rotation
        if (orientation == Surface.ROTATION_90 || orientation == Surface.ROTATION_270) {
            keyWidth = getString(R.string.ca_pref_preview_height)
            keyHeight = getString(R.string.ca_pref_preview_width)
            keyWidthView = getString(R.string.ca_pref_preview_height)
            keyHeightView = getString(R.string.ca_pref_surface_view_width)
        }

        if (!::sp.isInitialized) {
            setupSharedPref()
        }
        val mSavedWidth: Int = sp.getInt(keyHeight, 0)
        val mSavedHeight: Int = sp.getInt(keyWidth, 0)

        val w = aspectRatio.width
        val h = aspectRatio.height
        val mSavedWidthOfView: Int = sp.getInt(keyWidthView, 0)
        val mSavedHeightOfView: Int = sp.getInt(keyHeightView, 0)

        if ((mSavedWidth != 0 && mSavedHeight != 0) && (mSavedWidthOfView == w && mSavedHeightOfView == h)) {
            return Size(mSavedWidth, mSavedHeight)
        } else {
            sp.edit().putInt(keyWidthView, w).apply()
            sp.edit().putInt(keyHeightView, h).apply()
            // resolutions > preview Surface
            val bigEnough: MutableList<Size> = ArrayList()
            // resolutions < preview Surface
            val notBigEnough: MutableList<Size> = ArrayList()

            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w
                ) {
                    if (option.width >= textureViewWidth &&
                        option.height >= textureViewHeight
                    ) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            return when {
                bigEnough.size > 0 -> {
                    sp.edit()
                        .putInt(keyWidth, Collections.min(bigEnough, CompareSizesByArea()).width)
                        .apply()
                    sp.edit()
                        .putInt(keyHeight, Collections.min(bigEnough, CompareSizesByArea()).height)
                        .apply()
                    Collections.min(bigEnough, CompareSizesByArea())
                }
                notBigEnough.size > 0 -> {
                    sp.edit()
                        .putInt(keyWidth, Collections.max(notBigEnough, CompareSizesByArea()).width)
                        .apply()
                    sp.edit().putInt(
                        keyHeight,
                        Collections.max(notBigEnough, CompareSizesByArea()).height
                    ).apply()
                    Collections.max(notBigEnough, CompareSizesByArea())
                }
                else -> {
                    choices[0]
                }
            }
        }


    }

    internal class CompareSizesByArea : Comparator<Size?> {
        override fun compare(o1: Size?, o2: Size?): Int {
            if (o1 != null) {
                if (o2 != null) {
                    return java.lang.Long.signum(o1.width.toLong() * o2.height - o1.width.toLong() * o2.height)
                }
            }
            return -1
        }
    }

    override fun onStop() {
        super.onStop()
        handlerThread?.quitSafely()
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
                    openCamera(surfaceTextureHeight, surfaceTextureWidth)
                } else {
                    Toast.makeText(this,getString(R.string.permission_request_en), Toast.LENGTH_LONG).show()
                    verifyPermissions(this)
                }
            }
        }
    }

    //Capture an image by simply saving the current frame of the Texture View
    private fun captureImageWithPreviewExtraction() {
        isCapturing = true
        startTime = System.currentTimeMillis()
        var mBitmap: Bitmap? = mTextureView.bitmap
        val screenOrientation = windowManager.defaultDisplay.rotation

        // screen rotated
        if (screenOrientation != Surface.ROTATION_0 && mBitmap != null) {
            when (screenOrientation) {
                Surface.ROTATION_90 -> mBitmap = rotateBitmapAndAdjustRatio(mBitmap, -90F)
                Surface.ROTATION_270 -> mBitmap = rotateBitmapAndAdjustRatio(mBitmap, 90F)
                Surface.ROTATION_180 -> mBitmap = rotateBitmap(mBitmap, 180F)
            }
        }
        // screen rotation locked, but physical phone rotated
        else if(screenOrientation == Surface.ROTATION_0 && phoneOrientation != Surface.ROTATION_0 && mBitmap != null){
            when(phoneOrientation){
                Surface.ROTATION_90 -> mBitmap = rotateBitmap(mBitmap, -90F)
                Surface.ROTATION_270 -> mBitmap = rotateBitmap(mBitmap, 90F)
                Surface.ROTATION_180 -> mBitmap = rotateBitmap(mBitmap, 180F)
            }
        }

        if (mBitmap != null) {
            StudyLogger.hashMap["tc_image_captured"] = System.currentTimeMillis()   // image is in memory
            StudyLogger.hashMap["long_side"] = IMG_TARGET_SIZE
            val greyImg = rescale(mBitmap, IMG_TARGET_SIZE)
            val matchingOptions: HashMap<Any?, Any?>? = getMatchingOptionsFromPref()
            sendBitmap(greyImg, mServerURL, this, matchingOptions)
        }
    }


    private fun getUserID() {
        val sharedPreferences =
            this.getSharedPreferences("com.pda.screenshotmatcher2", Context.MODE_PRIVATE)
        mUserID = sharedPreferences.getString("uid", "").toString()
        if (mUserID.isEmpty()) {
            val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            mUserID = (1..20).map { allowedChars.random() }.joinToString("")
            sharedPreferences.edit().putString("uid", mUserID).apply()
        }
    }

    private fun getServerURL() {
        Thread {
            mServerUrlList = discoverServersOnNetwork(this, 49050, "")
        }.start()
    }

    fun onServerURLsGet(servers: List<Pair<String, String>>) {
        if (servers.isNotEmpty()) {
            updateServerUrlList(servers)
            if (isConnectedToServer && handlerThread!!.isAlive){
                runOnUiThread {
                    updateConnectedStatus(true)
                }
            }
        } else {
            updateConnectedStatus(false)
        }
    }



    private fun updateConnectedStatus(isConnected: Boolean, startHeartbeat: Boolean = true) {
        isConnectedToServer = isConnected
        if (isConnected){
            mSelectDeviceButton.background =
                resources.getDrawable(R.drawable.select_device_connected)

            if (phoneOrientation == Surface.ROTATION_0) {
                mServerUrlList.forEach {
                    if (it.first == mServerURL){
                        mSelectDeviceButtonText.text = it.second
                    }
                }
            }

            if (startHeartbeat){
                startHeartbeatThread()
            }
        } else{
            mServerURL = ""
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

    private fun startHeartbeatThread(){
        if (handlerThread?.isAlive!!){
            mHandler!!.sendMessage(mHandler!!.obtainMessage(2))
        }
    }

    fun onHeartbeatFail(){
        updateConnectedStatus(false)
        startDiscoverThread()
        Toast.makeText(this,getString(R.string.heartbeat_fail_en), Toast.LENGTH_SHORT).show()
    }

    private fun startDiscoverThread(){
        if (handlerThread?.isAlive!!) {
            mHandler!!.sendMessage(mHandler!!.obtainMessage(1))
        }
    }

    private fun updateServerUrlList(newServers: List<Pair<String, String>>) {
        var oldServerIsInNewList = false
        mServerUrlList = newServers
        mServerUrlList.forEach {
            if (it.first == mServerURL) {
                oldServerIsInNewList = true
                updateConnectedStatus(true)
            }
        }
        if (!oldServerIsInNewList){
            updateConnectedStatus(false)
        }
    }


    private fun startResultsActivity(matchID: String, img: ByteArray) {
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra("matchID", matchID)
            putExtra("img", img)
            putExtra("ServerURL", mServerURL)
//            putExtra("DownloadID", downloadID)
        }
        startActivityForResult(intent, RESULT_ACTIVITY_REQUEST_CODE)
    }

    fun onMatchResult(matchID: String, img: ByteArray) {
        isCapturing = false
        startResultsActivity(matchID, img)
    }

    fun onMatchRequestError(){
        isCapturing = false
        Toast.makeText(this, getString(R.string.match_request_error_en), Toast.LENGTH_LONG).show()
    }

    private fun openSelectDeviceFragment(withTransition: Boolean = true) {
        mSettingsButton.visibility = View.INVISIBLE

        mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_close_48))

        val selectDeviceFragment = SelectDeviceFragment()
        if (withTransition) {
            this.supportFragmentManager
                .beginTransaction()
                .add(R.id.camera_activity_frameLayout, selectDeviceFragment, "SelectDeviceFragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit()
        } else {
            this.supportFragmentManager
                .beginTransaction()
                .add(R.id.camera_activity_frameLayout, selectDeviceFragment, "SelectDeviceFragment")
                .commit()
        }
    }

    fun onSelectDeviceFragmentClosed() {
        mCaptureButton.setImageResource(R.drawable.ic_baseline_photo_camera_24)
        mCaptureButton.setOnClickListener(mCaptureButtonListener)
        mSelectDeviceButton.setOnClickListener(mSelectDeviceButtonListener)
        mSettingsButton.visibility = View.VISIBLE
    }


    fun openErrorFragment(uid: String) {
        isCapturing = false
        val errorFragment = ErrorFragment()
        val bundle = Bundle()
        bundle.putString(UID_KEY, uid)
        bundle.putString(URL_KEY, mServerURL)
        errorFragment.arguments = bundle

        this.supportFragmentManager
            .beginTransaction()
            .add(R.id.fragment_container_view, errorFragment, "ErrorFragment")
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
    }

    private fun openSettings() {
        val settingsFragment = SettingsFragment()

        this.supportFragmentManager
            .beginTransaction()
            .add(R.id.settings_fragment_container_view, settingsFragment, "SettingsFragment")
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
        mFragmentDarkBackground.visibility = View.VISIBLE
    }

    private fun openGallery(withTransition: Boolean = true, savedImageFiles: File? = null) {
        val galleryFragment = GalleryFragment()

        if (withTransition){
            this.supportFragmentManager
                .beginTransaction()
                .add(R.id.fragment_container_view, galleryFragment, "GalleryFragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit()
        } else {
            this.supportFragmentManager
                .beginTransaction()
                .add(R.id.fragment_container_view, galleryFragment, "GalleryFragment")
                .commit()
        }

        mFragmentDarkBackground.visibility = View.VISIBLE
    }

    fun openPreviewFragment(firstImage: File?, secondImage: File?, withTransition: Boolean = true) {
        val previewFragment = GalleryPreviewFragment()

        val bundle = Bundle()
        bundle.putSerializable(FIRST_IMAGE_KEY, firstImage)
        bundle.putSerializable(SECOND_IMAGE_KEY, secondImage)
        previewFragment.arguments = bundle

        if (withTransition){
            this.supportFragmentManager
                .beginTransaction()
                .add(R.id.gallery_fragment_body_layout, previewFragment, "PreviewFragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit()
        } else {
            this.supportFragmentManager
                .beginTransaction()
                .add(R.id.gallery_fragment_body_layout, previewFragment, "PreviewFragment")
                .commit()
        }

    }

    private fun setupSharedPref() {
        if (!::sp.isInitialized) {
            sp = PreferenceManager.getDefaultSharedPreferences(this)
            MATCHING_MODE_PREF_KEY = getString(R.string.settings_algorithm_key)
        }
    }

    private fun getMatchingOptionsFromPref(): HashMap<Any?, Any?>? {
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

    fun getServerUrlList(): List<Pair<String, String>>? {
        if (::mServerUrlList.isInitialized) {
            return mServerUrlList
        } else return null
    }

    fun setServerUrl(hostname: String) {
        mServerUrlList.forEach {
            if (it.second == hostname){
                mServerURL = it.first
            }
        }
        updateConnectedStatus(true)
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
                    updateConnectedStatus(isConnectedToServer, false)
                }
                Surface.ROTATION_90 -> {
                    mSelectDeviceButtonText.text = ""
                    mSelectDeviceButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_link_24_landscape))
                    mGalleryButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_image_24_landscape))
                }
                Surface.ROTATION_180 -> {
                    //same as normal portrait
                    mSelectDeviceButton.setImageResource(android.R.color.transparent)
                    mGalleryButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_image_24))
                    updateConnectedStatus(isConnectedToServer, false)
                }
                Surface.ROTATION_270 -> {
                    mSelectDeviceButtonText.text = ""
                    mSelectDeviceButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_link_24_landscape))
                    mGalleryButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_image_24_landscape2))
                }
            }
            rotateFragments()
            checkSensor = false
        }

    }

    private fun rotateFragments() {
        rotateGalleryFragment()
        rotateSelectDeviceFragment()
    }

    private fun rotateGalleryFragment() {
        val gFrag: GalleryFragment? =
            supportFragmentManager.findFragmentByTag("GalleryFragment") as GalleryFragment?
        if (gFrag != null && gFrag.isVisible &&gFrag.getOrientation() != phoneOrientation){
            var savedImageFiles = gFrag.removeThisFragmentForRotation()
            openGallery(false)
            if (savedImageFiles != null) {
                openPreviewFragment(savedImageFiles[0], savedImageFiles[1])
            }
        }
    }

    private fun rotateSelectDeviceFragment() {
        val sdFrag: SelectDeviceFragment? =
            supportFragmentManager.findFragmentByTag("SelectDeviceFragment") as SelectDeviceFragment?
        if (sdFrag != null && sdFrag.isVisible &&sdFrag.getOrientation() != phoneOrientation){
            sdFrag.removeThisFragmentForRotation()
            openSelectDeviceFragment(false)
        } else {
            when (phoneOrientation){
                Surface.ROTATION_0 -> {
                    mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_photo_camera_24))
                }
                Surface.ROTATION_90 -> {
                    mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_photo_camera_24_landscape))
                }
                Surface.ROTATION_180 -> {
                    //same as normal portrait
                    mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_photo_camera_24))
                }
                Surface.ROTATION_270 -> {
                    mCaptureButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_photo_camera_24_landscape2))
                }
            }

        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }
}