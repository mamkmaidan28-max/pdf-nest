package com.pdfnest.tools.model

import android.net.Uri
import kotlinx.serialization.Serializable

enum class ToolType(val displayName: String) {
  IMAGE_COMPRESSOR("Image Compressor"),
  JPG_TO_PDF("JPG to PDF"),
  PDF_COMPRESSOR("PDF Compressor"),
  MERGE_PDF("Merge PDF"),
}

enum class CompressionLevel(
  val label: String,
  val subtitle: String,
  val jpegQuality: Int,
  val imageMaxDimension: Int,
  val pdfRenderScale: Float,
) {
  LOW(
    label = "Low",
    subtitle = "Best quality, lighter compression",
    jpegQuality = 85,
    imageMaxDimension = 2560,
    pdfRenderScale = 1.6f,
  ),
  MEDIUM(
    label = "Medium",
    subtitle = "Balanced quality & file size",
    jpegQuality = 65,
    imageMaxDimension = 1920,
    pdfRenderScale = 1.2f,
  ),
  HIGH(
    label = "High",
    subtitle = "Smallest file size",
    jpegQuality = 42,
    imageMaxDimension = 1440,
    pdfRenderScale = 0.85f,
  ),
}

enum class PdfPageSize(val label: String, val widthPt: Int, val heightPt: Int) {
  A4("A4", 595, 842),
  LETTER("Letter", 612, 792),
  FIT_TO_IMAGE("Fit to image", 0, 0),
}

enum class PdfMargin(val label: String, val marginPt: Float) {
  NONE("None", 0f),
  SMALL("Small", 18f),
  NORMAL("Normal", 36f),
}

data class PickedImageItem(
  val id: String,
  val uri: Uri,
  val name: String,
  val sizeBytes: Long,
)

data class PickedPdfItem(
  val id: String,
  val uri: Uri,
  val name: String,
  val sizeBytes: Long,
  val pageCount: Int,
)

@Serializable
data class CompressedImageSummary(
  val fileName: String,
  val outputPath: String,
  val mimeType: String,
  val originalBytes: Long,
  val compressedBytes: Long,
)

@Serializable
data class OutputFileRef(
  val fileName: String,
  val filePath: String,
  val mimeType: String,
  val sizeBytes: Long,
)

@Serializable
data class ToolResult(
  val id: String,
  val toolTypeName: String,
  val primaryFilePath: String,
  val primaryFileName: String,
  val primaryMimeType: String,
  val primarySizeBytes: Long,
  val originalTotalBytes: Long,
  val outputTotalBytes: Long,
  val allOutputFiles: List<OutputFileRef> = emptyList(),
  val imageSummaries: List<CompressedImageSummary> = emptyList(),
  val keptOriginalPdf: Boolean = false,
  val attemptedCompressedBytes: Long = 0L,
  val pageCount: Int = 0,
  val createdAtMillis: Long = System.currentTimeMillis(),
)

@Serializable
data class RecentFileEntry(
  val id: String,
  val fileName: String,
  val filePath: String,
  val mimeType: String,
  val sizeBytes: Long,
  val toolName: String,
  val createdAtMillis: Long,
)

data class ProcessingProgress(
  val currentStep: Int = 0,
  val totalSteps: Int = 1,
  val message: String = "",
) {
  val fraction: Float
    get() = if (totalSteps <= 0) 0f else (currentStep.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)

  val percent: Int
    get() = (fraction * 100f).toInt().coerceIn(0, 100)
}
