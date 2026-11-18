package com.pdfnest.tools.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pdfnest.tools.model.RecentFileEntry
import com.pdfnest.tools.model.ToolResult
import com.pdfnest.tools.util.FileStorageHelper
import com.pdfnest.tools.util.RecentFilesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the latest completed ToolResult across screen navigation and configuration changes.
 */
object ResultStore {
  private val _currentResult = MutableStateFlow<ToolResult?>(null)
  val currentResult: StateFlow<ToolResult?> = _currentResult.asStateFlow()

  private val _savedResultIds = MutableStateFlow<Set<String>>(emptySet())
  val savedResultIds: StateFlow<Set<String>> = _savedResultIds.asStateFlow()

  fun setResult(result: ToolResult) {
    _currentResult.value = result
  }

  fun markSaved(resultId: String) {
    _savedResultIds.value = _savedResultIds.value + resultId
  }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
  val recentFiles: StateFlow<List<RecentFileEntry>> = RecentFilesRepository.recentFiles

  init {
    RecentFilesRepository.init(application)
  }

  fun refreshRecentFiles() {
    RecentFilesRepository.refresh(getApplication())
  }

  fun openRecent(entry: RecentFileEntry): Result<Unit> {
    return FileStorageHelper.openFile(getApplication(), entry.filePath, entry.mimeType)
  }

  fun shareRecent(entry: RecentFileEntry): Result<Unit> {
    return FileStorageHelper.shareFiles(
      getApplication(),
      listOf(
        com.pdfnest.tools.model.OutputFileRef(
          fileName = entry.fileName,
          filePath = entry.filePath,
          mimeType = entry.mimeType,
          sizeBytes = entry.sizeBytes,
        )
      ),
    )
  }
}
