package com.pdfnest.tools.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pdfnest.tools.ads.AdManager
import com.pdfnest.tools.model.PdfMargin
import com.pdfnest.tools.model.PdfPageSize
import com.pdfnest.tools.model.PickedImageItem
import com.pdfnest.tools.ui.components.PdfNestTopBar
import com.pdfnest.tools.ui.components.PrimaryActionButton
import com.pdfnest.tools.ui.components.ProcessingCard
import com.pdfnest.tools.ui.components.ToolStepHeader
import com.pdfnest.tools.util.FormatUtils
import com.pdfnest.tools.viewmodel.JpgToPdfViewModel

@Composable
fun JpgToPdfScreen(
  viewModel: JpgToPdfViewModel,
  onBack: () -> Unit,
  onCompleted: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  val activity = context as? Activity

  val photoPicker =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50),
    ) { uris ->
      if (uris.isNotEmpty()) {
        viewModel.addImages(uris)
      }
    }

  LaunchedEffect(Unit) {
    viewModel.errors.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  var draggedId by remember { mutableStateOf<String?>(null) }
  var dragOffsetY by remember { mutableFloatStateOf(0f) }
  val rowHeightPx = with(LocalDensity.current) { 86.dp.toPx() }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      PdfNestTopBar(
        title = "JPG to PDF",
        onBack = {
          if (!state.isProcessing) onBack()
        },
      )
    },
  ) { innerPadding ->
    Box(
      contentAlignment = Alignment.TopCenter,
      modifier = Modifier.fillMaxSize().padding(innerPadding),
    ) {
      LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
      ) {
        item {
          ToolStepHeader(
            stepNumber = 1,
            title = "Select & order images",
            subtitle = "Use arrows or long-press drag handle to set page order.",
          )
        }

        item {
          OutlinedButton(
            onClick = {
              photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            enabled = !state.isProcessing,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("jpg_to_pdf_pick_button"),
          ) {
            Icon(imageVector = Icons.Rounded.AddPhotoAlternate, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text =
                if (state.selectedImages.isEmpty()) "Choose Images"
                else "Add More Images (${state.selectedImages.size} pages)",
              style = MaterialTheme.typography.titleSmall,
            )
          }
        }

        if (state.selectedImages.isNotEmpty()) {
          itemsIndexed(state.selectedImages, key = { _, item -> item.id }) { index, item ->
            val isDragging = draggedId == item.id
            ReorderableImageCard(
              pageNumber = index + 1,
              totalCount = state.selectedImages.size,
              item = item,
              enabled = !state.isProcessing,
              isDragging = isDragging,
              dragTranslationY = if (isDragging) dragOffsetY else 0f,
              onMoveUp = { viewModel.moveImage(index, index - 1) },
              onMoveDown = { viewModel.moveImage(index, index + 1) },
              onRemove = { viewModel.removeImage(item.id) },
              onDragStart = {
                draggedId = item.id
                dragOffsetY = 0f
              },
              onDragDelta = { delta ->
                dragOffsetY += delta
                val currentIdx = state.selectedImages.indexOfFirst { it.id == item.id }
                if (currentIdx >= 0) {
                  if (dragOffsetY > rowHeightPx * 0.65f && currentIdx < state.selectedImages.lastIndex) {
                    viewModel.moveImage(currentIdx, currentIdx + 1)
                    dragOffsetY -= rowHeightPx
                  } else if (dragOffsetY < -rowHeightPx * 0.65f && currentIdx > 0) {
                    viewModel.moveImage(currentIdx, currentIdx - 1)
                    dragOffsetY += rowHeightPx
                  }
                }
              },
              onDragEnd = {
                draggedId = null
                dragOffsetY = 0f
              },
            )
          }
        } else {
          item {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "No images selected yet. Each selected image becomes one PDF page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
              )
            }
          }
        }

        item {
          Spacer(modifier = Modifier.height(4.dp))
          ToolStepHeader(
            stepNumber = 2,
            title = "PDF layout options",
            subtitle = "Each image is centered while keeping its aspect ratio.",
          )
        }

        item {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              text = "Page size",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              PdfPageSize.entries.forEach { sizeOption ->
                FilterChip(
                  selected = state.pageSize == sizeOption,
                  onClick = { viewModel.setPageSize(sizeOption) },
                  enabled = !state.isProcessing,
                  label = { Text(sizeOption.label) },
                  modifier = Modifier.testTag("page_size_${sizeOption.name.lowercase()}"),
                )
              }
            }

            Text(
              text = "Margin",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              PdfMargin.entries.forEach { marginOption ->
                FilterChip(
                  selected = state.margin == marginOption,
                  onClick = { viewModel.setMargin(marginOption) },
                  enabled = !state.isProcessing,
                  label = { Text(marginOption.label) },
                  modifier = Modifier.testTag("page_margin_${marginOption.name.lowercase()}"),
                )
              }
            }
          }
        }

        item {
          Spacer(modifier = Modifier.height(4.dp))
          ToolStepHeader(stepNumber = 3, title = "Create PDF")
        }

        item {
          if (state.isProcessing) {
            ProcessingCard(
              progress = state.progress,
              onCancel = viewModel::cancelConversion,
            )
          } else {
            val count = state.selectedImages.size
            PrimaryActionButton(
              text = if (count == 0) "Create PDF" else "Create PDF ($count ${if (count == 1) "page" else "pages"})",
              enabled = count > 0,
              onClick = {
                viewModel.startConversion {
                  AdManager.onTaskCompleted(activity, context) {
                    onCompleted()
                  }
                }
              },
              testTag = "start_jpg_to_pdf_button",
            )
          }
          Spacer(modifier = Modifier.height(16.dp))
        }
      }
    }
  }
}

@Composable
private fun ReorderableImageCard(
  pageNumber: Int,
  totalCount: Int,
  item: PickedImageItem,
  enabled: Boolean,
  isDragging: Boolean,
  dragTranslationY: Float,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onRemove: () -> Unit,
  onDragStart: () -> Unit,
  onDragDelta: (Float) -> Unit,
  onDragEnd: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
          else MaterialTheme.colorScheme.surface,
      ),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 1.dp),
    border =
      BorderStroke(
        width = if (isDragging) 1.5.dp else 1.dp,
        color =
          if (isDragging) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
      ),
    modifier =
      Modifier
        .fillMaxWidth()
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer { translationY = dragTranslationY },
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.DragIndicator,
        contentDescription = "Long press and drag to reorder",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
          Modifier
            .size(32.dp)
            .padding(4.dp)
            .pointerInput(enabled, item.id) {
              if (!enabled) return@pointerInput
              detectDragGesturesAfterLongPress(
                onDragStart = { onDragStart() },
                onDrag = { change, dragAmount ->
                  change.consume()
                  onDragDelta(dragAmount.y)
                },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragEnd() },
              )
            },
      )
      AsyncImage(
        model = item.uri,
        contentDescription = item.name,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Page $pageNumber • ${item.name}",
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = FormatUtils.formatFileSize(item.sizeBytes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(
        onClick = onMoveUp,
        enabled = enabled && pageNumber > 1,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(imageVector = Icons.Rounded.ArrowUpward, contentDescription = "Move up")
      }
      IconButton(
        onClick = onMoveDown,
        enabled = enabled && pageNumber < totalCount,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(imageVector = Icons.Rounded.ArrowDownward, contentDescription = "Move down")
      }
      IconButton(
        onClick = onRemove,
        enabled = enabled,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Remove ${item.name}")
      }
    }
  }
}
