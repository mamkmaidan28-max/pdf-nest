package com.pdfnest.tools.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfnest.tools.ads.AdManager
import com.pdfnest.tools.model.PickedPdfItem
import com.pdfnest.tools.ui.components.PdfNestTopBar
import com.pdfnest.tools.ui.components.PrimaryActionButton
import com.pdfnest.tools.ui.components.ProcessingCard
import com.pdfnest.tools.ui.components.ToolStepHeader
import com.pdfnest.tools.util.FormatUtils
import com.pdfnest.tools.viewmodel.MergePdfViewModel

@Composable
fun MergePdfScreen(
  viewModel: MergePdfViewModel,
  onBack: () -> Unit,
  onCompleted: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  val activity = context as? Activity

  val multiPdfPicker =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
      if (uris.isNotEmpty()) {
        viewModel.addPdfs(uris)
      }
    }

  LaunchedEffect(Unit) {
    viewModel.errors.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  var draggedId by remember { mutableStateOf<String?>(null) }
  var dragOffsetY by remember { mutableFloatStateOf(0f) }
  val rowHeightPx = with(LocalDensity.current) { 82.dp.toPx() }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      PdfNestTopBar(
        title = "Merge PDF",
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
            title = "Select PDFs (2 or more)",
            subtitle = "Preserves original text and quality. Reorder below.",
          )
        }

        item {
          OutlinedButton(
            onClick = { multiPdfPicker.launch(arrayOf("application/pdf")) },
            enabled = !state.isProcessing && !state.isInspecting,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("pick_pdfs_merge_button"),
          ) {
            Icon(imageVector = Icons.Rounded.LibraryAdd, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text =
                if (state.selectedPdfs.isEmpty()) "Choose PDFs"
                else "Add More PDFs (${state.selectedPdfs.size} selected)",
              style = MaterialTheme.typography.titleSmall,
            )
          }
        }

        if (state.isInspecting) {
          item {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp),
              ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Reading PDF metadata...", style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }

        if (state.selectedPdfs.isNotEmpty()) {
          itemsIndexed(state.selectedPdfs, key = { _, item -> item.id }) { index, pdf ->
            val isDragging = draggedId == pdf.id
            MergePdfItemCard(
              orderNumber = index + 1,
              totalCount = state.selectedPdfs.size,
              item = pdf,
              enabled = !state.isProcessing,
              isDragging = isDragging,
              dragTranslationY = if (isDragging) dragOffsetY else 0f,
              onMoveUp = { viewModel.movePdf(index, index - 1) },
              onMoveDown = { viewModel.movePdf(index, index + 1) },
              onRemove = { viewModel.removePdf(pdf.id) },
              onDragStart = {
                draggedId = pdf.id
                dragOffsetY = 0f
              },
              onDragDelta = { delta ->
                dragOffsetY += delta
                val currentIdx = state.selectedPdfs.indexOfFirst { it.id == pdf.id }
                if (currentIdx >= 0) {
                  if (dragOffsetY > rowHeightPx * 0.65f && currentIdx < state.selectedPdfs.lastIndex) {
                    viewModel.movePdf(currentIdx, currentIdx + 1)
                    dragOffsetY -= rowHeightPx
                  } else if (dragOffsetY < -rowHeightPx * 0.65f && currentIdx > 0) {
                    viewModel.movePdf(currentIdx, currentIdx - 1)
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
        } else if (!state.isInspecting) {
          item {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "Pick at least 2 PDFs to combine them into a single document.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
              )
            }
          }
        }

        item {
          Spacer(modifier = Modifier.height(4.dp))
          val totalPages = state.selectedPdfs.sumOf { it.pageCount }
          val totalSize = state.selectedPdfs.sumOf { it.sizeBytes }
          ToolStepHeader(
            stepNumber = 2,
            title = "Review & merge",
            subtitle =
              if (state.selectedPdfs.size == 1) "Select at least 1 more PDF to enable merging."
              else if (state.selectedPdfs.size >= 2) "${state.selectedPdfs.size} files • $totalPages pages • ${FormatUtils.formatFileSize(totalSize)}"
              else null,
          )
        }

        item {
          if (state.isProcessing) {
            ProcessingCard(
              progress = state.progress,
              onCancel = viewModel::cancelMerge,
            )
          } else {
            PrimaryActionButton(
              text =
                if (state.selectedPdfs.size >= 2) "Merge ${state.selectedPdfs.size} PDFs"
                else "Merge PDFs",
              enabled = state.selectedPdfs.size >= 2 && !state.isInspecting,
              onClick = {
                viewModel.startMerge {
                  AdManager.onTaskCompleted(activity, context) {
                    onCompleted()
                  }
                }
              },
              testTag = "start_merge_pdf_button",
            )
          }
          Spacer(modifier = Modifier.height(16.dp))
        }
      }
    }
  }
}

@Composable
private fun MergePdfItemCard(
  orderNumber: Int,
  totalCount: Int,
  item: PickedPdfItem,
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
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
      ) {
        Icon(
          imageVector = Icons.Rounded.Description,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "$orderNumber. ${item.name}",
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${item.pageCount} ${if (item.pageCount == 1) "page" else "pages"} • ${FormatUtils.formatFileSize(item.sizeBytes)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(
        onClick = onMoveUp,
        enabled = enabled && orderNumber > 1,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(imageVector = Icons.Rounded.ArrowUpward, contentDescription = "Move up")
      }
      IconButton(
        onClick = onMoveDown,
        enabled = enabled && orderNumber < totalCount,
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
