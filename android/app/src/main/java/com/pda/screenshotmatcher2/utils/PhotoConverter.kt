package com.pda.screenshotmatcher2.utils

import android.graphics.*
import androidx.core.graphics.scale
import kotlin.math.max


/**
 * Rescales a [bitmap] to the specified [targetSize] and returns it
 */
fun rescale(bitmap: Bitmap, targetSize: Int): Bitmap {
    val longSide = max(bitmap.width, bitmap.height)
    val factor : Float = targetSize.toFloat() / longSide.toFloat()
    val greybmp : Bitmap = toGrayscale(bitmap)
    return greybmp.scale(width = (greybmp.width*factor).toInt(), height = (greybmp.height*factor).toInt())
}

/**
 * Converts a bitmap to grayscale and returns it
 *
 * @param bmpOriginal The bitmap to convert to grayscale
 * @return The converted grayscale bitmap
 */
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