package com.pdfnest.tools.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pdfnest.tools.model.ToolType
import com.pdfnest.tools.ui.screens.AboutScreen
import com.pdfnest.tools.ui.screens.HomeScreen
import com.pdfnest.tools.ui.screens.ImageCompressorScreen
import com.pdfnest.tools.ui.screens.JpgToPdfScreen
import com.pdfnest.tools.ui.screens.MergePdfScreen
import com.pdfnest.tools.ui.screens.PdfCompressorScreen
import com.pdfnest.tools.ui.screens.ResultScreen
import com.pdfnest.tools.viewmodel.HomeViewModel
import com.pdfnest.tools.viewmodel.ImageCompressorViewModel
import com.pdfnest.tools.viewmodel.JpgToPdfViewModel
import com.pdfnest.tools.viewmodel.MergePdfViewModel
import com.pdfnest.tools.viewmodel.PdfCompressorViewModel
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object ImageCompressorRoute

@Serializable
object JpgToPdfRoute

@Serializable
object PdfCompressorRoute

@Serializable
object MergePdfRoute

@Serializable
object ResultRoute

@Serializable
object AboutRoute

@Composable
fun PdfNestNavHost() {
  val navController = rememberNavController()

  NavHost(
    navController = navController,
    startDestination = HomeRoute,
    enterTransition = {
      slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(260),
      ) + fadeIn(animationSpec = tween(220))
    },
    exitTransition = {
      fadeOut(animationSpec = tween(180))
    },
    popEnterTransition = {
      fadeIn(animationSpec = tween(220))
    },
    popExitTransition = {
      slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(240),
      ) + fadeOut(animationSpec = tween(180))
    },
  ) {
    composable<HomeRoute> {
      val homeVm: HomeViewModel = viewModel()
      HomeScreen(
        viewModel = homeVm,
        onSelectTool = { tool ->
          when (tool) {
            ToolType.IMAGE_COMPRESSOR -> navController.navigate(ImageCompressorRoute)
            ToolType.JPG_TO_PDF -> navController.navigate(JpgToPdfRoute)
            ToolType.PDF_COMPRESSOR -> navController.navigate(PdfCompressorRoute)
            ToolType.MERGE_PDF -> navController.navigate(MergePdfRoute)
          }
        },
        onOpenAbout = { navController.navigate(AboutRoute) },
      )
    }

    composable<ImageCompressorRoute> {
      val vm: ImageCompressorViewModel = viewModel()
      ImageCompressorScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        onCompleted = {
          navController.navigate(ResultRoute) {
            popUpTo<HomeRoute> { inclusive = false }
          }
        },
      )
    }

    composable<JpgToPdfRoute> {
      val vm: JpgToPdfViewModel = viewModel()
      JpgToPdfScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        onCompleted = {
          navController.navigate(ResultRoute) {
            popUpTo<HomeRoute> { inclusive = false }
          }
        },
      )
    }

    composable<PdfCompressorRoute> {
      val vm: PdfCompressorViewModel = viewModel()
      PdfCompressorScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        onCompleted = {
          navController.navigate(ResultRoute) {
            popUpTo<HomeRoute> { inclusive = false }
          }
        },
      )
    }

    composable<MergePdfRoute> {
      val vm: MergePdfViewModel = viewModel()
      MergePdfScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        onCompleted = {
          navController.navigate(ResultRoute) {
            popUpTo<HomeRoute> { inclusive = false }
          }
        },
      )
    }

    composable<ResultRoute> {
      ResultScreen(
        onBackToHome = {
          navController.popBackStack<HomeRoute>(inclusive = false)
        },
      )
    }

    composable<AboutRoute> {
      AboutScreen(onBack = { navController.popBackStack() })
    }
  }
}
