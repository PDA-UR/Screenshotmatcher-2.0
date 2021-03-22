package com.pda.screenshotmatcher2

import android.icu.text.Edits
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import java.io.File
import kotlin.concurrent.thread

class GalleryFragment : Fragment() {

    //Views
    lateinit var containerView: FrameLayout
    lateinit var mFragmentBackground: FrameLayout
    lateinit var mGridView: GridView

    lateinit var imageDirectory: File
    lateinit var files: Array<File>
    private var imageArray: ArrayList<ArrayList<File>> = ArrayList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        containerView = container as FrameLayout
        containerView.visibility = View.VISIBLE
        return inflater.inflate(R.layout.fragment_gallery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        imageDirectory = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        files = imageDirectory.listFiles()
        initViews()
    }

    private fun fileBelongsToImageArrayItem(file: File, item: ArrayList<File>): Boolean {
        //Item has already 2 entries
        var filename: String = file.name.split("_".toRegex()).first()
        val itemName: String = item[0].name.split("_".toRegex()).first()

        Log.d("GF", "$filename and $itemName")

        if(item.size == 2){
            Log.d("GF", "full list")
            return false
        }

        return filename == itemName
    }

    private fun fillUpImageList() {
        files.forEach outer@{ file ->
            if (imageArray.isNotEmpty()){
                imageArray.forEach inner@{ item ->
                    if(fileBelongsToImageArrayItem(file, item)){
                        item.add(file)
                        Log.d("GF", "added ${file.name} to existing list")
                        return@outer
                    }
                }

                var fileCouple: ArrayList<File> = ArrayList()
                fileCouple.add(file)
                imageArray.add(fileCouple)
                Log.d("GF", "added ${file.name} to new list")

            } else {
                var fileCouple: ArrayList<File> = ArrayList()
                fileCouple.add(file)
                imageArray.add(fileCouple)
                Log.d("GF", "added ${file.name} to new list as init")
            }
        }

    }

    private fun initViews() {
        mFragmentBackground = activity?.findViewById(R.id.ca_dark_background)!!
        mFragmentBackground.setOnClickListener { removeThisFragment(true) }
        mFragmentBackground.visibility = View.VISIBLE
        mGridView = activity?.findViewById(R.id.gallery_fragment_gridview)!!

        val adapter = GridBaseAdapter(requireContext())
        mGridView.adapter = adapter

    }

    private fun removeThisFragment(removeBackground: Boolean = true) {
        containerView.visibility = View.INVISIBLE
        if (removeBackground) {
            mFragmentBackground.visibility = View.INVISIBLE
        }
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)?.commit()
    }
}