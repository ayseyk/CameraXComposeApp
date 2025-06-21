package com.ays.cameraxcomposeapp.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.ays.cameraxcomposeapp.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService

private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    outputDirectory: File,
    executor: ExecutorService,
    onPhotoCaptured: (Uri) -> Unit
) {
    val photoFile = File(
        outputDirectory,
        SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onError(exc: ImageCaptureException) {
            ContextCompat.getMainExecutor(context).execute {
                Toast.makeText(
                    context,
                    "Fotoğraf çekme başarısız: ${exc.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            val savedUri = Uri.fromFile(photoFile)
            saveImageToGallery(context, photoFile)
            onPhotoCaptured(savedUri)
        }
    })
}

fun getOutputDirectory(context: Context): File {
    val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
        File(it, context.resources.getString(R.string.app_name)).apply { mkdirs() }
    }
    return if (mediaDir != null && mediaDir.exists())
        mediaDir else context.filesDir
}


fun saveImageToGallery(context: Context, imageFile: File) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/${context.getString(R.string.app_name)}")
    }

    val resolver = context.contentResolver
    var uri: Uri? = null
    try {
        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let { imageUri ->
            resolver.openOutputStream(imageUri)?.use { outputStream ->
                imageFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            ContextCompat.getMainExecutor(context).execute {
                Toast.makeText(
                    context,
                    "Fotoğraf galeriye kaydedildi: $imageUri",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } ?: run {
            ContextCompat.getMainExecutor(context).execute {
                Toast.makeText(context, "Fotoğraf galeriye kaydedilemedi.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    } catch (e: Exception) {
        ContextCompat.getMainExecutor(context).execute {
            Toast.makeText(
                context,
                "Galeriye kaydederken hata oluştu: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
        if (uri != null) {
            resolver.delete(uri, null, null)
        }
    }
}