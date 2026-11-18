package com.pdfnest.tools.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.UploadFile
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfnest.tools.ads.AdManager
import com.pdfnest.tools.ui.components.AdaptivePageContainer
import com.pdfnest.tools.ui.components.CompressionLevelSelector
import com.pdfnest.tools.ui.components.PdfNestTopBar
import com.pdfnest.tools.ui.components.PrimaryActionButton
import com.pdfnest.tools.ui.components.ProcessingCard
import com.pdfnest.tools.ui.components.ToolStepHeader
import com.pdfnest.tools.util.FormatUtils
import com.pdfnest.tools.viewmodel.PdfCompressorViewModel

@Composable
fun PdfCompressorScreen(
  viewModel: PdfCompressorViewModel,
  onBack: () -> Unit,
  onCompleted: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  val activity = context as? Activity

  val pdfPicker =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
      if (uri != null) {
        viewModel.selectPdf(uri)
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
        title = "PDF Compressor",
        onBack = {
          if (!state.isProcessing) onBack()
        },
      )
    },
  ) { innerPadding ->
    AdaptivePageContainer(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ToolStepHeader(
          stepNumber = 1,
          title = "Select a PDF",
          subtitle = "Pick one PDF file from your device.",
        )

        OutlinedButton(
          onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
          enabled = !state.isProcessing && !state.isInspecting,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("pick_pdf_compress_button"),
        ) {
          Icon(imageVector = Icons.Rounded.UploadFile, contentDescription = null)
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = if (state.selectedPdf == null) "Choose PDF" else "Change PDF",
            style = MaterialTheme.typography.titleSmall,
          )
        }

        if (state.isInspecting) {
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
              Text("Checking PDF...", style = MaterialTheme.typography.bodyMedium)
            }
          }
        } else if (state.selectedPdf != null) {
          val pdf = state.selectedPdf!!
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier =
                  Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Description,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = pdf.name,
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "${pdf.pageCount} ${if (pdf.pageCount == 1) "page" else "pages"} • ${FormatUtils.formatFileSize(pdf.sizeBytes)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = viewModel::clearPdf,
                enabled = !state.isProcessing,
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear PDF")
              }
            }
          }
        }

        ToolStepHeader(
          stepNumber = 2,
          title = "Compression level",
        )

        CompressionLevelSelector(
          selected = state.compressionLevel,
          onSelect = viewModel::setCompressionLevel,
          enabled = !state.isProcessing,
        )

        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "Compressed pages become images, so text may not be selectable.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }

        ToolStepHeader(stepNumber = 3, title = "Start compression")

        if (state.isProcessing) {
          ProcessingCard(
            progress = state.progress,
            onCancel = viewModel::cancelCompression,
          )
        } else {
          PrimaryActionButton(
            text = "Compress PDF",
            enabled = state.selectedPdf != null && !state.isInspecting,
            onClick = {
              viewModel.startCompression {
                AdManager.onTaskCompleted(activity, context) {
                  onCompleted()
                }
              }
            },
            testTag = "start_pdf_compress_button",
          )
        }

        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}
