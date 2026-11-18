package com.pdfnest.tools

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.pdfnest.tools.ads.AdManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Application entry point initializing PDFBox resources and Google Mobile Ads once at startup.
 */
class PdfNestApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    // Initialize PDFBox font/resource mappings once for Merge PDF
    PDFBoxResourceLoader.init(applicationContext)

    // Initialize Mobile Ads safely without blocking UI or failing when offline
    try {
      MobileAds.initialize(this) {}
      AdManager.preloadInterstitial(applicationContext)
    } catch (_: Throwable) {
      // Ignore ad SDK initialization errors so offline tools always work
    }
  }
}
