package com.pda.screenshotmatcher2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import kotlin.concurrent.thread

class SelectDeviceFragment : Fragment() {

    private lateinit var containerView: FrameLayout
    private lateinit var mSelectDeviceButton: ImageButton
    private lateinit var mBackButton: ImageButton
    private lateinit var mListView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var mServerList: ArrayList<String> = ArrayList()
    private lateinit var mView: View
    private var rotation: Int = 0
    private var removeForRotation: Boolean = false
    private lateinit var lastSelectedItem: TextView

    private lateinit var ca: CameraActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        if (this::mView.isInitialized) return mView

        if (activity is CameraActivity){
            ca = activity as CameraActivity
        }

        containerView = container as FrameLayout
        mView = inflater.inflate(R.layout.fragment_select_device, container, false)

        rotation = ca.phoneOrientation
        if (rotation == 0 || rotation == 2) {
            return mView
        }
        return rotateView(rotation * 90, mView)
    }

    private fun rotateView(rotationDeg: Int, v: View): View {
        var mRotatedView: View = v

        val container = containerView as ViewGroup
        val w = container.width
        val h = container.height
        mRotatedView.rotation = rotationDeg.toFloat();
        mRotatedView.translationX = ((w - h) / 2).toFloat();
        mRotatedView.translationY = ((h - w) / 2).toFloat();

        val lp = mView.layoutParams
        lp.height = w
        lp.width = h
        mRotatedView.requestLayout()
        return mRotatedView
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

    public fun getOrientation(): Int {
        return rotation
    }

    private fun removeThisFragment() {
        var ca: CameraActivity = requireActivity() as CameraActivity
        ca.onSelectDeviceFragmentClosed()
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }
    public fun removeThisFragmentForRotation() {
        removeForRotation = true
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.commit()
    }

    override fun onDestroy() {
        if(!removeForRotation){
            var ca: CameraActivity = requireActivity() as CameraActivity
            ca.onSelectDeviceFragmentClosed()
        }
        super.onDestroy()
    }




}