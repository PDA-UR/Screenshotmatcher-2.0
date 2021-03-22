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
import java.io.File


class GridBaseAdapter(context: Context): BaseAdapter () {

    private val mInflator: LayoutInflater = LayoutInflater.from(context)
    private val imageDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
    private val files = imageDirectory.listFiles()
    private var imageArray: ArrayList<ArrayList<File>> = ArrayList()

    init {
        fillUpImageList()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        val view: View?
        val vh: ListRowHolder
        if (convertView == null) {
            view = this.mInflator.inflate(R.layout.grid_view_item, parent, false)
            vh = ListRowHolder(view)
            view.tag = vh
        } else {
            view = convertView
            vh = view.tag as ListRowHolder
        }

        val mFirstBitmap = BitmapFactory.decodeFile(imageArray[position][0].absolutePath)
        if (mFirstBitmap != null){
            vh.firstImage.setImageBitmap(mFirstBitmap)
        }
        val mSecondBitmap: Bitmap
        if (imageArray[position].size > 1){
            val mSecondBitmap = BitmapFactory.decodeFile(imageArray[position][1].absolutePath)
            if (mSecondBitmap != null){
                vh.secondImage.setImageBitmap(mSecondBitmap)
            }
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
}

private class ListRowHolder(row: View?) {
    public val firstImage: ImageView = row?.findViewById(R.id.grid_view_item_first_image) as ImageView
    public val secondImage: ImageView = row?.findViewById(R.id.grid_view_item_second_image) as ImageView
}
