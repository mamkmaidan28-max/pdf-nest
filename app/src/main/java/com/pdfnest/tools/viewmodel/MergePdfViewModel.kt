package com.pdfnest.tools.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfnest.tools.model.PickedPdfItem
import com.pdfnest.tools.model.ProcessingProgress
import com.pdfnest.tools.model.RecentFileEntry
import com.pdfnest.tools.model.ToolResult
import com.pdfnest.tools.model.ToolType
import com.pdfnest.tools.util.PdfEngine
import com.pdfnest.tools.util.RecentFilesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MergePdfUiState(
  val selectedPdfs: List<PickedPdfItem> = emptyList(),
  val isInspecting: Boolean = false,
  val isProcessing: Boolean = false,
  val progress: ProcessingProgress = ProcessingProgress(),
)

class MergePdfViewModel(application: Application) : AndroidViewModel(application) {
  private val _uiState = MutableStateFlow(MergePdfUiState())
  val uiState: StateFlow<MergePdfUiState> = _uiState.asStateFlow()

  private val _errors = MutableSharedFlow<String>()
  val errors: SharedFlow<String> = _errors.asSharedFlow()

  private var activeJob: Job? = null

  fun addPdfs(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isInspecting = true) }
      val context = getApplication<Application>()
      val added = mutableListOf<PickedPdfItem>()
      var firstError: String? = null

      val existingUris = _uiState.value.selectedPdfs.map { it.uri }.toSet()
      for (uri in uris) {
        if (uri in existingUris) continue
        try {
          added.add(PdfEngine.inspectPdf(context, uri))
        } catch (t: Throwable) {
          if (firstError == null) {
            firstError = t.message ?: "Could not read one of the selected PDFs."
          }
        }
      }

      _uiState.update { state ->
        state.copy(
          selectedPdfs = state.selectedPdfs + added,
          isInspecting = false,
        )
      }
      if (firstError != null) {
        _errors.emit(firstError)
      }
    }
  }

  fun removePdf(id: String) {
    _uiState.update { state ->
      state.copy(selectedPdfs = state.selectedPdfs.filterNot { it.id == id })
    }
  }

  fun movePdf(fromIndex: Int, toIndex: Int) {
    _uiState.update { state ->
      val list = state.selectedPdfs.toMutableList()
      if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
        return@update state
      }
      val item = list.removeAt(fromIndex)
      list.add(toIndex, item)
      state.copy(selectedPdfs = list)
    }
  }

  fun startMerge(onComplete: (ToolResult) -> Unit) {
    val state = _uiState.value
    if (state.isProcessing || state.isInspecting || state.selectedPdfs.size < 2) return

    activeJob?.cancel()
    activeJob =
      viewModelScope.launch {
        _uiState.update {
          it.copy(
            isProcessing = true,
            progress = ProcessingProgress(0, state.selectedPdfs.size + 1, "Starting merge..."),
          )
        }
        try {
          val context = getApplication<Application>()
          val result =
            PdfEngine.mergePdfs(
              context = context,
              items = state.selectedPdfs,
              onProgress = { current, total, msg ->
                _uiState.update {
                  it.copy(progress = ProcessingProgress(current, total, msg))
                }
              },
            )
          RecentFilesRepository.addEntries(
            context,
            listOf(
              RecentFileEntry(
                id = result.id,
                fileName = result.primaryFileName,
                filePath = result.primaryFilePath,
                mimeType = result.primaryMimeType,
                sizeBytes = result.primarySizeBytes,
                toolName = ToolType.MERGE_PDF.displayName,
                createdAtMillis = result.createdAtMillis,
              )
            ),
          )
          ResultStore.setResult(result)
          _uiState.update { it.copy(isProcessing = false) }
          onComplete(result)
        } catch (_: CancellationException) {
          _uiState.update { it.copy(isProcessing = false, progress = ProcessingProgress()) }
        } catch (t: Throwable) {
          _uiState.update { it.copy(isProcessing = false, progress = ProcessingProgress()) }
          _errors.emit(t.message ?: "Could not merge PDFs.")
        }
      }
  }

  fun cancelMerge() {
    activeJob?.cancel()
    activeJob = null
    _uiState.update { it.copy(isProcessing = false, progress = ProcessingProgress()) }
  }
}
