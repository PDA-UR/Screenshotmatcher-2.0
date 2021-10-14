package com.pda.screenshotmatcher2.views.fragments.rotationFragments
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.viewModels.ServerConnectionViewModel

class SelectDeviceFragment : RotationFragment() {
    private lateinit var mSelectDeviceButton: ImageButton
    private lateinit var mListView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var mServerList: ArrayList<String> = ArrayList()
    private lateinit var lastSelectedItem: TextView
    private lateinit var mHandler: Handler

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mHandler = Handler(Looper.getMainLooper())
        initServerList()
        initViews()
    }


    private lateinit var serverConnectionViewModel: ServerConnectionViewModel

    private fun initServerList() {
        serverConnectionViewModel = ViewModelProvider(requireActivity(), ServerConnectionViewModel.Factory(requireActivity().application)).get(ServerConnectionViewModel::class.java).apply {
            getServerUrlListLiveData().observe(viewLifecycleOwner, Observer {
                urlList ->
                run {
                    mServerList.clear()
                    urlList.forEach { pair: Pair<String, String> ->
                        mServerList.add(pair.second)
                    }
                    adapter.notifyDataSetChanged()
                }
            })
        }
    }

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
                lastSelectedItem.setTextColor(resources.getColor(R.color.white))
            }
                lastSelectedItem = view as TextView
                val itemView: TextView = view
                itemView.setTextColor(resources.getColor(R.color.connected_green))
                serverConnectionViewModel.setServerUrl(mServerList[position])
        }
    }

    override fun removeThisFragment(removeBackground: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            ca.window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }
        ca.onCloseSelectDeviceFragment()
        super.removeThisFragment(removeBackground)
    }

    override fun onDestroy() {
        super.onDestroy()
        mHandler.removeCallbacksAndMessages(null)
    }
}