package com.pdfnest.tools.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.pdfnest.tools.ui.components.AdaptivePageContainer
import com.pdfnest.tools.ui.components.PdfNestTopBar
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val storeUrl = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      PdfNestTopBar(title = "About", onBack = onBack)
    },
  ) { innerPadding ->
    AdaptivePageContainer(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier
              .size(84.dp)
              .clip(RoundedCornerShape(22.dp))
              .background(MaterialTheme.colorScheme.primary),
        ) {
          Icon(
            imageVector = Icons.Rounded.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(44.dp),
          )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Version ${BuildConfig.VERSION_NAME}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp).size(22.dp),
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
              text = "All processing happens on your device. Your files are never uploaded.",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
          onClick = {
            try {
              val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl)).apply {
                  addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
              context.startActivity(intent)
            } catch (_: Throwable) {
              scope.launch {
                snackbarHostState.showSnackbar("Store listing link: $storeUrl")
              }
            }
          },
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("rate_us_button"),
        ) {
          Icon(imageVector = Icons.Rounded.Star, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Rate us", style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
          onClick = {
            try {
              val shareText = "Try PDF Nest — fast, offline PDF & image tools on your phone: $storeUrl"
              val intent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, shareText)
                }
              context.startActivity(
                Intent.createChooser(intent, "Share PDF Nest").apply {
                  addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
              )
            } catch (_: Throwable) {
              scope.launch {
                snackbarHostState.showSnackbar("Unable to open share sheet.")
              }
            }
          },
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("share_app_button"),
        ) {
          Icon(imageVector = Icons.Rounded.Share, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Share app", style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
          text = "Offline tools powered by Android PDF & Apache PDFBox.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
