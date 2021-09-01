package com.pda.screenshotmatcher2.fragments.rotationFragments
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import androidx.fragment.app.FragmentTransaction
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.fragments.removeForRotation
import kotlin.concurrent.thread

class SelectDeviceFragment : RotationFragment() {
    private lateinit var mSelectDeviceButton: ImageButton
    private lateinit var mBackButton: ImageButton
    private lateinit var mListView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var mServerList: ArrayList<String> = ArrayList()
    private lateinit var lastSelectedItem: TextView


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initServerList()
        initViews()
    }

    private fun initServerList() {
        val l = ca.serverConnection.getServerUrlList()
        if (l != null){
            l.forEach { mServerList.add(it.second) }
        } else {
            thread {
                Thread.sleep(100)
                initServerList()
                if (::adapter.isInitialized){
                    requireActivity().runOnUiThread {  adapter.notifyDataSetChanged() }
                }
            }.start()
        }
    }

    private fun initViews() {
        mSelectDeviceButton = activity?.findViewById(R.id.select_device_button)!!
        mSelectDeviceButton.setOnClickListener { removeThisFragment() }
        mBackButton = activity?.findViewById(R.id.capture_button)!!
        mBackButton.setOnClickListener { removeThisFragment() }
        mListView = activity?.findViewById(R.id.select_device_fragment_list)!!
        adapter = ArrayAdapter(requireContext(),
            R.layout.select_device_list_item, mServerList)
        mListView.adapter = adapter
        mListView.onItemClickListener = AdapterView.OnItemClickListener { _, view, position, _ ->
            requireActivity().window.decorView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            if (::lastSelectedItem.isInitialized){
                lastSelectedItem.setTextColor(resources.getColor(R.color.white))
            }
                lastSelectedItem = view as TextView
                val itemView: TextView = view
                itemView.setTextColor(resources.getColor(R.color.connected_green))
                ca.serverConnection.setServerUrl(mServerList[position])
        }
    }

    override fun removeThisFragment(removeBackground: Boolean) {
        ca.onCloseSelectDeviceFragment()
        super.removeThisFragment(removeBackground)
    }
}