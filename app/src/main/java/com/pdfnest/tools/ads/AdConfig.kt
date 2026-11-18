package com.pdfnest.tools.ads

/**
 * Centralized Google AdMob configuration.
 * Replace these Google-provided test IDs with your production IDs before release.
 */
object AdConfig {
  const val APP_ID = "ca-app-pub-3940256099942544~3347511713"
  const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

  /** Show an interstitial only after every 3rd completed task. */
  const val INTERSTITIAL_TASK_INTERVAL = 3

  /** Minimum cooldown between two interstitials (2 minutes). */
  const val INTERSTITIAL_COOLDOWN_MS = 120_000L
}
