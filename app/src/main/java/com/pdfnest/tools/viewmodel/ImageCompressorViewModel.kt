package com.pdfnest.tools.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfnest.tools.model.CompressionLevel
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

data class ImageCompressorUiState(
  val selectedImages: List<PickedImageItem> = emptyList(),
  val compressionLevel: CompressionLevel = CompressionLevel.MEDIUM,
  val isProcessing: Boolean = false,
  val progress: ProcessingProgress = ProcessingProgress(),
)

class ImageCompressorViewModel(application: Application) : AndroidViewModel(application) {
  private val _uiState = MutableStateFlow(ImageCompressorUiState())
  val uiState: StateFlow<ImageCompressorUiState> = _uiState.asStateFlow()

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
            val (name, size) = FileStorageHelper.queryUriMetadata(context, uri, "image_${idx + 1}")
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

  fun setCompressionLevel(level: CompressionLevel) {
    _uiState.update { it.copy(compressionLevel = level) }
  }

  fun startCompression(onComplete: (ToolResult) -> Unit) {
    val state = _uiState.value
    if (state.isProcessing || state.selectedImages.isEmpty()) return

    activeJob?.cancel()
    activeJob =
      viewModelScope.launch {
        _uiState.update {
          it.copy(
            isProcessing = true,
            progress = ProcessingProgress(0, state.selectedImages.size, "Starting..."),
          )
        }
        try {
          val context = getApplication<Application>()
          val result =
            PdfEngine.compressImages(
              context = context,
              items = state.selectedImages,
              level = state.compressionLevel,
              onProgress = { current, total, msg ->
                _uiState.update {
                  it.copy(progress = ProcessingProgress(current, total, msg))
                }
              },
            )
          val recentEntries =
            result.allOutputFiles.map { ref ->
              RecentFileEntry(
                id = UUID.randomUUID().toString(),
                fileName = ref.fileName,
                filePath = ref.filePath,
                mimeType = ref.mimeType,
                sizeBytes = ref.sizeBytes,
                toolName = ToolType.IMAGE_COMPRESSOR.displayName,
                createdAtMillis = result.createdAtMillis,
              )
            }
          RecentFilesRepository.addEntries(context, recentEntries)
          ResultStore.setResult(result)
          _uiState.update { it.copy(isProcessing = false) }
          onComplete(result)
        } catch (_: CancellationException) {
          _uiState.update { it.copy(isProcessing = false, progress = ProcessingProgress()) }
        } catch (t: Throwable) {
          _uiState.update { it.copy(isProcessing = false, progress = ProcessingProgress()) }
          _errors.emit(t.message ?: "Failed to compress images.")
        }
      }
  }

  fun cancelCompression() {
    activeJob?.cancel()
    activeJob = null
    _uiState.update { it.copy(isProcessing = false, progress = ProcessingProgress()) }
  }
}
