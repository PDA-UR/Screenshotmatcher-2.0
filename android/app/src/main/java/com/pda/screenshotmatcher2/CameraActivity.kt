package com.pda.screenshotmatcher2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentTransaction
import androidx.preference.PreferenceManager
import java.io.File
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.thread


class CameraActivity : AppCompatActivity() {
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

    //Handler for background tasks
    private lateinit var mBackgroundHandler: Handler

    //Handles still image capture
    private lateinit var mImageReader: ImageReader

    //Camera preview builder
    private lateinit var mPreviewRequestBuilder: CaptureRequest.Builder

    //Request from builder
    private lateinit var mPreviewRequest: CaptureRequest

    //Prevent exit before closing cam
    private var mCameraOpenCloseLock: Semaphore = Semaphore(1)

    //Permission ID
    private val REQUEST_CAMERA_PERMISSION: Int = 1
    private var surfaceTextureHeight: Int = 0
    private var surfaceTextureWidth: Int = 0

    //Other UI Views
    private lateinit var mSelectDeviceButton: ImageButton
    private lateinit var mSelectDeviceList: ListView
    private lateinit var mCaptureButton: ImageButton
    private lateinit var mFragmentDarkBackground: FrameLayout
    private lateinit var mSelectDeviceButtonText: TextView
    private lateinit var mSettingsButton: ImageButton
    private lateinit var mGalleryButton: ImageButton

    private var mServerURL: String = ""
    private var mUserID: String = ""
    private var startTime: Long = 0

    //Shared preferences
    private lateinit var sp: SharedPreferences
    private lateinit var MATCHING_MODE_PREF_KEY: String

    //Old images gallery
    lateinit var imageDirectory: File
    lateinit var files: Array<File>
    lateinit var imageArray: ArrayList<ArrayList<File>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        verifyPermissions(this)
        getServerURL()
        getUserID()
        initViews()
        hideStatusAndActionBars()
        setViewListeners()
        setupSharedPref()
        if (!this::imageArray.isInitialized) {
            thread { fillUpImageList() }
        }
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
        if (!::mTextureView.isInitialized){
            mTextureView = findViewById(R.id.preview_view)
            mCaptureButton = findViewById(R.id.capture_button)
            mSelectDeviceButton = findViewById(R.id.select_device_button)
            mSelectDeviceButton.background =
                resources.getDrawable(R.drawable.select_device_disconnected)
            mSelectDeviceButtonText = findViewById(R.id.camera_activity_select_device_text)
            mSelectDeviceButtonText.text = getText(R.string.select_device_button_notConnected_en)
            mSelectDeviceList = findViewById(R.id.select_device_list)
            mFragmentDarkBackground = findViewById(R.id.ca_dark_background)
            mFragmentDarkBackground.setOnClickListener { Log.d("bg", "background clicked") }
            mSettingsButton = findViewById(R.id.camera_activity_settings_button)
            mSettingsButton.setOnClickListener { openSettings() }
            mGalleryButton = findViewById(R.id.camera_activity_gallery_button)
            mGalleryButton.setOnClickListener { openGallery() }
        }
    }

    private fun setViewListeners() {
        if (!mTextureView.hasOnClickListeners()){
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

        if (!mCaptureButton.hasOnClickListeners()){
            mCaptureButton.setOnClickListener {
                StudyLogger.hashMap["client_id"] = mUserID
                StudyLogger.hashMap["tc_button_pressed"] = System.currentTimeMillis()
                captureImageWithPreviewExtraction()
            }
        }


        if (!mSelectDeviceButton.hasOnClickListeners()){
            mSelectDeviceButton.setOnClickListener {
                if (mSelectDeviceList.isVisible) {
                    mSelectDeviceList.visibility = View.INVISIBLE
                    getServerURL()
                } else {
                    mSelectDeviceList.visibility = View.VISIBLE
                }
            }
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
            Log.d("CAMERA", "Camera opened")
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
                throw RuntimeException("Camera lock opening timeout")
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
                            Log.d("CAMERA", "Start Preview with size: $mPreviewSize")
                            StudyLogger.hashMap["preview_width"] = mPreviewSize.width
                            StudyLogger.hashMap["preview_height"] = mPreviewSize.height
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(
                        cameraCaptureSession: CameraCaptureSession
                    ) {
                        Log.d("CAMERA", "camera config Failed")
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
            Log.d("CAMERA", "Camera2 API unavailable")
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


        var keyWidth: String = "previewWidthPortrait"
        var keyHeight: String = "previewHeightPortrait"

        var keyWidthView: String = "ViewPreviewWidthPortrait"
        var keyHeightView: String = "ViewPreviewHeightPortrait"

        val orientation = windowManager.defaultDisplay.rotation
        if (orientation == Surface.ROTATION_90 || orientation == Surface.ROTATION_270) {
            Log.d("CA", "Landscape")
            keyWidth = "previewHeightPortrait"
            keyHeight = "previewWidthPortrait"
            keyWidthView = "ViewPreviewWidthPortrait"
            keyHeightView = "ViewPreviewHeightPortrait"
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
            Log.d("CA", "Using old width")
            return Size(mSavedWidth, mSavedHeight)
        } else {
            sp.edit().putInt(keyWidthView, w).apply()
            sp.edit().putInt(keyHeightView, h).apply()
            // resolutions > preview Surface
            val bigEnough: MutableList<Size> = ArrayList()
            // resolutions < preview Surface
            val notBigEnough: MutableList<Size> = ArrayList()

            for (option in choices) {
                Log.d("CAMERA", "Checking option $option")
                if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w
                ) {
                    if (option.width >= textureViewWidth &&
                        option.height >= textureViewHeight
                    ) {
                        bigEnough.add(option)
                        Log.d("CAMERA", "Added $option to big resolutions")
                    } else {
                        notBigEnough.add(option)
                        Log.d("CAMERA", "Added $option to small resolutions")
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
                    Log.e("CAMERA", "No suitable preview size")
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


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("CAMERA", "Permission Callback Code $requestCode")
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    for (permission in permissions) {
                        Log.d("CAMERA", "GOT permission $permission")
                    }
                    Log.d("CAMERA", "Got Permissions")
                    openCamera(surfaceTextureHeight, surfaceTextureWidth)

                } else {
                    Log.d("CAMERA", "No permission")
                }
            }
        }
    }

    //Capture an image by simply saving the current frame of the Texture View
    private fun captureImageWithPreviewExtraction() {
        Log.v("TIMING", "Button pressed")
        startTime = System.currentTimeMillis()
        var mBitmap: Bitmap? = mTextureView.bitmap

        val orientation = windowManager.defaultDisplay.rotation
        if (orientation != Surface.ROTATION_0 && mBitmap != null) {
            Log.d("CA", "Start rotate")
            when (orientation) {
                Surface.ROTATION_90 -> mBitmap = rotateBitmapAndAdjustRatio(mBitmap, -90F)
                Surface.ROTATION_270 -> mBitmap = rotateBitmapAndAdjustRatio(mBitmap, 90F)
            }
            Log.d("CA", "End rotate")
        }

        if (mBitmap != null) {
            StudyLogger.hashMap["tc_image_captured"] = System.currentTimeMillis()
            StudyLogger.hashMap["long_side"] = IMG_TARGET_SIZE
            val greyImg = rescale(mBitmap, IMG_TARGET_SIZE)
            Log.v("TIMING", "Image rescaled.")
            var matchingOptions: HashMap<Any?, Any?>? = getMatchingOptionsFromPref()
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
        Log.v("TEST", mUserID)
    }

    private fun getServerURL() {
        Thread {
            Log.v("TEST", "discovering server...")
            mServerURL = discoverServersOnNetwork(this, 49050, "")[0].first   //TODO: properly use the entire list

        }.start()
    }

    fun onServerURLsGet(servers: List<Pair<String,String>>) {
        Thread {
            Log.v("TIMING", "Got URL")
            runOnUiThread {
                mSelectDeviceButton.background =
                    resources.getDrawable(R.drawable.select_device_connected)
                mSelectDeviceButtonText.text = servers[0].first   // TODO: properly use the entire list
            }
        }.start()
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
        startResultsActivity(matchID, img)
    }


    fun openErrorFragment(uid: String, url: String) {
        Log.d("FRAG", "opening fragment")
        val errorFragment = ErrorFragment()

        val bundle = Bundle()
        bundle.putString(UID_KEY, uid)
        bundle.putString(URL_KEY, url)
        errorFragment.arguments = bundle

        this.supportFragmentManager
            .beginTransaction()
            .add(R.id.fragment_container_view, errorFragment, "ErrorFragment")
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
        mFragmentDarkBackground.visibility = View.VISIBLE
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

    private fun openGallery() {
        val galleryFragment = GalleryFragment()
        this.supportFragmentManager
            .beginTransaction()
            .add(R.id.fragment_container_view, galleryFragment, "GalleryFragment")
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
        mFragmentDarkBackground.visibility = View.VISIBLE
    }

    fun openPreviewFragment(firstImage: File?, secondImage: File?) {
        val previewFragment = GalleryPreviewFragment()

        val bundle = Bundle()
        bundle.putSerializable(FIRST_IMAGE_KEY, firstImage)
        bundle.putSerializable(SECOND_IMAGE_KEY, secondImage)
        previewFragment.arguments = bundle

        this.supportFragmentManager
            .beginTransaction()
            .add(R.id.gallery_fragment_body_layout, previewFragment, "PreviewFragment")
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
    }

    private fun setupSharedPref() {
        if(!::sp.isInitialized){
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

}