package com.pdfnest.tools.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pdfnest.tools.ads.AdManager
import com.pdfnest.tools.model.PickedImageItem
import com.pdfnest.tools.ui.components.CompressionLevelSelector
import com.pdfnest.tools.ui.components.PdfNestTopBar
import com.pdfnest.tools.ui.components.PrimaryActionButton
import com.pdfnest.tools.ui.components.ProcessingCard
import com.pdfnest.tools.ui.components.ToolStepHeader
import com.pdfnest.tools.util.FormatUtils
import com.pdfnest.tools.viewmodel.ImageCompressorViewModel

@Composable
fun ImageCompressorScreen(
  viewModel: ImageCompressorViewModel,
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

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      PdfNestTopBar(
        title = "Image Compressor",
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
      ) {
        item {
          ToolStepHeader(
            stepNumber = 1,
            title = "Select images",
            subtitle = "Supports JPG, PNG, and WEBP. Output is saved as JPEG.",
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
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("pick_images_button"),
          ) {
            Icon(imageVector = Icons.Rounded.AddPhotoAlternate, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text =
                if (state.selectedImages.isEmpty()) "Choose Images"
                else "Add More Images (${state.selectedImages.size} selected)",
              style = MaterialTheme.typography.titleSmall,
            )
          }
        }

        if (state.selectedImages.isNotEmpty()) {
          items(state.selectedImages, key = { it.id }) { item ->
            SelectedImageRow(
              item = item,
              enabled = !state.isProcessing,
              onRemove = { viewModel.removeImage(item.id) },
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
                text = "No images selected yet. Pick one or multiple photos to compress.",
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
            title = "Compression level",
            subtitle = "EXIF rotation is preserved automatically.",
          )
        }

        item {
          CompressionLevelSelector(
            selected = state.compressionLevel,
            onSelect = viewModel::setCompressionLevel,
            enabled = !state.isProcessing,
          )
        }

        item {
          Spacer(modifier = Modifier.height(4.dp))
          ToolStepHeader(
            stepNumber = 3,
            title = "Start compression",
          )
        }

        item {
          if (state.isProcessing) {
            ProcessingCard(
              progress = state.progress,
              onCancel = viewModel::cancelCompression,
            )
          } else {
            val count = state.selectedImages.size
            val label =
              if (count == 0) "Compress Images"
              else if (count == 1) "Compress 1 Image"
              else "Compress $count Images"
            PrimaryActionButton(
              text = label,
              enabled = count > 0,
              onClick = {
                viewModel.startCompression {
                  AdManager.onTaskCompleted(activity, context) {
                    onCompleted()
                  }
                }
              },
              testTag = "start_image_compress_button",
            )
          }
          Spacer(modifier = Modifier.height(16.dp))
        }
      }
    }
  }
}

@Composable
private fun SelectedImageRow(
  item: PickedImageItem,
  enabled: Boolean,
  onRemove: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
    ) {
      AsyncImage(
        model = item.uri,
        contentDescription = item.name,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.name,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "Original: ${FormatUtils.formatFileSize(item.sizeBytes)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(
        onClick = onRemove,
        enabled = enabled,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("remove_image_${item.id}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = "Remove ${item.name}",
        )
      }
    }
  }
}
