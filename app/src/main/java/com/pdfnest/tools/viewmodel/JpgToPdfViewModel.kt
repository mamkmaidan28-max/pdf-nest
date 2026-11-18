package com.pdfnest.tools.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfnest.tools.model.PdfMargin
import com.pdfnest.tools.model.PdfPageSize
import com.pdfnest.tools.model.PickedImageItem
import com.pdfnest.tools.model.ProcessingProgress
import com.pdfnest.tools.model.RecentFileEntry
import com.pdfnest.tools.model.ToolResult
import com.pdfnest.tools.model.ToolType
import com.pdfnest.tools.util.FileStorageHelper
import com.pdfnest.tools.util.PdfEngine
import com.pdfnest.tools.util.RecentFilesRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class JpgToPdfUiState(
  val selectedImages: List<PickedImageItem> = emptyList(),
  val pageSize: PdfPageSize = PdfPageSize.A4,
  val margin: PdfMargin = PdfMargin.SMALL,
  val isProcessing: Boolean = false,
  val progress: ProcessingProgress = ProcessingProgress(),
)

class JpgToPdfViewModel(application: Application) : AndroidViewModel(application) {
  private val _uiState = MutableStateFlow(JpgToPdfUiState())
  val uiState: StateFlow<JpgToPdfUiState> = _uiState.asStateFlow()

  private val _errors = MutableSharedFlow<String>()
  val errors: SharedFlow<String> = _errors.asSharedFlow()

  private var activeJob: Job? = null

  fun addImages(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      val context = getApplication<Application>()
      val newItems =
        withContext(Dispatchers.IO) {
          uris.mapIndexed { idx, uri ->
            val (name, size) = FileStorageHelper.queryUriMetadata(context, uri, "page_${idx + 1}")
            PickedImageItem(
              id = UUID.randomUUID().toString(),
              uri = uri,
              name = name,
              sizeBytes = size,
            )
          }
        }
      _uiState.update { state ->
        val existingUris = state.selectedImages.map { it.uri }.toSet()
        val uniqueNew = newItems.filterNot { it.uri in existingUris }
        state.copy(selectedImages = state.selectedImages + uniqueNew)
      }
    }
  }

  fun removeImage(id: String) {
    _uiState.update { state ->
      state.copy(selectedImages = state.selectedImages.filterNot { it.id == id })
    }
  }

  fun moveImage(fromIndex: Int, toIndex: Int) {
    _uiState.update { state ->
      val list = state.selectedImages.toMutableList()
      if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
        return@update state
      }
      val item = list.removeAt(fromIndex)
      list.add(toIndex, item)
      state.copy(selectedImages = list)
    }
  }

  fun setPageSize(pageSize: PdfPageSize) {
    _uiState.update { it.copy(pageSize = pageSize) }
  }

  fun setMargin(margin: PdfMargin) {
    _uiState.update { it.copy(margin = margin) }
  }

  fun startConversion(onComplete: (ToolResult) -> Unit) {
    val state = _uiState.value
    if (state.isProcessing || state.selectedImages.isEmpty()) return

    activeJob?.cancel()
    activeJob =
      viewModelScope.launch {
        _uiState.update {
          it.copy(
            isProcessing = true,
            progress = ProcessingProgress(0, state.selectedImages.size, "Preparing PDF..."),
          )
        }
        try {
          val context = getApplication<Application>()
          val result =
            PdfEngine.imagesToPdf(
              context = context,
              items = state.selectedImages,
              pageSize = state.pageSize,
              margin = state.margin,
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
                toolName = ToolType.JPG_TO_PDF.displayName,
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
          _errors.emit(t.message ?: "Could not create PDF.")
        }
      }
  }

  fun cancelConversion() {
    activeJob?.cancel()
    activeJob = null
    _uiState.update { it.copy(isProcessing = false, progress = ProcessingProgress()) }
  }
}
