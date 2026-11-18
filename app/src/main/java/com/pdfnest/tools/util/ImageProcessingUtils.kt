package com.pdfnest.tools.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageProcessingUtils {
  /**
   * Decodes an image URI with bounds-based downsampling and applies EXIF orientation correction.
   * Retries at higher sample size if memory is constrained.
   */
  fun decodeSampledOrientedBitmap(
    context: Context,
    uri: Uri,
    maxDimension: Int,
  ): Bitmap {
    val resolver = context.contentResolver

    // 1. Read EXIF orientation
    val orientation =
      try {
        resolver.openInputStream(uri)?.use { stream ->
          ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
          )
        } ?: ExifInterface.ORIENTATION_NORMAL
      } catch (_: Throwable) {
        ExifInterface.ORIENTATION_NORMAL
      }

    // 2. Inspect image bounds without allocating pixel memory
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, boundsOpts)
    } ?: throw PdfNestException("Could not open the selected image.")

    if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
      throw PdfNestException("Selected file is not a valid or supported image.")
    }

    val initialSampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDimension)
    var decoded: Bitmap? = null
    var sampleSize = initialSampleSize
    var attempt = 0

    while (attempt < 3 && decoded == null) {
      val decodeOpts =
        BitmapFactory.Options().apply {
          inSampleSize = sampleSize
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
      try {
        decoded =
          resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
          }
      } catch (_: OutOfMemoryError) {
        System.gc()
        sampleSize *= 2
      }
      attempt++
    }

    val rawBitmap = decoded ?: throw PdfNestException("Image is too large to process on this device.")

    // 3. Apply exact max-dimension scale + EXIF orientation transform
    val oriented = applyExifAndScale(rawBitmap, orientation, maxDimension)
    if (oriented !== rawBitmap) {
      rawBitmap.recycle()
    }
    return oriented
  }

  /**
   * Saves a bitmap as an opaque JPEG (flattening any alpha channel onto white).
   */
  fun saveBitmapAsJpeg(bitmap: Bitmap, destFile: File, quality: Int) {
    val exportBitmap =
      if (bitmap.hasAlpha()) {
        Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { whiteBg ->
          val canvas = Canvas(whiteBg)
          canvas.drawColor(Color.WHITE)
          canvas.drawBitmap(bitmap, 0f, 0f, null)
        }
      } else {
        bitmap
      }

    try {
      FileOutputStream(destFile).use { out ->
        val ok = exportBitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), out)
        if (!ok) throw PdfNestException("Failed to encode JPEG image.")
      }
    } finally {
      if (exportBitmap !== bitmap) {
        exportBitmap.recycle()
      }
    }
  }

  private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
    var inSampleSize = 1
    val largest = max(width, height)
    if (largest > maxDim) {
      while ((largest / inSampleSize) > maxDim * 2) {
        inSampleSize *= 2
      }
    }
    return inSampleSize
  }

  private fun applyExifAndScale(source: Bitmap, orientation: Int, maxDimension: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(270f)
        matrix.postScale(-1f, 1f)
      }
    }

    val rotated =
      if (!matrix.isIdentity) {
        try {
          Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } catch (_: OutOfMemoryError) {
          source
        }
      } else {
        source
      }

    val maxSide = max(rotated.width, rotated.height)
    if (maxSide <= maxDimension) {
      return rotated
    }

    val ratio = maxDimension.toFloat() / maxSide.toFloat()
    val targetW = (rotated.width * ratio).roundToInt().coerceAtLeast(1)
    val targetH = (rotated.height * ratio).roundToInt().coerceAtLeast(1)
    val scaled =
      try {
        Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
      } catch (_: OutOfMemoryError) {
        rotated
      }
    if (scaled !== rotated && rotated !== source) {
      rotated.recycle()
    }
    return scaled
  }
}
