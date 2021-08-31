## ScreenshotMatcher App Documentation

## 🗂 Folder structure

- [[#🚛 Activities]]
- [[#🚗 Fragments]]
	- [[#🔄 Rotation Fragments]]
	- [[#⏸ Fixed Fragments]]
- [[#🔗 Network]]
- [[#👋 Helpers]]
- [[#🪵 Logger]]
- [[#👁 Views]]



## 🚛 Activities

### CameraActivity

#### Tasks:

- Display camera preview
- Take photos
- Discover/connect to servers
- Manage settings

#### Noteworthy code:

##### onCreate()

1. hideStatusAndActionBars() → hide status/action bars
2. setContentView() → load layout and views
3. verifyPermissions() → verifies all permissions 
	- read/write external storage
	- access wifi state
	- internet
	- camera
4. setupSharedPref() → initializes the PreferenceManager
5. createDeviceID() → creates unique ID for the device when using the app for the first time
6. initViews() → assigns views to variables
7. setViewListeners() → sets listeners
8. start [[#FragmentHandler]]
9. start [[#ServerConnection]]
10. start [[#CameraInstance]]

##### capturePhoto()

1. sets `isCapturing = true`
2. extracts bitmap from camera preview ([[#CameraInstance]])
3. converts image to greyscale ([[#PhotoConverter]])
4. sends image to server ([[#Http]])
5. listens to server response (→ [[#sendBitmap bitmap serverURL activity matchingOptions permissionToken|sendBitmap()]])

##### fillUpImageList()

Loads all images from the app directory into memory so that [[#GalleryFragment]] doesn't lag when opened up the first time

##### updateConnectionStatus()

- changes colour and text of the `mSelectDeviceButton` based on the connection status ([[#ServerConnection]].isConnectedToServer):
	- **true:** green, "HOSTNAME"
	- **false:** red, "not connected"

##### onPermissionDenied()

- sets `isCapturing = false`
- shows toast to the user, informing them that the matching permission was denied

##### onMatchResult(matchID, img)

- sets `isCapturing = false`
- starts [[#ResultsActivity]]

### ResultsActivity

#### Tasks:

- Display the cropped screenshot that was returned from the server
- optional: download and display full screenshot when the user requests it
- share/save screenshots
- return to [[#CameraActivity]]

#### Noteworthy code:

##### onCreate()

1. get serverURL/matchID/images from Intent
2. check whether it received a cropped image
	-  if not: enter fullScreenshotOnlyMode
		-  no switching between screenshots
		-  saving both images not allowed
3. downloadFullScreenshotInThread()

##### downloadFullScreenshotInThread()

1. starts download of full screenshot in a new thread 
2. → onScreenshotDownloaded()
3. saves screenshot to app directory, displays it on ImageView

##### saveCurrentPreviewImage()

1. saves the currently displayed image to the phone gallery
2. hasSharedImage = true → when closing the activity, the image(s) will also be saved to the app storage → accessed by [[#GalleryFragment]] 

##### saveBothImages()

1. only executed when the cropped and full screenshot are available
2. saves both images to phone gallery
3. hasSharedImage = true → when closing the activity, both images will also be saved to the app storage → accessed by [[#GalleryFragment]] 

##### shareImage()

1. opens android share context menu, allowing users to share the currently displayed screenshot
2. hasSharedImage = true → when closing the activity, both images will also be saved to the app storage → accessed by [[#GalleryFragment]] 

## 🚗 Fragments

### 🔄 Rotation Fragments

#### RotationFragment

##### Tasks:

= **abstract** class that `extends Fragment` and adds extra functionality allowing the fragment to change orientation without defining an extra layout
- rotation works by:
	1. removing the fragment (→ removeThisFragmentForRotation())
	2. getting a rotated view of the fragment (→ rotateView())
	3. attaching the rotated fragment

##### Noteworthy code:

###### rotateView(rotationDeg: Int, v: View)

called when the view gets created (or reloaded), returns view thats rotated by `rotationDeg` degrees

###### removeThisFragmentForRotation()

called when the view should be rotated (detach, rotate, attach), removes the view without an animation

###### removeThisFragment(removeBackground: Boolean)

called when the fragment should actually be closed (not rotated)
- removes the dark background of the fragment (if `removeBackground` = true)
- plays animation

#### SelectDeviceFragment

##### Tasks:

- display all available servers
- allow users to connect to servers on the list by clicking on them

##### Noteworthy code:

###### initServerList()

1. gets server list from [[#ServerConnection]] object in the [[#CameraActivity]]
2. (if not null): adds all servers from the list to the `mServerList` variable
3. (else): retry after 100ms

###### initViews()

1. initializes all views
2. attaches ArrayAdapter that fills up the ListView with the elements from `mServerList`
3. sets onClickListener that listens to clicks on items of the ListView
	- onClick:
		1. change color of clicked item
		2. call [[#setServerUrl hostname String|setServerUrl(hostname)]] of the [[#ServerConnection]] object in the [[#CameraActivity]] with the url of the clicked server item as parameter

#### GalleryFragment

##### Tasks:

- show a list of all cropped (and full) screenshots the user has taken and then either saved/shared in the [[#ResultsActivity]]
- allow users to click on screenshots → [[#GalleryPreviewFragment]]

##### Noteworthy code:

###### initViews()

1. initializes all view variables
2. attaches [[#GridBaseAdapter]] to the GridView

#### GalleryPreviewFragment

##### Tasks:

- allow users to save/share previously matched screenshots like in [[#ResultsActivity]]

##### Noteworthy code:

similar to [[#ResultsActivity]]

###### getFilesFromBundle

- retrieves the provided image files from the bundle
- increments `numberOfAvailableImages` for each image retrieved


### ⏸ Fixed Fragments

#### ErrorFragment

##### Tasks:

- notify the user when there is not match result
- allow user to open the feedback fragment
- allow user to request and show the full screenshot

#### FeedbackFragment

##### Tasks:

- allow user to send feedback to a server

#### SettingsFragment

##### Tasks:

- allow user to modify app preferences (currently only matching mode)
- the preference layout (and the keys to access the values) are stored in "/res/xml/rood_preferences.xml"

##### List of preferences keys:

- **settings_algorithm_key:** "ALG_MODE_KEY" (stored in "/values/strings.xml", access via getString(@StringRes int resId))
- **settings_logging_key:** "LOG_MODE_KEY" (stored in "/values/strings.xml", accessed via getString(@StringRes int resId))

###### Example:

- get algorithm mode key: `MATCHING_MODE_PREF_KEY = getString(R.string.settings_algorithm_key)`

##### Accessing preferences:

1. get default shared preferences via: `val sp = PreferenceManager.getDefaultSharedPreferences(context: Context)`
2. access the preferences via key value principle e.g.: `sp.getBoolean(PREFERENCE_KEY_HERE: String, defaultValue: Boolean)`

##### Noteworthy code:

###### onCreatePreferences()

- loads `root_preferences` layout which lets users change predefined preferences


## 👋 Helpers


#### CameraActivityFragmentHandler

##### Tasks:

- outsource code to launch fragments from the [[#CameraActivity]] to an extra file
- launch any [[#CameraActivity]] fragments

##### Noteworthy code:

###### openFragment(fragment: Fragment, containerID: Int, transition: Int? = null)

- attaches the provided fragment in the given container (with an animation) 


###### rotateGalleryFragment()

1. removes the [[#GalleryFragment]] if it is open ([[#removeThisFragmentForRotation|removeThisFragmentForRotation()]])
2. re-attaches ([[#openFragment fragment Fragment containerID Int transition Int null|openFragment()]])
3. re-attaches the [[#GalleryPreviewFragment]] if it was open


#### PhotoConverter

##### Tasks:

- rescale a photo and convert it to grayscale

#### Utils


##### Tasks:

- provide various utility functions


##### Noteworthy code:

###### CompareSizesByArea: Comparator

- comparator class used by [[#CameraInstance]] to compare camera preview sizes

## 🔗 Network

### ServerConnection

#### Tasks:

- start network discovery if not connected to a server, update `mServerUrlList`
- send heartbeats to a server if connected to one
- [[#updateConnectionStatus|update]] UI in [[#CameraActivity]] accordingly

##### Noteworthy code:

###### mHandler: Handler

- a handler object that manages all runnables based on the `msg: Message` it receives:
	- `END_ALL_THREADS` → kills all active runnables
	- `START_DISCOVER` → kills [[#heartbeatRunnable Runnable|heartbeatRunnable]], starts [[#discoverRunnable Runnable|discoverRunnable]]
	- `START_HEARTBEAT` → kills [[#discoverRunnable Runnable|discoverRunnable]], starts [[#heartbeatRunnable Runnable|heartbeatRunnable]]

###### startHeartbeatThread()

- sends a `msg: Message` to the [[#mHandler Handler]] that starts the [[#heartbeatRunnable Runnable|heartbeatRunnable]]

###### startDiscoverThread()

- sends a `msg: Message` to the [[#mHandler Handler]] that starts the [[#discoverRunnable Runnable|discoverRunnable]]

###### discoverRunnable: Runnable

- a runnable that runs the server discovery (→ [[#discoverServersOnNetwork]]) every 5s
- callback: [[#onServerURLsGet servers List Pair String String|onServerURLsGet()]]

###### onServerURLsGet(servers: List<Pair<String, String>>)

- if the returned list is not empty:
	- updates `serverUrlList` and checks if `mServerURL` is in the list
	- if it is: change connection to connected (→ [[#onConnectionChanged isConnected Boolean|onConnectionChanged(true)]]) 
	
###### onConnectionChanged(isConnected: Boolean)

- `isConnected = true`: [[#startHeartbeatThread]]
- `isConnected = false`: [[#startDiscoverThread]]
- calls [[#updateConnectionStatus|updateConnectionStatus()]] in [[#CameraActivity]]


###### heartbeatRunnable: Runnable

- a runnable that sends a heartbeat request (→ [[#sendHeartbeatRequest serverURL activity|sendHeartbeatRequest()]]) to the connected server every 5s
- if heartbeat fails → [[#onHeartbeatFail]]

###### onHeartbeatFail()

- notifies the user via Toast that the server disconnected
- calls [[#onConnectionChanged isConnected Boolean|onConnectionChanged(false)]]

###### setServerUrl(hostname: String)

- sets `mServerURL` to an url that belongs to the hostname provided in the parameters via `hostname: String`

### NetworkDiscovery

#### Tasks

- provide functions for discovering servers in the local network

#### Noteworthy code:

##### discoverServersOnNetwork()

- *todo: @TF*


### Http

#### Tasks

- works as a helper class for all HTTP related tasks:
	- send bitmap to server → [[#sendBitmap bitmap serverURL activity matchingOptions permissionToken|sendBitmap()]]
	- send heartbeats → [[#sendHeartbeatRequest serverURL activity|sendHeartbeatRequest()]]
	- send feedback to server → (from [[#FeedbackFragment]])
	- request permission from server to match screenshots → [[#requestPermission bitmap serverURL activity matchingOptions|requestPermission()]]
	- send logs to logging server → **sendLog()** 

#### Noteworthy code:

##### sendBitmap(bitmap, serverURL, activity, matchingOptions, permissionToken)

1. encodes `bitmap` (= camera image) to a base64 string and appends it to JSON
2. appends `matchingOptions`, `deviceName`, `deviceID` to JSON
3. adds `permissionToken`, if not empty, to JSON
4. sends request, waits for these possible responses:
	- **error** → call [[#onPermissionDenied]]
	- **response with url param "error" = "permissionRequired"** → [[#requestPermission bitmap serverURL activity matchingOptions|requestPermission()]]
	- **response with url param "hasResult" = true** → decode base64 to bitmap call [[#onMatchResult matchID img|onMatchResult()]]
	- **result with url param "hasResult" = false** → start [[#ErrorFragment]]

##### sendHeartbeatRequest(serverURL, activity)

1. makes GET request to the servers heartbeat route
2. if it returns a response, do nothing
3. if it returns an error, call [[#onHeartbeatFail]]

##### requestPermission(bitmap, serverURL, activity, matchingOptions)

1. @TF

## 👁 Views

### CameraInstance

#### Tasks

- provide a helper class for the [[#CameraActivity]] that works abstracts the [Camera 2 API](https://developer.android.com/reference/android/hardware/camera2/package-summary):
	- initializing the TextureView
	- choosing a fitting preview size
	- setting up camera outputs
	- listen to camera state callbacks

#### Noteworthy code:

##### initializeTextureView()

- initializes the TextureView found in the [[#CameraActivity]] layout
- sets callback object that listens to changes of the TextureView
	- `onSurfaceTextureAvailable` = the starting point of the application code

##### openCamera(width: Int, height: Int)

1. [[#setupCameraOutputs width Int height Int|setupCameraOutputs()]] → obtained `mPreviewSize`
2. checks for CAMERA permission
3. opens camera using CameraManager, [[#mCameraDeviceStateCallback]] as callback

##### setupCameraOutputs (width: Int, height: Int)

- helper function for determining the optimal camera output size (= preview size)
- checks each output resolution for each camera of the device and picks the biggest one with a 4:3 ratio → `mPreviewSize`

##### mCameraDeviceStateCallback

= a callback object for listening to camera device state changes:
	- onOpened() → [[#createCameraCaptureSession]]
	- onDisconnected() → closes `cameraDevice`
	- onError() → finishes [[#CameraActivity]]

##### createCameraCaptureSession()

1. creates a Surface from `mTextureView`
2. chooses this Surface as target for displaying the camera preview
3. creates a CaptureRequest, [[#mCameraDeviceStateCallback]] as callback object

##### mCameraCaptureSessionStateCallback

= a callback object for listening to CaptureSession callbacks
	- onConfigured() 
		- → sets a repeating request on the CameraSession
		- → preview gets displayed on the Surface of `mTextureView`
	- onConfigureFailed() → throws RuntimeException

### GridBaseAdapter

#### Tasks

- provide all Views (= screenshot thumbnails) for the [[#GalleryFragment]]

#### Noteworthy code:

##### getView(position: Int, convertView: View, parent: ViewGroup)

- returns a View based on the provided `position`
- sets an onClickListener on each returned View → onClick: `openGalleryPreviewFragment(firstImageFile, secondImageFile)` of [[#CameraActivityFragmentHandler]]

##### ListRowHolder

- private helper class for storing the information about which images are stored in a returned View

## 🪵 Logger

### Logger

- @TF
