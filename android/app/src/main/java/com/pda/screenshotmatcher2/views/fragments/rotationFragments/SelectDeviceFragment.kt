package com.pda.screenshotmatcher2.views.fragments.rotationFragments
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.viewModels.ServerConnectionViewModel

/**
 * [RotationFragment] to connect to a matching server (= pc/laptop)
 *
 * @property mSelectDeviceButton The select device button of [CameraActivity][com/pda/screenshotmatcher2/views/activities/CameraActivity.kt], opens/closes this fragment
 * @property mListView The list view containing all available servers
 * @property adapter The adapter to fill up [mListView]
 * @property mServerList The list of all available servers, updated by [ServerConnectionViewModel]
 * @property lastSelectedItem The last selected item in [mListView], green highlight
 * @property serverConnectionViewModel The [ServerConnectionViewModel], provides two way data bindings for [ServerConnectionViewModel.serverUrlList]
 */
class SelectDeviceFragment : RotationFragment() {
    private lateinit var mSelectDeviceButton: ImageButton
    private lateinit var mListView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var mServerList: ArrayList<String> = ArrayList()
    private lateinit var lastSelectedItem: TextView
    private var serverConnectionViewModel: ServerConnectionViewModel? = null

    /**
     * Called when the fragment is created, calls [initServerList] and [initViews].
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initServerList()
        initViews()
    }

    /**
     * Registers an observer for [ServerConnectionViewModel.getServerUrlListLiveData], which updates  [mServerList] and refreshes [adapter] on data change.
     */
    private fun initServerList() {
        serverConnectionViewModel = ViewModelProvider(requireActivity(), ServerConnectionViewModel.Factory(requireActivity().application)).get(ServerConnectionViewModel::class.java).apply {
            getServerUrlListLiveData().observe(viewLifecycleOwner) { urlList ->
                run {
                    mServerList.clear()
                    urlList.forEach { pair: Pair<String, String> ->
                        mServerList.add(pair.second)
                    }
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    /**
     * Initiates all views and sets their listeners.
     */
    private fun initViews() {
        mSelectDeviceButton = activity?.findViewById(R.id.select_device_button)!!
        mSelectDeviceButton.setOnClickListener { removeThisFragment() }
        mListView = activity?.findViewById(R.id.select_device_fragment_list)!!
        adapter = ArrayAdapter(requireContext(),
            R.layout.select_device_list_item, mServerList)
        mListView.adapter = adapter
        mListView.onItemClickListener = AdapterView.OnItemClickListener { _, view, position, _ ->
            requireActivity().window.decorView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (::lastSelectedItem.isInitialized){
                lastSelectedItem.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            }
                lastSelectedItem = view as TextView
                val itemView: TextView = view
                itemView.setTextColor(ContextCompat.getColor(requireContext(), R.color.connected_green))
                // add the selected server url to shared preferences
                val knownList = PreferenceManager.getDefaultSharedPreferences(requireContext()).getStringSet(context?.getString(R.string.KNOWN_SERVERS_KEY), setOf())
                PreferenceManager.getDefaultSharedPreferences(context).edit().putStringSet(context?.getString(R.string.KNOWN_SERVERS_KEY), knownList?.plus(mServerList[position])).apply()
                serverConnectionViewModel?.setServerUrl(mServerList[position])
        }
    }

    /**
     * Removes this fragment.
     *
     * Plays a short vibration & animation to indicate that the fragment is removed.
     */
    override fun removeThisFragment(removeBackground: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            ca?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }
        ca?.onCloseSelectDeviceFragment()
        clearGarbage()
        super.removeThisFragment(removeBackground)
    }

    override fun clearGarbage() {
        super.clearGarbage()
        mListView.onItemClickListener = null
        serverConnectionViewModel = null
        ca = null
    }
}