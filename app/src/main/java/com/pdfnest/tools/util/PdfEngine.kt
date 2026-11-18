package com.pdfnest.tools.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.pdfnest.tools.model.CompressedImageSummary
import com.pdfnest.tools.model.CompressionLevel
import com.pdfnest.tools.model.OutputFileRef
import com.pdfnest.tools.model.PdfMargin
import com.pdfnest.tools.model.PdfPageSize
import com.pdfnest.tools.model.PickedImageItem
import com.pdfnest.tools.model.PickedPdfItem
import com.pdfnest.tools.model.ToolResult
import com.pdfnest.tools.model.ToolType
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PdfNestException(message: String) : Exception(message)

object PdfEngine {

  suspend fun compressImages(
    context: Context,
    items: List<PickedImageItem>,
    level: CompressionLevel,
    onProgress: (current: Int, total: Int, message: String) -> Unit,
  ): ToolResult =
    withContext(Dispatchers.IO) {
      if (items.isEmpty()) throw PdfNestException("Select at least one image first.")

      val outputsDir = FileStorageHelper.getOutputsDir(context)
      val tempDir = FileStorageHelper.createJobTempDir(context)
      val ts = FormatUtils.timestampTag()
      val summaries = mutableListOf<CompressedImageSummary>()
      val createdFiles = mutableListOf<File>()

      try {
        items.forEachIndexed { index, item ->
          coroutineContext.ensureActive()
          onProgress(index, items.size, "Compressing ${item.name} (${index + 1}/${items.size})...")

          val baseName = FormatUtils.sanitizeBaseName(item.name, "image_${index + 1}")
          val outFile = File(outputsDir, "${baseName}_compressed_${ts}_${index + 1}.jpg")

          var bitmap: Bitmap? = null
          try {
            bitmap =
              ImageProcessingUtils.decodeSampledOrientedBitmap(
                context = context,
                uri = item.uri,
                maxDimension = level.imageMaxDimension,
              )
            ImageProcessingUtils.saveBitmapAsJpeg(bitmap, outFile, level.jpegQuality)
          } finally {
            bitmap?.recycle()
          }

          createdFiles.add(outFile)
          val originalBytes =
            if (item.sizeBytes > 0L) item.sizeBytes
            else measureUriSizeFallback(context, item.uri)
          val newBytes = outFile.length()

          summaries.add(
            CompressedImageSummary(
              fileName = outFile.name,
              outputPath = outFile.absolutePath,
              mimeType = "image/jpeg",
              originalBytes = originalBytes,
              compressedBytes = newBytes,
            )
          )
          onProgress(index + 1, items.size, "Compressed ${index + 1} of ${items.size}")
        }

        val allRefs =
          summaries.map {
            OutputFileRef(
              fileName = it.fileName,
              filePath = it.outputPath,
              mimeType = it.mimeType,
              sizeBytes = it.compressedBytes,
            )
          }
        val primary = allRefs.first()

        ToolResult(
          id = UUID.randomUUID().toString(),
          toolTypeName = ToolType.IMAGE_COMPRESSOR.displayName,
          primaryFilePath = primary.filePath,
          primaryFileName = primary.fileName,
          primaryMimeType = primary.mimeType,
          primarySizeBytes = primary.sizeBytes,
          originalTotalBytes = summaries.sumOf { it.originalBytes },
          outputTotalBytes = summaries.sumOf { it.compressedBytes },
          allOutputFiles = allRefs,
          imageSummaries = summaries,
        )
      } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) {
          createdFiles.forEach { FileStorageHelper.deleteQuietly(it) }
          throw t
        }
        createdFiles.forEach { FileStorageHelper.deleteQuietly(it) }
        throw mapFriendlyError(t, "Could not compress one or more images.")
      } finally {
        FileStorageHelper.deleteQuietly(tempDir)
      }
    }

  suspend fun imagesToPdf(
    context: Context,
    items: List<PickedImageItem>,
    pageSize: PdfPageSize,
    margin: PdfMargin,
    onProgress: (current: Int, total: Int, message: String) -> Unit,
  ): ToolResult =
    withContext(Dispatchers.IO) {
      if (items.isEmpty()) throw PdfNestException("Select at least one image first.")

      val outputsDir = FileStorageHelper.getOutputsDir(context)
      val tempDir = FileStorageHelper.createJobTempDir(context)
      val ts = FormatUtils.timestampTag()
      val outFile = File(outputsDir, "PDFNest_Images_$ts.pdf")
      val tempOutFile = File(tempDir, "building.pdf")
      val pdfDocument = PdfDocument()
      val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

      try {
        var totalInputBytes = 0L

        items.forEachIndexed { index, item ->
          coroutineContext.ensureActive()
          onProgress(index, items.size, "Adding image ${index + 1} of ${items.size}...")
          totalInputBytes +=
            if (item.sizeBytes > 0L) item.sizeBytes else measureUriSizeFallback(context, item.uri)

          var bitmap: Bitmap? = null
          try {
            bitmap =
              ImageProcessingUtils.decodeSampledOrientedBitmap(
                context = context,
                uri = item.uri,
                maxDimension = 2048,
              )

            val (pageW, pageH) = computePdfPageDimensions(bitmap.width, bitmap.height, pageSize)
            val marginPt =
              if (pageSize == PdfPageSize.FIT_TO_IMAGE) {
                min(margin.marginPt, min(pageW, pageH) * 0.1f)
              } else {
                margin.marginPt
              }

            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val availW = (pageW - marginPt * 2f).coerceAtLeast(10f)
            val availH = (pageH - marginPt * 2f).coerceAtLeast(10f)
            val scale = min(availW / bitmap.width.toFloat(), availH / bitmap.height.toFloat())
            val drawW = bitmap.width * scale
            val drawH = bitmap.height * scale
            val left = (pageW - drawW) / 2f
            val top = (pageH - drawH) / 2f

            val destRect = RectF(left, top, left + drawW, top + drawH)
            canvas.drawBitmap(bitmap, null, destRect, paint)
            pdfDocument.finishPage(page)
          } finally {
            bitmap?.recycle()
          }

          onProgress(index + 1, items.size, "Added image ${index + 1} of ${items.size}")
        }

        coroutineContext.ensureActive()
        FileOutputStream(tempOutFile).use { out -> pdfDocument.writeTo(out) }
        tempOutFile.copyTo(outFile, overwrite = true)

        val outBytes = outFile.length()
        val ref = OutputFileRef(outFile.name, outFile.absolutePath, "application/pdf", outBytes)

        ToolResult(
          id = UUID.randomUUID().toString(),
          toolTypeName = ToolType.JPG_TO_PDF.displayName,
          primaryFilePath = outFile.absolutePath,
          primaryFileName = outFile.name,
          primaryMimeType = "application/pdf",
          primarySizeBytes = outBytes,
          originalTotalBytes = totalInputBytes,
          outputTotalBytes = outBytes,
          allOutputFiles = listOf(ref),
          pageCount = items.size,
        )
      } catch (t: Throwable) {
        FileStorageHelper.deleteQuietly(outFile)
        if (t is kotlinx.coroutines.CancellationException) throw t
        throw mapFriendlyError(t, "Could not create PDF from the selected images.")
      } finally {
        try {
          pdfDocument.close()
        } catch (_: Throwable) {
        }
        FileStorageHelper.deleteQuietly(tempDir)
      }
    }

  suspend fun compressPdf(
    context: Context,
    pickedPdf: PickedPdfItem,
    level: CompressionLevel,
    onProgress: (current: Int, total: Int, message: String) -> Unit,
  ): ToolResult =
    withContext(Dispatchers.IO) {
      val outputsDir = FileStorageHelper.getOutputsDir(context)
      val tempDir = FileStorageHelper.createJobTempDir(context)
      val ts = FormatUtils.timestampTag()
      val baseName = FormatUtils.sanitizeBaseName(pickedPdf.name, "document")
      val tempInputPdf = File(tempDir, "input.pdf")
      val tempCompressedPdf = File(tempDir, "compressed.pdf")
      val finalOutFile = File(outputsDir, "${baseName}_compressed_$ts.pdf")

      var pfd: ParcelFileDescriptor? = null
      var renderer: PdfRenderer? = null
      val pdfDocument = PdfDocument()
      val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

      try {
        onProgress(0, 1, "Reading PDF...")
        copyUriToFile(context, pickedPdf.uri, tempInputPdf)
        val originalSize = tempInputPdf.length()
        if (originalSize <= 0L) throw PdfNestException("Selected PDF file is empty.")

        pfd = ParcelFileDescriptor.open(tempInputPdf, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer =
          try {
            PdfRenderer(pfd)
          } catch (sec: SecurityException) {
            throw PdfNestException("This PDF is password protected")
          } catch (t: Throwable) {
            throw PdfNestException("Could not read PDF. The file may be corrupted or unsupported.")
          }

        val pageCount = renderer.pageCount
        if (pageCount <= 0) throw PdfNestException("This PDF has no pages.")

        for (i in 0 until pageCount) {
          coroutineContext.ensureActive()
          onProgress(i, pageCount, "Compressing page ${i + 1} of $pageCount...")

          val page = renderer.openPage(i)
          val pageW = page.width.coerceAtLeast(1)
          val pageH = page.height.coerceAtLeast(1)

          // Calculate target render pixel dimensions capped to avoid OOM on giant vector sheets
          val rawW = (pageW * level.pdfRenderScale).roundToInt().coerceAtLeast(1)
          val rawH = (pageH * level.pdfRenderScale).roundToInt().coerceAtLeast(1)
          val maxRenderPx = 2200
          val capScale =
            if (max(rawW, rawH) > maxRenderPx) maxRenderPx.toFloat() / max(rawW, rawH).toFloat()
            else 1f
          val renderW = (rawW * capScale).roundToInt().coerceAtLeast(1)
          val renderH = (rawH * capScale).roundToInt().coerceAtLeast(1)

          var renderedBitmap: Bitmap? = null
          var jpegBitmap: Bitmap? = null

          try {
            renderedBitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
            renderedBitmap.eraseColor(Color.WHITE)
            page.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
          } catch (_: OutOfMemoryError) {
            renderedBitmap?.recycle()
            renderedBitmap = null
            throw PdfNestException("Page ${i + 1} is too large to process on this device.")
          } finally {
            page.close()
          }

          val jpegBytes =
            try {
              ByteArrayOutputStream().use { baos ->
                renderedBitmap!!.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, baos)
                baos.toByteArray()
              }
            } finally {
              renderedBitmap?.recycle()
              renderedBitmap = null
            }

          try {
            jpegBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
              ?: throw PdfNestException("Failed to re-encode page ${i + 1}.")

            val outPageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
            val outPage = pdfDocument.startPage(outPageInfo)
            outPage.canvas.drawColor(Color.WHITE)
            outPage.canvas.drawBitmap(jpegBitmap, null, Rect(0, 0, pageW, pageH), paint)
            pdfDocument.finishPage(outPage)
          } finally {
            jpegBitmap?.recycle()
            jpegBitmap = null
          }

          onProgress(i + 1, pageCount, "Processed page ${i + 1} of $pageCount")
        }

        coroutineContext.ensureActive()
        FileOutputStream(tempCompressedPdf).use { out -> pdfDocument.writeTo(out) }

        val compressedSize = tempCompressedPdf.length()
        val keptOriginal = compressedSize >= originalSize
        val resultFile =
          if (keptOriginal) {
            val keptFile = File(outputsDir, "${baseName}_original_$ts.pdf")
            tempInputPdf.copyTo(keptFile, overwrite = true)
            keptFile
          } else {
            tempCompressedPdf.copyTo(finalOutFile, overwrite = true)
            finalOutFile
          }

        val finalSize = resultFile.length()
        val ref = OutputFileRef(resultFile.name, resultFile.absolutePath, "application/pdf", finalSize)

        ToolResult(
          id = UUID.randomUUID().toString(),
          toolTypeName = ToolType.PDF_COMPRESSOR.displayName,
          primaryFilePath = resultFile.absolutePath,
          primaryFileName = resultFile.name,
          primaryMimeType = "application/pdf",
          primarySizeBytes = finalSize,
          originalTotalBytes = originalSize,
          outputTotalBytes = finalSize,
          allOutputFiles = listOf(ref),
          keptOriginalPdf = keptOriginal,
          attemptedCompressedBytes = compressedSize,
          pageCount = pageCount,
        )
      } catch (t: Throwable) {
        FileStorageHelper.deleteQuietly(finalOutFile)
        if (t is kotlinx.coroutines.CancellationException) throw t
        throw mapFriendlyError(t, "Could not compress this PDF.")
      } finally {
        try {
          pdfDocument.close()
        } catch (_: Throwable) {
        }
        try {
          renderer?.close()
        } catch (_: Throwable) {
        }
        try {
          pfd?.close()
        } catch (_: Throwable) {
        }
        FileStorageHelper.deleteQuietly(tempDir)
      }
    }

  suspend fun inspectPdf(context: Context, uri: Uri): PickedPdfItem =
    withContext(Dispatchers.IO) {
      val (name, reportedSize) = FileStorageHelper.queryUriMetadata(context, uri, "document")
      val tempDir = FileStorageHelper.createJobTempDir(context)
      val tempFile = File(tempDir, "inspect.pdf")
      var doc: PDDocument? = null
      try {
        copyUriToFile(context, uri, tempFile)
        val actualSize = if (reportedSize > 0L) reportedSize else tempFile.length()
        if (actualSize <= 0L) {
          throw PdfNestException("Selected PDF file is empty.")
        }
        doc =
          try {
            PDDocument.load(tempFile, MemoryUsageSetting.setupTempFileOnly())
          } catch (_: InvalidPasswordException) {
            throw PdfNestException("This PDF is password protected")
          } catch (_: Throwable) {
            throw PdfNestException("Could not read PDF. The file may be corrupted or unsupported.")
          }

        if (doc.isEncrypted) {
          throw PdfNestException("This PDF is password protected")
        }
        val pages = doc.numberOfPages
        if (pages <= 0) {
          throw PdfNestException("This PDF has no pages.")
        }

        PickedPdfItem(
          id = UUID.randomUUID().toString(),
          uri = uri,
          name = name,
          sizeBytes = actualSize,
          pageCount = pages,
        )
      } finally {
        try {
          doc?.close()
        } catch (_: Throwable) {
        }
        FileStorageHelper.deleteQuietly(tempDir)
      }
    }

  suspend fun mergePdfs(
    context: Context,
    items: List<PickedPdfItem>,
    onProgress: (current: Int, total: Int, message: String) -> Unit,
  ): ToolResult =
    withContext(Dispatchers.IO) {
      if (items.size < 2) throw PdfNestException("Select at least 2 PDF files to merge.")

      val outputsDir = FileStorageHelper.getOutputsDir(context)
      val tempDir = FileStorageHelper.createJobTempDir(context)
      val ts = FormatUtils.timestampTag()
      val outFile = File(outputsDir, "PDFNest_Merged_$ts.pdf")
      val tempOutFile = File(tempDir, "merged.pdf")

      val totalSteps = items.size + 1
      val merger = PDFMergerUtility()
      val destDoc = PDDocument(MemoryUsageSetting.setupTempFileOnly())
      val openedSources = mutableListOf<PDDocument>()

      try {
        var totalInputBytes = 0L

        items.forEachIndexed { index, item ->
          coroutineContext.ensureActive()
          onProgress(index, totalSteps, "Merging ${item.name} (${index + 1}/${items.size})...")

          val srcTemp = File(tempDir, "src_${index}.pdf")
          copyUriToFile(context, item.uri, srcTemp)
          totalInputBytes += if (item.sizeBytes > 0L) item.sizeBytes else srcTemp.length()

          val srcDoc =
            try {
              PDDocument.load(srcTemp, MemoryUsageSetting.setupTempFileOnly())
            } catch (_: InvalidPasswordException) {
              throw PdfNestException("This PDF is password protected")
            } catch (_: Throwable) {
              throw PdfNestException("Could not read \"${item.name}\". It may be corrupted.")
            }

          if (srcDoc.isEncrypted) {
            srcDoc.close()
            throw PdfNestException("This PDF is password protected")
          }

          openedSources.add(srcDoc)
          merger.appendDocument(destDoc, srcDoc)
          onProgress(index + 1, totalSteps, "Added ${item.name}")
        }

        coroutineContext.ensureActive()
        onProgress(items.size, totalSteps, "Saving merged PDF...")
        destDoc.save(tempOutFile)
        tempOutFile.copyTo(outFile, overwrite = true)
        onProgress(totalSteps, totalSteps, "Merge complete")

        val outBytes = outFile.length()
        val ref = OutputFileRef(outFile.name, outFile.absolutePath, "application/pdf", outBytes)

        ToolResult(
          id = UUID.randomUUID().toString(),
          toolTypeName = ToolType.MERGE_PDF.displayName,
          primaryFilePath = outFile.absolutePath,
          primaryFileName = outFile.name,
          primaryMimeType = "application/pdf",
          primarySizeBytes = outBytes,
          originalTotalBytes = totalInputBytes,
          outputTotalBytes = outBytes,
          allOutputFiles = listOf(ref),
          pageCount = items.sumOf { it.pageCount },
        )
      } catch (t: Throwable) {
        FileStorageHelper.deleteQuietly(outFile)
        if (t is kotlinx.coroutines.CancellationException) throw t
        throw mapFriendlyError(t, "Could not merge the selected PDFs.")
      } finally {
        openedSources.forEach { doc ->
          try {
            doc.close()
          } catch (_: Throwable) {
          }
        }
        try {
          destDoc.close()
        } catch (_: Throwable) {
        }
        FileStorageHelper.deleteQuietly(tempDir)
      }
    }

  private fun copyUriToFile(context: Context, uri: Uri, dest: File) {
    context.contentResolver.openInputStream(uri)?.use { input ->
      FileOutputStream(dest).use { output ->
        input.copyTo(output)
      }
    } ?: throw PdfNestException("Could not open the selected file.")
  }

  private fun measureUriSizeFallback(context: Context, uri: Uri): Long {
    return try {
      context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
          val read = input.read(buffer)
          if (read <= 0) break
          total += read
        }
        total
      } ?: 0L
    } catch (_: Throwable) {
      0L
    }
  }

  private fun computePdfPageDimensions(
    imgW: Int,
    imgH: Int,
    pageSize: PdfPageSize,
  ): Pair<Int, Int> {
    return when (pageSize) {
      PdfPageSize.A4 -> PdfPageSize.A4.widthPt to PdfPageSize.A4.heightPt
      PdfPageSize.LETTER -> PdfPageSize.LETTER.widthPt to PdfPageSize.LETTER.heightPt
      PdfPageSize.FIT_TO_IMAGE -> {
        val maxPt = 1440f
        val maxDim = max(imgW, imgH).toFloat().coerceAtLeast(1f)
        val scale = if (maxDim > maxPt) maxPt / maxDim else 1f
        val w = (imgW * scale).roundToInt().coerceAtLeast(72)
        val h = (imgH * scale).roundToInt().coerceAtLeast(72)
        w to h
      }
    }
  }

  private fun mapFriendlyError(t: Throwable, fallback: String): PdfNestException {
    if (t is PdfNestException) return t
    if (t is OutOfMemoryError) return PdfNestException("File is too large to process on this device.")
    if (t is SecurityException) return PdfNestException("This PDF is password protected")
    return PdfNestException(t.message?.takeIf { it.isNotBlank() } ?: fallback)
  }

  private fun max(a: Int, b: Int): Int = if (a >= b) a else b
}
