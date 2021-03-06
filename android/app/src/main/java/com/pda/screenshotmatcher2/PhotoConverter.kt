package com.pda.screenshotmatcher2

import android.content.Context
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import java.io.*
import java.nio.ByteBuffer
import kotlin.math.max

fun rescale(bitmap: Bitmap, targetSize: Int): Bitmap {
    val longSide = max(bitmap.width, bitmap.height)
    val factor : Float = targetSize.toFloat() / longSide.toFloat()
    val greybmp : Bitmap = toGrayscale(bitmap)
    return greybmp.scale(width = (greybmp.width*factor).toInt(), height = (greybmp.height*factor).toInt())
}

fun toGrayscale(bmpOriginal: Bitmap): Bitmap {
    val height: Int = bmpOriginal.height
    val width: Int = bmpOriginal.width
    val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    val c = Canvas(bmpGrayscale)
    val paint = Paint()
    val cm = ColorMatrix()
    cm.setSaturation(0f)
    val f = ColorMatrixColorFilter(cm)
    paint.colorFilter = f
    c.drawBitmap(bmpOriginal, 0f, 0f, paint)
    return bmpGrayscale
}

fun b64ToBitmap(b64String : String) : Bitmap{
    val byteArray = Base64.decode(b64String, Base64.DEFAULT)
    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    return bitmap
}