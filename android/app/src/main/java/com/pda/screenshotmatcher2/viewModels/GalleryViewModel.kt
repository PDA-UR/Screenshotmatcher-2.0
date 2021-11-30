package com.pda.screenshotmatcher2.viewModels

import android.app.Application
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.*
import java.io.File

/**
 * [ViewModel] that provides two way data bindings for images stored in the internal gallery.
 *
 * @see [MVVM Architecture](https://developer.android.com/jetpack/guide) For more information about how this software architectural pattern works.
 *
 * @constructor An instance of the current [Application]
 *
 * @property imageDirectory Directory of the internal gallery
 * @property images Data binding for the images stored in the internal gallery
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val imageDirectory: File =
        application.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!

    private val images: MutableLiveData<ArrayList<ArrayList<File>>> by lazy {
        MutableLiveData<ArrayList<ArrayList<File>>>().also {
            loadImages(it)
        }
    }

    /**
     * Getter method that returns [images] as [MutableLiveData]
	 * @return
	 */
    fun getImages(): LiveData<ArrayList<ArrayList<File>>> {
        return images
    }

    /**
     * Calls [loadImages] to refresh [images]
     */
    fun reloadImages() {
        loadImages(images)
    }

    /**
     * Updates [images] to be the same as the images in the external file directory
	 */
    private fun loadImages(images: MutableLiveData<ArrayList<ArrayList<File>>>) {
        val files: Array<File> = imageDirectory.listFiles() ?: emptyArray()
        Handler(Looper.getMainLooper()).post {
            val imageArray: ArrayList<ArrayList<File>> = ArrayList()
            files.forEachIndexed outer@{ index, file ->
                if (index != 0) {
                    imageArray.forEach inner@{ item ->
                        if (fileBelongsToImageArrayItem(file, item)) {
                            if (file.name.split("_").last() == "Cropped.png") {
                                item.add(file)
                            } else {
                                item.add(0, file)
                            }
                            return@outer
                        }
                    }
                }
                val fileCouple: ArrayList<File> = ArrayList()
                fileCouple.add(file)
                imageArray.add(fileCouple)
            }
            images.setValue(imageArray)
        }
    }

    /**
     * Deletes an image pair from [images] and the external file directory.
     *
	 * @param imagesToDelete The image pair to remove
	 */
    fun deleteImagePair(imagesToDelete: ArrayList<File>) {
        val newList: ArrayList<ArrayList<File>> = if(images.value != null) images.value!! else ArrayList()
        for (imagePair in newList) {
            var didRemove = false
            for (image in imagesToDelete) {
                if (image in imagePair) {
                    imagePair.forEach { imageFile ->
                        if(imageFile.exists()) imageFile.delete()
                    }
                    newList.remove(imagePair)
                    didRemove = true
                    break
                }
            }
            if (didRemove) break
        }
        images.value = newList
    }

    /**
     * Helper function to check whether a [file] is in an [item]
	 */
    private fun fileBelongsToImageArrayItem(file: File, item: ArrayList<File>): Boolean {
        //Item has already 2 entries
        val filename: String = file.name.split("_".toRegex()).first()
        val itemName: String = item[0].name.split("_".toRegex()).first()

        if (item.size == 2) {
            return false
        }

        return filename == itemName
    }

    /**
     * Factory to initiate this [GalleryViewModel]
     *
     * @constructor An instance of the current [Application]
     */
    internal class Factory (private val application: Application) : ViewModelProvider.Factory {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
                return GalleryViewModel(
                    application
                ) as T
            }

            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
