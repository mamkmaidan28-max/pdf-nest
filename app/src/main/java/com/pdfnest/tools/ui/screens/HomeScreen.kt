package com.pdfnest.tools.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.ToolAmberAccent
import com.example.ui.theme.ToolBlueAccent
import com.example.ui.theme.ToolEmeraldAccent
import com.example.ui.theme.ToolTealAccent
import com.pdfnest.tools.model.RecentFileEntry
import com.pdfnest.tools.model.ToolType
import com.pdfnest.tools.util.FormatUtils
import com.pdfnest.tools.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

private data class HomeToolItem(
  val type: ToolType,
  val title: String,
  val description: String,
  val icon: ImageVector,
  val accent: Color,
  val testTag: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: HomeViewModel,
  onSelectTool: (ToolType) -> Unit,
  onOpenAbout: () -> Unit,
) {
  val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    viewModel.refreshRecentFiles()
  }

  val tools = remember {
    listOf(
      HomeToolItem(
        type = ToolType.IMAGE_COMPRESSOR,
        title = "Image Compressor",
        description = "Shrink JPG, PNG & WEBP sizes",
        icon = Icons.Rounded.PhotoSizeSelectLarge,
        accent = ToolTealAccent,
        testTag = "tool_image_compressor",
      ),
      HomeToolItem(
        type = ToolType.JPG_TO_PDF,
        title = "JPG to PDF",
        description = "Turn photos into a neat PDF",
        icon = Icons.Rounded.PictureAsPdf,
        accent = ToolBlueAccent,
        testTag = "tool_jpg_to_pdf",
      ),
      HomeToolItem(
        type = ToolType.PDF_COMPRESSOR,
        title = "PDF Compressor",
        description = "Make large PDFs smaller",
        icon = Icons.Rounded.Compress,
        accent = ToolAmberAccent,
        testTag = "tool_pdf_compressor",
      ),
      HomeToolItem(
        type = ToolType.MERGE_PDF,
        title = "Merge PDF",
        description = "Combine PDFs in any order",
        icon = Icons.Rounded.LibraryAdd,
        accent = ToolEmeraldAccent,
        testTag = "tool_merge_pdf",
      ),
    )
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = stringResource(R.string.app_tagline),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        actions = {
          IconButton(
            onClick = onOpenAbout,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("about_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = "About PDF Nest",
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
          ),
      )
    },
  ) { innerPadding ->
    Box(
      contentAlignment = Alignment.TopCenter,
      modifier = Modifier.fillMaxSize().padding(innerPadding),
    ) {
      LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
      ) {
        item {
          OfflineTrustBanner()
        }

        item {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              ToolGridCard(
                tool = tools[0],
                onClick = { onSelectTool(tools[0].type) },
                modifier = Modifier.weight(1f),
              )
              ToolGridCard(
                tool = tools[1],
                onClick = { onSelectTool(tools[1].type) },
                modifier = Modifier.weight(1f),
              )
            }
            Row(
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              ToolGridCard(
                tool = tools[2],
                onClick = { onSelectTool(tools[2].type) },
                modifier = Modifier.weight(1f),
              )
              ToolGridCard(
                tool = tools[3],
                onClick = { onSelectTool(tools[3].type) },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        item {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          ) {
            Text(
              text = "Recent files",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onBackground,
            )
            if (recentFiles.isNotEmpty()) {
              Text(
                text = "Last ${recentFiles.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        if (recentFiles.isEmpty()) {
          item {
            EmptyRecentFilesCard()
          }
        } else {
          items(recentFiles, key = { it.id }) { entry ->
            RecentFileRow(
              entry = entry,
              onOpen = {
                viewModel.openRecent(entry).onFailure { err ->
                  scope.launch {
                    snackbarHostState.showSnackbar(err.message ?: "Unable to open file.")
                  }
                }
              },
              onShare = {
                viewModel.shareRecent(entry).onFailure { err ->
                  scope.launch {
                    snackbarHostState.showSnackbar(err.message ?: "Unable to share file.")
                  }
                }
              },
            )
          }
        }

        item {
          Spacer(modifier = Modifier.height(8.dp))
        }
      }
    }
  }
}

@Composable
private fun OfflineTrustBanner() {
  val primary = MaterialTheme.colorScheme.primary
  val container = MaterialTheme.colorScheme.primaryContainer
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Color.Transparent,
    border = BorderStroke(1.dp, primary.copy(alpha = 0.18f)),
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(
          Brush.horizontalGradient(
            listOf(
              container.copy(alpha = 0.72f),
              container.copy(alpha = 0.35f),
            )
          )
        ),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(primary.copy(alpha = 0.14f)),
      ) {
        Icon(
          imageVector = Icons.Rounded.Lock,
          contentDescription = null,
          tint = primary,
          modifier = Modifier.size(18.dp),
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Private & 100% Offline",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
          text = "Your files stay on your device at all times.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
      }
    }
  }
}

@Composable
private fun ToolGridCard(
  tool: HomeToolItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    modifier =
      modifier
        .height(156.dp)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .testTag(tool.testTag),
  ) {
    Column(
      verticalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tool.accent.copy(alpha = 0.14f)),
      ) {
        Icon(
          imageVector = tool.icon,
          contentDescription = null,
          tint = tool.accent,
          modifier = Modifier.size(26.dp),
        )
      }
      Column {
        Text(
          text = tool.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = tool.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun EmptyRecentFilesCard() {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(18.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Icon(
          imageVector = Icons.Rounded.FolderOpen,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column {
        Text(
          text = "No recent files yet",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "Pick a tool above to create your first file.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun RecentFileRow(
  entry: RecentFileEntry,
  onOpen: () -> Unit,
  onShare: () -> Unit,
) {
  val isPdf = entry.mimeType == "application/pdf"
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onOpen)
        .testTag("recent_file_${entry.id}"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
              if (isPdf) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.secondaryContainer
            ),
      ) {
        Icon(
          imageVector = if (isPdf) Icons.Rounded.Description else Icons.Rounded.Image,
          contentDescription = null,
          tint =
            if (isPdf) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(22.dp),
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = entry.fileName,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "${entry.toolName} • ${FormatUtils.formatFileSize(entry.sizeBytes)} • ${FormatUtils.formatShortDateTime(entry.createdAtMillis)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(
        onClick = onShare,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("share_recent_${entry.id}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Share,
          contentDescription = "Share ${entry.fileName}",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}
