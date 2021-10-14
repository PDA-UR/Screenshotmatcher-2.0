package com.pda.screenshotmatcher2.viewModels

import android.app.Application
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.*
import java.io.File

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val imageDirectory: File =
        application.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!

    private val images: MutableLiveData<ArrayList<ArrayList<File>>> by lazy {
        MutableLiveData<ArrayList<ArrayList<File>>>().also {
            loadImages(it)
        }
    }

    fun getImages(): LiveData<ArrayList<ArrayList<File>>> {
        return images
    }

    fun reloadImages() {
        // Log.d("VM", "Loading images")
        loadImages(images)
    }

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
    // Check if provided images are in live date, delete pair if so
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
    private fun fileBelongsToImageArrayItem(file: File, item: ArrayList<File>): Boolean {
        //Item has already 2 entries
        val filename: String = file.name.split("_".toRegex()).first()
        val itemName: String = item[0].name.split("_".toRegex()).first()

        if (item.size == 2) {
            return false
        }

        return filename == itemName
    }

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
