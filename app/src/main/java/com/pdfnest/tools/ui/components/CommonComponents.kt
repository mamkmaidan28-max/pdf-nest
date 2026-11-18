package com.pdfnest.tools.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdfnest.tools.model.CompressionLevel
import com.pdfnest.tools.model.ProcessingProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfNestTopBar(
  title: String,
  onBack: (() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  CenterAlignedTopAppBar(
    title = {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    navigationIcon = {
      if (onBack != null) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("top_bar_back"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Navigate back",
          )
        }
      }
    },
    actions = actions,
    colors =
      TopAppBarDefaults.centerAlignedTopAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
      ),
  )
}

@Composable
fun AdaptivePageContainer(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(
    modifier = modifier.fillMaxWidth(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
      content = content,
    )
  }
}

@Composable
fun ToolStepHeader(
  stepNumber: Int,
  title: String,
  subtitle: String? = null,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.fillMaxWidth(),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier
          .size(28.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary),
    ) {
      Text(
        text = stepNumber.toString(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun CompressionLevelSelector(
  selected: CompressionLevel,
  onSelect: (CompressionLevel) -> Unit,
  enabled: Boolean,
  modifier: Modifier = Modifier,
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    CompressionLevel.entries.forEach { level ->
      val isSelected = level == selected
      Surface(
        shape = RoundedCornerShape(16.dp),
        color =
          if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
          else MaterialTheme.colorScheme.surface,
        border =
          BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color =
              if (isSelected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outlineVariant,
          ),
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onSelect(level) }
            .testTag("compression_level_${level.name.lowercase()}"),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${level.label} compression",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
              color =
                if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = level.subtitle,
              style = MaterialTheme.typography.bodySmall,
              color =
                if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (isSelected) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = "Selected",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
fun ProcessingCard(
  progress: ProcessingProgress,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val animatedFraction by animateFloatAsState(
    targetValue = progress.fraction,
    label = "processing_progress",
  )

  Card(
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
      ),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = progress.message.ifBlank { "Working..." },
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = "${progress.percent}%",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }
      Spacer(modifier = Modifier.height(14.dp))
      LinearProgressIndicator(
        progress = { animatedFraction },
        strokeCap = StrokeCap.Round,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(8.dp),
      )
      Spacer(modifier = Modifier.height(16.dp))
      OutlinedButton(
        onClick = onCancel,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("cancel_job_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Cancel")
      }
    }
  }
}

@Composable
fun PrimaryActionButton(
  text: String,
  enabled: Boolean,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(16.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      ),
    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
    modifier = modifier.fillMaxWidth().height(56.dp).testTag(testTag),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.titleMedium,
    )
  }
}

@Composable
fun StepTransitionSection(
  visible: Boolean,
  content: @Composable () -> Unit,
) {
  AnimatedVisibility(visible = visible) {
    content()
  }
}
