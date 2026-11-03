package com.example.coolconnect

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

object ImageUtils {

    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }

    /**
     * Validates that the Uri points to a valid local image file and does not exceed max size (default 10 MB).
     */
    fun validateImageUri(
        context: Context,
        uri: Uri,
        maxSizeBytes: Long = 10 * 1024 * 1024 // 10 MB
    ): ValidationResult {
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            if (mimeType.isNotEmpty() && !mimeType.startsWith("image/")) {
                return ValidationResult.Error("Invalid file type ($mimeType). Only image files (JPEG, PNG, WEBP) are allowed.")
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (options.outMimeType != null && !options.outMimeType.startsWith("image/")) {
                return ValidationResult.Error("Invalid image type (${options.outMimeType}). Please select a valid photo.")
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return ValidationResult.Error("Unable to decode image dimensions. File may be corrupted or not a valid image.")
            }

            val fileSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length
            } ?: -1L

            if (fileSize > maxSizeBytes) {
                val sizeMb = String.format(Locale.US, "%.1f", fileSize / (1024.0 * 1024.0))
                return ValidationResult.Error("File size ($sizeMb MB) exceeds maximum allowed size of 10 MB.")
            }

            ValidationResult.Success
        } catch (e: Exception) {
            ValidationResult.Error("Failed to validate image: ${e.localizedMessage}")
        }
    }

    /**
     * Saves compressed image bytes into internal app private storage (`files/folderName/fileName`).
     * Returns a permanent local file Uri string ("file:///data/user/0/.../fileName") that persists across app restarts.
     */
    fun saveImageToInternalStorage(
        context: Context,
        bytes: ByteArray,
        folderName: String,
        fileName: String
    ): String? {
        return try {
            val folder = File(context.filesDir, folderName)
            if (!folder.exists()) {
                folder.mkdirs()
            }
            val file = File(folder, fileName)
            FileOutputStream(file).use { outStream ->
                outStream.write(bytes)
                outStream.flush()
            }
            if (file.exists() && file.length() > 0) {
                Uri.fromFile(file).toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error saving image to internal storage", e)
            null
        }
    }

    /**
     * Saves compressed image bytes into local device MediaStore gallery with MIME type image/jpeg
     * and verifies exact byte count to prevent truncation/corruption.
     */
    fun saveImageToGallery(
        context: Context,
        bytes: ByteArray,
        fileName: String = "CoolConnect_${System.currentTimeMillis()}.jpg"
    ): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CoolConnect")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null

            resolver.openOutputStream(imageUri)?.use { outputStream ->
                outputStream.write(bytes)
                outputStream.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            val writtenDescriptor = resolver.openAssetFileDescriptor(imageUri, "r")
            val writtenLength = writtenDescriptor?.use { it.length } ?: 0L
            if (writtenLength != bytes.size.toLong() && writtenLength > 0) {
                Log.w("ImageUtils", "Saved image size mismatch: original ${bytes.size}, written $writtenLength")
            }

            imageUri
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error saving image to gallery", e)
            null
        }
    }

    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "CoolConnect_${System.currentTimeMillis()}.jpg"
    ): Uri? {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val bytes = outputStream.toByteArray()
        val uri = saveImageToGallery(context, bytes, fileName)
        try {
            outputStream.close()
        } catch (_: Exception) {}
        return uri
    }

    fun compressImage(
        context: Context,
        uri: Uri,
        maxWidth: Int = 800,
        maxHeight: Int = 800,
        quality: Int = 80
    ): ByteArray? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            var inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false

            inputStream = context.contentResolver.openInputStream(uri)
            var decodedBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            if (decodedBitmap == null) return null

            decodedBitmap = handleExifRotation(context, uri, decodedBitmap)

            val scaledBitmap = scaleBitmapIfNeeded(decodedBitmap, maxWidth, maxHeight)

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val result = outputStream.toByteArray()

            if (scaledBitmap != decodedBitmap) {
                scaledBitmap.recycle()
            }
            decodedBitmap.recycle()
            outputStream.close()

            result
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    fun compressBitmap(
        bitmap: Bitmap,
        maxWidth: Int = 800,
        maxHeight: Int = 800,
        quality: Int = 80
    ): ByteArray? {
        return try {
            val scaledBitmap = scaleBitmapIfNeeded(bitmap, maxWidth, maxHeight)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val result = outputStream.toByteArray()

            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            outputStream.close()

            result
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun handleExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (t: Throwable) {
            bitmap
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        var width = maxWidth
        var height = (width / ratio).toInt()

        if (height > maxHeight) {
            height = maxHeight
            width = (height * ratio).toInt()
        }

        if (width >= bitmap.width || height >= bitmap.height) {
            return bitmap
        }

        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
