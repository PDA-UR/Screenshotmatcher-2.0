package com.pda.screenshotmatcher2.views

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.activities.CameraActivity
import java.io.File


class GridBaseAdapter(context: Context): BaseAdapter () {

    private val context = context
    private val activity = context as CameraActivity
    private var imageArray = activity.imageArray

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        val mInflator: LayoutInflater = LayoutInflater.from(context)
        var view: View = mInflator.inflate(R.layout.grid_view_item, parent, false)
        var vh = ListRowHolder(view)
        view.tag = vh

        view.setOnClickListener {
            val firstImageFile = vh.firstImageFile
            val secondImageFile = vh.secondImageFile
            val ca: CameraActivity = context as CameraActivity
            ca.fragmentHandler.openGalleryPreviewFragment(firstImageFile, secondImageFile)
        }

        Glide.with(context)
            .load(imageArray[position][0])
            .placeholder(R.drawable.fragment_normal_button_small)
            .centerCrop()
            .into(vh.firstImage)
        vh.firstImage.visibility = View.VISIBLE
        vh.firstImageFile = imageArray[position][0]
        if (imageArray[position].size > 1){
            Glide.with(context)
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
