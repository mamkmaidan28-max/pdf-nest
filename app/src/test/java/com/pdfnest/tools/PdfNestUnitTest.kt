package com.pdfnest.tools

import com.pdfnest.tools.ads.AdConfig
import com.pdfnest.tools.model.CompressionLevel
import com.pdfnest.tools.model.PdfMargin
import com.pdfnest.tools.model.PdfPageSize
import com.pdfnest.tools.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfNestUnitTest {
  @Test
  fun formatFileSize_formatsBytesKbMbCorrectly() {
    assertEquals("0 B", FormatUtils.formatFileSize(0L))
    assertEquals("512 B", FormatUtils.formatFileSize(512L))
    assertEquals("2.0 KB", FormatUtils.formatFileSize(2048L))
    assertEquals("1.50 MB", FormatUtils.formatFileSize((1.5 * 1024 * 1024).toLong()))
  }

  @Test
  fun calculatePercentSaved_computesExpectedPercentage() {
    assertEquals(50, FormatUtils.calculatePercentSaved(1000L, 500L))
    assertEquals(0, FormatUtils.calculatePercentSaved(0L, 100L))
    assertEquals(-20, FormatUtils.calculatePercentSaved(1000L, 1200L))
  }

  @Test
  fun sanitizeBaseName_stripsUnsafeCharactersAndExtension() {
    assertEquals("report_2026_final", FormatUtils.sanitizeBaseName("report 2026/final.pdf", "doc"))
    assertEquals("doc", FormatUtils.sanitizeBaseName("...", "doc"))
  }

  @Test
  fun adConfig_usesOfficialGoogleTestIds() {
    assertEquals("ca-app-pub-3940256099942544~3347511713", AdConfig.APP_ID)
    assertEquals("ca-app-pub-3940256099942544/1033173712", AdConfig.INTERSTITIAL_AD_UNIT_ID)
    assertEquals(3, AdConfig.INTERSTITIAL_TASK_INTERVAL)
    assertEquals(120_000L, AdConfig.INTERSTITIAL_COOLDOWN_MS)
  }

  @Test
  fun compressionAndPdfOptions_haveValidBounds() {
    assertTrue(CompressionLevel.LOW.jpegQuality > CompressionLevel.MEDIUM.jpegQuality)
    assertTrue(CompressionLevel.MEDIUM.jpegQuality > CompressionLevel.HIGH.jpegQuality)
    assertEquals(595, PdfPageSize.A4.widthPt)
    assertEquals(842, PdfPageSize.A4.heightPt)
    assertEquals(0f, PdfMargin.NONE.marginPt)
  }
}
