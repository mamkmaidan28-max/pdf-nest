package com.pdfnest.tools.util

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.pdfnest.tools.model.OutputFileRef
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

object FileStorageHelper {
  fun getOutputsDir(context: Context): File {
    val dir =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        File(context.filesDir, "outputs")
      } else {
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "PDFNest") }
          ?: File(context.filesDir, "outputs")
      }
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  fun createJobTempDir(context: Context): File {
    val dir = File(context.cacheDir, "job_${System.currentTimeMillis()}_${UUID.randomUUID()}")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  fun deleteQuietly(file: File?) {
    if (file == null || !file.exists()) return
    try {
      if (file.isDirectory) {
        file.listFiles()?.forEach { deleteQuietly(it) }
      }
      file.delete()
    } catch (_: Throwable) {
    }
  }

  fun queryUriMetadata(context: Context, uri: Uri, fallbackPrefix: String): Pair<String, Long> {
    var displayName: String? = null
    var sizeBytes = -1L

    try {
      context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
          val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
        }
      }
    } catch (_: Throwable) {
    }

    if (sizeBytes <= 0L) {
      try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
          if (afd.length >= 0) sizeBytes = afd.length
        }
      } catch (_: Throwable) {
      }
    }

    val safeName = displayName?.takeIf { it.isNotBlank() } ?: "${fallbackPrefix}_${System.currentTimeMillis()}"
    return safeName to sizeBytes.coerceAtLeast(0L)
  }

  fun getFileProviderUri(context: Context, file: File): Uri {
    val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
    return FileProvider.getUriForFile(context, authority, file)
  }

  fun openFile(context: Context, filePath: String, mimeType: String): Result<Unit> {
    return try {
      val file = File(filePath)
      if (!file.exists()) {
        return Result.failure(IllegalStateException("File no longer exists on device."))
      }
      val uri = getFileProviderUri(context, file)
      val intent =
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, mimeType)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(Intent.createChooser(intent, "Open with").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      })
      Result.success(Unit)
    } catch (_: ActivityNotFoundException) {
      Result.failure(IllegalStateException("No app installed on this device can open this file type."))
    } catch (e: Throwable) {
      Result.failure(IllegalStateException(e.message ?: "Unable to open file."))
    }
  }

  fun shareFiles(context: Context, files: List<OutputFileRef>): Result<Unit> {
    return try {
      val existing = files.mapNotNull { ref -> File(ref.filePath).takeIf { it.exists() }?.let { it to ref.mimeType } }
      if (existing.isEmpty()) {
        return Result.failure(IllegalStateException("File no longer exists on device."))
      }

      val intent =
        if (existing.size == 1) {
          val (file, mime) = existing.first()
          val uri = getFileProviderUri(context, file)
          Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        } else {
          val uris = ArrayList(existing.map { (file, _) -> getFileProviderUri(context, file) })
          val commonMime =
            if (existing.all { it.second.startsWith("image/") }) "image/*"
            else if (existing.all { it.second == "application/pdf" }) "application/pdf"
            else "*/*"
          Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = commonMime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        }

      val chooser =
        Intent.createChooser(intent, "Share via").apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(chooser)
      Result.success(Unit)
    } catch (e: Throwable) {
      Result.failure(IllegalStateException(e.message ?: "Unable to share file."))
    }
  }

  /**
   * Saves one or more output files to Downloads/PDFNest using MediaStore on Android 10+
   * (zero storage permissions needed), or app-scoped external Downloads/PDFNest on Android 9 and below.
   */
  fun saveToDownloads(context: Context, files: List<OutputFileRef>): Result<String> {
    return try {
      val validFiles = files.filter { File(it.filePath).exists() }
      if (validFiles.isEmpty()) {
        return Result.failure(IllegalStateException("Output file is missing."))
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        validFiles.forEach { ref ->
          val src = File(ref.filePath)
          val values =
            ContentValues().apply {
              put(MediaStore.Downloads.DISPLAY_NAME, ref.fileName)
              put(MediaStore.Downloads.MIME_TYPE, ref.mimeType)
              put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PDFNest")
              put(MediaStore.Downloads.IS_PENDING, 1)
            }
          val destUri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
              ?: throw IllegalStateException("Could not create destination in Downloads.")
          try {
            resolver.openOutputStream(destUri)?.use { out ->
              FileInputStream(src).use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Could not write to Downloads.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(destUri, values, null, null)
          } catch (t: Throwable) {
            resolver.delete(destUri, null, null)
            throw t
          }
        }
        val countLabel = if (validFiles.size == 1) validFiles.first().fileName else "${validFiles.size} files"
        Result.success("Saved $countLabel to Downloads/PDFNest")
      } else {
        val targetDir =
          context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "PDFNest") }
            ?: File(context.filesDir, "outputs")
        if (!targetDir.exists()) targetDir.mkdirs()

        validFiles.forEach { ref ->
          val src = File(ref.filePath)
          val dest = File(targetDir, ref.fileName)
          if (src.canonicalPath != dest.canonicalPath) {
            FileInputStream(src).use { input ->
              FileOutputStream(dest).use { out -> input.copyTo(out) }
            }
          }
        }
        val countLabel = if (validFiles.size == 1) validFiles.first().fileName else "${validFiles.size} files"
        Result.success("Saved $countLabel to PDFNest folder")
      }
    } catch (e: Throwable) {
      Result.failure(IllegalStateException(e.message ?: "Could not save to Downloads."))
    }
  }
}
