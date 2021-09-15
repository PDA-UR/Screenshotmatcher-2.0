package com.pda.screenshotmatcher2.viewModels.galleryViewModel

import android.app.Application
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.File

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val imageDirectory: File =
        application.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!

    private var files: Array<File>? = imageDirectory.listFiles()

    private val images: MutableLiveData<ArrayList<ArrayList<File>>> by lazy {
        MutableLiveData<ArrayList<ArrayList<File>>>().also {
            loadImages(it)
        }
    }

    fun getImages(): LiveData<ArrayList<ArrayList<File>>> {
        return images
    }

    private fun loadImages(images: MutableLiveData<ArrayList<ArrayList<File>>>) {
        Handler(Looper.getMainLooper()).post {
            val imageArray: ArrayList<ArrayList<File>> = ArrayList()
            files?.forEachIndexed outer@{ index, file ->
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

    private fun fileBelongsToImageArrayItem(file: File, item: ArrayList<File>): Boolean {
        //Item has already 2 entries
        val filename: String = file.name.split("_".toRegex()).first()
        val itemName: String = item[0].name.split("_".toRegex()).first()

        if (item.size == 2) {
            return false
        }

        return filename == itemName
    }
}