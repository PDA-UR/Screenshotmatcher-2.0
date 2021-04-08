package com.pda.screenshotmatcher2

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.get
import androidx.fragment.app.FragmentTransaction
import kotlin.concurrent.thread

class SelectDeviceFragment : Fragment() {

    private lateinit var containerView: FrameLayout
    private lateinit var mSelectDeviceButton: ImageButton
    private lateinit var mBackButton: ImageButton
    private lateinit var mListView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var mServerList: ArrayList<String> = ArrayList()

    private lateinit var lastSelectedItem: TextView

    private lateinit var ca: CameraActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        containerView = container as FrameLayout
        return inflater.inflate(R.layout.fragment_select_device, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initServerList()
        initViews()
    }

    private fun initServerList() {
        ca = requireActivity() as CameraActivity
        val l = ca.getServerUrlList()
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
        adapter = ArrayAdapter(requireContext(), R.layout.select_device_list_item, mServerList)
        mListView.adapter = adapter
                mListView.onItemClickListener = AdapterView.OnItemClickListener { _, view, position, _ ->
            if (::lastSelectedItem.isInitialized){
                lastSelectedItem.setTextColor(resources.getColor(R.color.white))
            }
                lastSelectedItem = view as TextView
                val itemView: TextView = view
                itemView.setTextColor(resources.getColor(R.color.connected_green))
                ca.setServerUrl(mServerList[position])
        }
    }

    private fun removeThisFragment() {
        var ca: CameraActivity = requireActivity() as CameraActivity
        ca.onSelectDeviceFragmentClosed()

        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }

}