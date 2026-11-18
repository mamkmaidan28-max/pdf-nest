package com.pdfnest.tools.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfnest.tools.model.CompressionLevel
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

data class PdfCompressorUiState(
  val selectedPdf: PickedPdfItem? = null,
  val isInspecting: Boolean = false,
  val compressionLevel: CompressionLevel = CompressionLevel.MEDIUM,
  val isProcessing: Boolean = false,
  val progress: ProcessingProgress = ProcessingProgress(),
)

class PdfCompressorViewModel(application: Application) : AndroidViewModel(application) {
  private val _uiState = MutableStateFlow(PdfCompressorUiState())
  val uiState: StateFlow<PdfCompressorUiState> = _uiState.asStateFlow()

  private val _errors = MutableSharedFlow<String>()
  val errors: SharedFlow<String> = _errors.asSharedFlow()

  private var activeJob: Job? = null

  fun selectPdf(uri: Uri) {
    viewModelScope.launch {
      _uiState.update { it.copy(isInspecting = true) }
      try {
        val inspected = PdfEngine.inspectPdf(getApplication(), uri)
        _uiState.update { it.copy(selectedPdf = inspected, isInspecting = false) }
      } catch (t: Throwable) {
        _uiState.update { it.copy(isInspecting = false) }
        _errors.emit(t.message ?: "Could not open selected PDF.")
      }
    }
  }

  fun clearPdf() {
    _uiState.update { it.copy(selectedPdf = null) }
  }

  fun setCompressionLevel(level: CompressionLevel) {
    _uiState.update { it.copy(compressionLevel = level) }
  }

  fun startCompression(onComplete: (ToolResult) -> Unit) {
    val state = _uiState.value
    val pdf = state.selectedPdf ?: return
    if (state.isProcessing || state.isInspecting) return

    activeJob?.cancel()
    activeJob =
      viewModelScope.launch {
        _uiState.update {
          it.copy(
            isProcessing = true,
            progress = ProcessingProgress(0, pdf.pageCount.coerceAtLeast(1), "Starting compression..."),
          )
        }
        try {
          val context = getApplication<Application>()
          val result =
            PdfEngine.compressPdf(
              context = context,
              pickedPdf = pdf,
              level = state.compressionLevel,
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
                toolName = ToolType.PDF_COMPRESSOR.displayName,
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
          _errors.emit(t.message ?: "Could not compress PDF.")
        }
      }
  }

  fun cancelCompression() {
    activeJob?.cancel()
    activeJob = null
    _uiState.update { it.copy(isProcessing = false, progress = ProcessingProgress()) }
  }
}
