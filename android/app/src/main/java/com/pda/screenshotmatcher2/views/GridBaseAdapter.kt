package com.pda.screenshotmatcher2.views

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.bumptech.glide.Glide
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.activities.CameraActivity
import com.pda.screenshotmatcher2.fragments.rotationFragments.GalleryFragment
import com.pda.screenshotmatcher2.viewModels.GalleryViewModel
import java.io.File


class GridBaseAdapter(private val context: GalleryFragment): BaseAdapter () {

    private var imageArray : ArrayList<ArrayList<File>> = ArrayList()
    private val gfvm = ViewModelProvider(context, GalleryViewModel.Factory(context.requireActivity().application)).get(
        GalleryViewModel::class.java).apply {
        getImages().observe(context.viewLifecycleOwner, Observer { images ->
           imageArray = images
        })
    }
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        val mInflator: LayoutInflater = LayoutInflater.from(context.activity)
        var view: View = mInflator.inflate(R.layout.grid_view_item, parent, false)
        var vh = ListRowHolder(view)
        view.tag = vh

        view.setOnClickListener {
            val firstImageFile = vh.firstImageFile
            val secondImageFile = vh.secondImageFile
            val ca: CameraActivity = context.activity as CameraActivity
            ca.cameraActivityFragmentHandler.openGalleryPreviewFragment(firstImageFile, secondImageFile)
        }

        Glide.with(context.requireActivity())
            .load(imageArray[position][0])
            .placeholder(R.drawable.fragment_normal_button_small)
            .centerCrop()
            .into(vh.firstImage)
        vh.firstImage.visibility = View.VISIBLE
        vh.firstImageFile = imageArray[position][0]
        if (imageArray[position].size > 1){
            Glide.with(context.requireActivity())
                .load(imageArray[position][1])
                .placeholder(R.drawable.fragment_normal_button_small)
                .centerCrop()
                .into(vh.secondImage)
            vh.secondImage.visibility = View.VISIBLE
            vh.secondImageFile = imageArray[position][1]
            vh.secondImage.rotation = 5F
            vh.firstImage.rotation = -5F
        }
        return view
    }

    override fun getItem(position: Int): Any {
        return imageArray[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return imageArray.size
    }
}

private class ListRowHolder(row: View?) {
    var firstImageFile: File? = null
    var secondImageFile: File? = null
    val firstImage: ImageView = row?.findViewById(R.id.grid_view_item_first_image) as ImageView
    val secondImage: ImageView = row?.findViewById(R.id.grid_view_item_second_image) as ImageView
}
