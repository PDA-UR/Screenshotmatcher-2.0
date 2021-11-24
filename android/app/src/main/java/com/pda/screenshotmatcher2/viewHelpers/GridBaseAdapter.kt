package com.pda.screenshotmatcher2.viewHelpers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.viewModels.GalleryViewModel
import com.pda.screenshotmatcher2.views.activities.CameraActivity
import com.pda.screenshotmatcher2.views.fragments.rotationFragments.GalleryFragment
import java.io.File

/**
 * A [BaseAdapter] used by [GalleryFragment] to inflate [Views][View] of previously taken screenshot pairs (cropped,full).
 *
 * @param context Instance of [GalleryFragment], in which the inflated views will be used
 *
 * @property imagePairs Local variable that stores all image pairs that will be displayed in the gallery
 * @property gfvm [GalleryViewModel], supplies and updates [imagePairs] with image pairs
 */
class GridBaseAdapter(private val context: GalleryFragment) : BaseAdapter() {
    private var imagePairs: ArrayList<ArrayList<File>> = ArrayList()
    private val gfvm = ViewModelProvider(
        context.requireActivity(),
        GalleryViewModel.Factory(context.requireActivity().application)
    ).get(
        GalleryViewModel::class.java
    ).apply {
        getImages().observe(context.viewLifecycleOwner, Observer { images ->
            imagePairs = images
        })
    }

    /**
     * Returns an inflated [View] for an image pair.
     *
	 * @param position The position of the image pair in [imagePairs]
	 * @return Inflated [View] with the layout [R.layout.grid_view_item]
	 */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val mInflator: LayoutInflater = LayoutInflater.from(context.activity)
        val view: View = mInflator.inflate(R.layout.grid_view_item, parent, false)
        val vh = ListRowHolder(view)
        view.tag = vh

        view.setOnClickListener {
            val firstImageFile = vh.firstImageFile
            val secondImageFile = vh.secondImageFile
            val ca: CameraActivity = context.activity as CameraActivity
            ca.cameraActivityFragmentHandler.openGalleryPreviewFragment(
                firstImageFile,
                secondImageFile
            )
        }

        Glide.with(context.requireActivity())
            .load(imagePairs[position][0])
            .placeholder(R.drawable.fragment_normal_button_small)
            .centerCrop()
            .into(vh.firstImage)
        vh.firstImage.visibility = View.VISIBLE
        vh.firstImageFile = imagePairs[position][0]
        if (imagePairs[position].size > 1) {
            Glide.with(context.requireActivity())
                .load(imagePairs[position][1])
                .placeholder(R.drawable.fragment_normal_button_small)
                .centerCrop()
                .into(vh.secondImage)
            vh.secondImage.visibility = View.VISIBLE
            vh.secondImageFile = imagePairs[position][1]
            vh.secondImage.rotation = 5F
            vh.firstImage.rotation = -5F
        }
        return view
    }

    /**
     * Returns an image pair stored in [imagePairs] at the specified [position].
	 */
    override fun getItem(position: Int): Any {
        return imagePairs[position]
    }

    /**
     * Returns the ID of an item at the specified [position].
	 */
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    /**
     * Returns the total count of image pairs in [imagePairs].
	 */
    override fun getCount(): Int {
        return imagePairs.size
    }
}

/**
 * Helper class for [GridBaseAdapter] that stores the files and views of an image pair.
 *
 * @param row The composite [View] of the image pair (consists of two [ImageViews][ImageView])
 *
 * @property firstImageFile The file of the first image
 * @property secondImageFile The file of the second image
 * @property firstImage The [ImageView] of the first image
 * @property secondImage The [ImageView] of the second image
 */
private class ListRowHolder(row: View?) {
    var firstImageFile: File? = null
    var secondImageFile: File? = null
    val firstImage: ImageView = row?.findViewById(R.id.grid_view_item_first_image) as ImageView
    val secondImage: ImageView = row?.findViewById(R.id.grid_view_item_second_image) as ImageView
}
