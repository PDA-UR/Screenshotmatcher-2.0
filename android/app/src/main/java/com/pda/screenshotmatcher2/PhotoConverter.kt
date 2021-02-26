package com.pda.screenshotmatcher2

import android.graphics.*
import android.media.Image
import android.os.Environment
import android.util.Log
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max

fun savePhotoToDisk(image: Image?, filepath: String?, targetSize: Int): File{
//    val bitmap = imageProxyToBitmap(image)
    val bitmap = BitmapFactory.decodeFile(filepath)
    Log.v("TEST", bitmap.height.toString())
    val longSide = max(bitmap.width, bitmap.height)
    val factor : Float = targetSize.toFloat() / longSide.toFloat()
    var greybmp : Bitmap = toGrayscale(bitmap)
    greybmp = greybmp.scale(width = (greybmp.width*factor).toInt(), height = (greybmp.height*factor).toInt())
    val filename = System.currentTimeMillis().toString()+".jpg"
    val file = File(Environment.getExternalStorageDirectory().toString()+"/Download/camdemo/", filename)
    try {
        val out = FileOutputStream(file)
        greybmp.compress(Bitmap.CompressFormat.JPEG, 100, out)
        out.flush()
        out.close()
        Log.v("TEST", "file saved")
    } catch (e: IOException){
        e.printStackTrace()
    }
    return file
}

private fun toGrayscale(bmpOriginal: Bitmap): Bitmap {
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

private fun imageProxyToBitmap(image: Image): Bitmap {
    val planeProxy = image.planes[0]
    val buffer: ByteBuffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}