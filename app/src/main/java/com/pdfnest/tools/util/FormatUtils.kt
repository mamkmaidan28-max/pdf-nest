package com.pdfnest.tools.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object FormatUtils {
  fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1.0) return "$bytes B"
    val mb = kb / 1024.0
    if (mb < 1.0) return String.format(Locale.US, "%.1f KB", kb)
    val gb = mb / 1024.0
    if (gb < 1.0) return String.format(Locale.US, "%.2f MB", mb)
    return String.format(Locale.US, "%.2f GB", gb)
  }

  fun calculatePercentSaved(originalBytes: Long, newBytes: Long): Int {
    if (originalBytes <= 0L) return 0
    val saved = ((originalBytes - newBytes).toDouble() / originalBytes.toDouble()) * 100.0
    return saved.roundToInt()
  }

  fun sanitizeBaseName(name: String, fallback: String): String {
    val withoutExt = name.substringBeforeLast('.')
    val cleaned =
      withoutExt
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('_', '.', '-')
        .take(40)
    return cleaned.ifBlank { fallback }
  }

  fun timestampTag(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

  fun formatShortDateTime(millis: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(millis))
}
