package com.pdfnest.tools.util

import android.content.Context
import com.pdfnest.tools.model.RecentFileEntry
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

object RecentFilesRepository {
  private const val PREFS_NAME = "pdf_nest_prefs"
  private const val KEY_RECENT_FILES = "recent_files_json"
  private const val MAX_RECENT_FILES = 10

  private val json = Json { ignoreUnknownKeys = true }
  private val _recentFiles = MutableStateFlow<List<RecentFileEntry>>(emptyList())
  val recentFiles: StateFlow<List<RecentFileEntry>> = _recentFiles.asStateFlow()

  @Volatile
  private var initialized = false

  fun init(context: Context) {
    if (initialized) return
    synchronized(this) {
      if (initialized) return
      _recentFiles.value = loadValidFromPrefs(context.applicationContext)
      initialized = true
    }
  }

  fun refresh(context: Context) {
    synchronized(this) {
      val valid = loadValidFromPrefs(context.applicationContext)
      _recentFiles.value = valid
      saveToPrefs(context.applicationContext, valid)
    }
  }

  fun addEntries(context: Context, newEntries: List<RecentFileEntry>) {
    if (newEntries.isEmpty()) return
    synchronized(this) {
      val appContext = context.applicationContext
      val current = loadValidFromPrefs(appContext)
      val combined =
        (newEntries + current)
          .distinctBy { it.filePath }
          .filter { File(it.filePath).exists() }
          .take(MAX_RECENT_FILES)
      saveToPrefs(appContext, combined)
      _recentFiles.value = combined
    }
  }

  private fun loadValidFromPrefs(context: Context): List<RecentFileEntry> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_RECENT_FILES, null) ?: return emptyList()
    return try {
      json.decodeFromString<List<RecentFileEntry>>(raw)
        .filter { File(it.filePath).exists() }
        .take(MAX_RECENT_FILES)
    } catch (_: Throwable) {
      emptyList()
    }
  }

  private fun saveToPrefs(context: Context, list: List<RecentFileEntry>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val encoded = json.encodeToString(list)
    prefs.edit().putString(KEY_RECENT_FILES, encoded).apply()
  }
}
