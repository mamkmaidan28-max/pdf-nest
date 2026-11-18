package com.pdfnest.tools.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.SuccessGreen
import com.pdfnest.tools.model.CompressedImageSummary
import com.pdfnest.tools.model.ToolResult
import com.pdfnest.tools.ui.components.PdfNestTopBar
import com.pdfnest.tools.util.FileStorageHelper
import com.pdfnest.tools.util.FormatUtils
import com.pdfnest.tools.viewmodel.ResultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ResultScreen(
  onBackToHome: () -> Unit,
) {
  val result by ResultStore.currentResult.collectAsStateWithLifecycle()
  val savedIds by ResultStore.savedResultIds.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  val checkScale = remember { Animatable(0.4f) }
  LaunchedEffect(result?.id) {
    checkScale.snapTo(0.4f)
    checkScale.animateTo(
      targetValue = 1f,
      animationSpec =
        spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessMediumLow,
        ),
    )
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      PdfNestTopBar(title = result?.toolTypeName ?: "Result", onBack = onBackToHome)
    },
  ) { innerPadding ->
    val current = result
    if (current == null) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("No result available", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(12.dp))
          Button(onClick = onBackToHome) {
            Text("Back to Home")
          }
        }
      }
      return@Scaffold
    }

    val isSaved = current.id in savedIds

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
          SuccessHeaderCard(
            result = current,
            checkScale = checkScale.value,
          )
        }

        if (current.keptOriginalPdf) {
          item {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(14.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Info,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text =
                    "This PDF is already well-optimized. The re-encoded version (${FormatUtils.formatFileSize(current.attemptedCompressedBytes)}) was not smaller than the original (${FormatUtils.formatFileSize(current.originalTotalBytes)}), so your original PDF was kept.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
              }
            }
          }
        }

        item {
          ActionButtonsSection(
            isMultiOutput = current.allOutputFiles.size > 1,
            isSaved = isSaved,
            onOpen = {
              FileStorageHelper.openFile(context, current.primaryFilePath, current.primaryMimeType)
                .onFailure { err ->
                  scope.launch { snackbarHostState.showSnackbar(err.message ?: "Unable to open file.") }
                }
            },
            onShare = {
              FileStorageHelper.shareFiles(context, current.allOutputFiles)
                .onFailure { err ->
                  scope.launch { snackbarHostState.showSnackbar(err.message ?: "Unable to share file.") }
                }
            },
            onSaveToDownloads = {
              scope.launch {
                val res =
                  withContext(Dispatchers.IO) {
                    FileStorageHelper.saveToDownloads(context, current.allOutputFiles)
                  }
                res
                  .onSuccess { msg ->
                    ResultStore.markSaved(current.id)
                    snackbarHostState.showSnackbar(msg)
                  }
                  .onFailure { err ->
                    snackbarHostState.showSnackbar(err.message ?: "Could not save to Downloads.")
                  }
              }
            },
          )
        }

        if (current.imageSummaries.isNotEmpty()) {
          item {
            Text(
              text = "Image breakdown (${current.imageSummaries.size})",
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.padding(top = 6.dp),
            )
          }
          items(current.imageSummaries, key = { it.outputPath }) { summary ->
            ImageCompressionBreakdownRow(
              summary = summary,
              onOpen = {
                FileStorageHelper.openFile(context, summary.outputPath, summary.mimeType)
                  .onFailure { err ->
                    scope.launch { snackbarHostState.showSnackbar(err.message ?: "Unable to open image.") }
                  }
              },
            )
          }
        }

        item {
          Spacer(modifier = Modifier.height(4.dp))
          TextButton(
            onClick = onBackToHome,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("back_to_home_button"),
          ) {
            Icon(imageVector = Icons.Rounded.Home, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Back to Home", style = MaterialTheme.typography.titleSmall)
          }
          Spacer(modifier = Modifier.height(12.dp))
        }
      }
    }
  }
}

@Composable
private fun SuccessHeaderCard(
  result: ToolResult,
  checkScale: Float,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth().padding(20.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier
            .size(72.dp)
            .scale(checkScale)
            .clip(CircleShape)
            .background(SuccessGreen.copy(alpha = 0.14f)),
      ) {
        Icon(
          imageVector = Icons.Rounded.CheckCircle,
          contentDescription = "Success",
          tint = SuccessGreen,
          modifier = Modifier.size(46.dp),
        )
      }

      Spacer(modifier = Modifier.height(14.dp))
      Text(
        text = if (result.keptOriginalPdf) "Original PDF Kept" else "Done!",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(6.dp))

      val titleText =
        if (result.allOutputFiles.size > 1) "${result.allOutputFiles.size} compressed JPEG images"
        else result.primaryFileName
      Text(
        text = titleText,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      Spacer(modifier = Modifier.height(14.dp))

      val savedPercent = FormatUtils.calculatePercentSaved(result.originalTotalBytes, result.outputTotalBytes)
      Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (result.originalTotalBytes > 0L) {
          MetricColumn(
            label = "Original",
            value = FormatUtils.formatFileSize(result.originalTotalBytes),
          )
        }
        MetricColumn(
          label = "Output size",
          value = FormatUtils.formatFileSize(result.outputTotalBytes),
        )
        if (result.imageSummaries.isNotEmpty() || (!result.keptOriginalPdf && result.pageCount > 0 && result.toolTypeName == "PDF Compressor")) {
          MetricColumn(
            label = "Saved",
            value = if (savedPercent >= 0) "$savedPercent%" else "0%",
            highlight = savedPercent > 0,
          )
        } else if (result.pageCount > 0) {
          MetricColumn(
            label = "Pages",
            value = result.pageCount.toString(),
          )
        }
      }
    }
  }
}

@Composable
private fun MetricColumn(
  label: String,
  value: String,
  highlight: Boolean = false,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      color = if (highlight) SuccessGreen else MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
private fun ActionButtonsSection(
  isMultiOutput: Boolean,
  isSaved: Boolean,
  onOpen: () -> Unit,
  onShare: () -> Unit,
  onSaveToDownloads: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Button(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.weight(1f).height(52.dp).testTag("result_open_button"),
      ) {
        Icon(imageVector = Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isMultiOutput) "Open First" else "Open")
      }
      FilledTonalButton(
        onClick = onShare,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.weight(1f).height(52.dp).testTag("result_share_button"),
      ) {
        Icon(imageVector = Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isMultiOutput) "Share All" else "Share")
      }
    }

    OutlinedButton(
      onClick = onSaveToDownloads,
      shape = RoundedCornerShape(16.dp),
      colors =
        if (isSaved) {
          ButtonDefaults.outlinedButtonColors(contentColor = SuccessGreen)
        } else {
          ButtonDefaults.outlinedButtonColors()
        },
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("result_save_downloads_button"),
    ) {
      Icon(
        imageVector = if (isSaved) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = if (isSaved) "Saved to Downloads/PDFNest" else "Save to Downloads",
        style = MaterialTheme.typography.titleSmall,
      )
    }
  }
}

@Composable
private fun ImageCompressionBreakdownRow(
  summary: CompressedImageSummary,
  onOpen: () -> Unit,
) {
  val saved = FormatUtils.calculatePercentSaved(summary.originalBytes, summary.compressedBytes)
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = summary.fileName,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "${FormatUtils.formatFileSize(summary.originalBytes)} → ${FormatUtils.formatFileSize(summary.compressedBytes)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Surface(
        shape = RoundedCornerShape(8.dp),
        color =
          if (saved > 0) SuccessGreen.copy(alpha = 0.14f)
          else MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Text(
          text = if (saved > 0) "-$saved%" else "0%",
          style = MaterialTheme.typography.labelMedium,
          color = if (saved > 0) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
      }
    }
  }
}
