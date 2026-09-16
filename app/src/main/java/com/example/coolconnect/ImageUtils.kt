package com.example.coolconnect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageUtils {
    fun compressImage(context: Context, uri: Uri, maxWidth: Int = 800, maxHeight: Int = 800, quality: Int = 80): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // Calculate scaled dimensions
            val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
            var width = maxWidth
            var height = (width / ratio).toInt()

            if (height > maxHeight) {
                height = maxHeight
                width = (height * ratio).toInt()
            }

            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            
            val result = outputStream.toByteArray()
            
            // Clean up
            originalBitmap.recycle()
            scaledBitmap.recycle()
            outputStream.close()
            
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
