package com.pda.screenshotmatcher2.utils

import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class PhotoProvider : FileProvider() {
    val CONTENT_PROVIDER_AUTHORITY = "com.pda.screenshotmatcher2.fileprovider"

    fun getPhotoUri(file: File?): Uri? {
        val outputUri = Uri.fromFile(file)
        val builder = Uri.Builder()
            .authority(CONTENT_PROVIDER_AUTHORITY)
            .scheme("file")
            .path(outputUri.path)
            .query(outputUri.query)
            .fragment(outputUri.fragment)
        return builder.build()
    }
}