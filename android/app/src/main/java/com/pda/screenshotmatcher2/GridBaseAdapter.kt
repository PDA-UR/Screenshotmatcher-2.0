package com.pda.screenshotmatcher2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import com.bumptech.glide.Glide
import java.io.File


class GridBaseAdapter(context: Context): BaseAdapter () {

    private val mInflator: LayoutInflater = LayoutInflater.from(context)
    private val imageDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
    private val files = imageDirectory.listFiles()
    private val context = context
    private val activity = context as CameraActivity
    private var imageArray = activity.imageArray

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        val view: View?
        val vh: ListRowHolder
        if (convertView == null) {
            view = this.mInflator.inflate(R.layout.grid_view_item, parent, false)
            view.setOnClickListener {
                var vh: ListRowHolder = it.tag as ListRowHolder
                val firstImageFile = vh.firstImageFile
                val secondImageFile = vh.secondImageFile

                val fragment: CameraActivity = context as CameraActivity
                fragment.openPreviewFragment(firstImageFile, secondImageFile)
            }
            vh = ListRowHolder(view)

            Glide.with(context)
                .load(imageArray[position][0])
                .placeholder(R.drawable.ic_baseline_downloading_24)
                .centerCrop()
                .into(vh.firstImage)
            vh.firstImage.visibility = View.VISIBLE
            vh.firstImageFile = imageArray[position][0]
            if (imageArray[position].size > 1){
                Glide.with(context)
                    .load(imageArray[position][1])
                    .placeholder(R.drawable.ic_baseline_downloading_24)
                    .centerCrop()
                    .into(vh.secondImage)
                vh.secondImage.visibility = View.VISIBLE
                vh.secondImageFile = imageArray[position][1]
                vh.secondImage.rotation = 5F
                vh.firstImage.rotation = -5F
            }
            view.tag = vh
        } else {
            view = convertView
            vh = view.tag as ListRowHolder
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
    public var firstImageFile: File? = null
    public var secondImageFile: File? = null
    public val firstImage: ImageView = row?.findViewById(R.id.grid_view_item_first_image) as ImageView
    public val secondImage: ImageView = row?.findViewById(R.id.grid_view_item_second_image) as ImageView
}
