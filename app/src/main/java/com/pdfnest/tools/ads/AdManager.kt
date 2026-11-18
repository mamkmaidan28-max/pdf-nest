package com.pdfnest.tools.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Manages preloading and frequency-capped display of AdMob interstitials.
 * Never blocks tool completion or shows errors when offline.
 */
object AdManager {
  private var interstitialAd: InterstitialAd? = null
  private var isLoading: Boolean = false
  private var completedTaskCount: Int = 0
  private var lastShownElapsedMs: Long = -AdConfig.INTERSTITIAL_COOLDOWN_MS

  fun preloadInterstitial(context: Context) {
    if (isLoading || interstitialAd != null) return
    isLoading = true
    try {
      val appContext = context.applicationContext
      InterstitialAd.load(
        appContext,
        AdConfig.INTERSTITIAL_AD_UNIT_ID,
        AdRequest.Builder().build(),
        object : InterstitialAdLoadCallback() {
          override fun onAdLoaded(ad: InterstitialAd) {
            isLoading = false
            interstitialAd = ad
          }

          override fun onAdFailedToLoad(error: LoadAdError) {
            isLoading = false
            interstitialAd = null
          }
        },
      )
    } catch (_: Throwable) {
      isLoading = false
      interstitialAd = null
    }
  }

  /**
   * Records a completed tool job and shows an interstitial only if:
   * - This is a multiple of 3 completed tasks
   * - At least 2 minutes have elapsed since the previous interstitial
   * - An interstitial is already loaded
   */
  fun onTaskCompleted(activity: Activity?, context: Context, onContinue: () -> Unit) {
    completedTaskCount += 1
    val shouldConsiderAd = completedTaskCount % AdConfig.INTERSTITIAL_TASK_INTERVAL == 0
    val nowElapsed = SystemClock.elapsedRealtime()
    val cooldownSatisfied = (nowElapsed - lastShownElapsedMs) >= AdConfig.INTERSTITIAL_COOLDOWN_MS

    val ad = interstitialAd
    if (!shouldConsiderAd || !cooldownSatisfied || ad == null || activity == null || activity.isFinishing) {
      if (ad == null) {
        preloadInterstitial(context)
      }
      onContinue()
      return
    }

    interstitialAd = null
    lastShownElapsedMs = nowElapsed
    ad.fullScreenContentCallback =
      object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
          preloadInterstitial(context)
          onContinue()
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
          preloadInterstitial(context)
          onContinue()
        }
      }
    try {
      ad.show(activity)
    } catch (_: Throwable) {
      preloadInterstitial(context)
      onContinue()
    }
  }
}
