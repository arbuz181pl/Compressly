package com.compressly.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.compressly.ui.theme.AppShapes
import com.compressly.CompresslyApp
import com.compressly.ui.screens.EditorScreen
import com.compressly.ui.screens.HomeScreen
import com.compressly.ui.screens.PickerScreen
import com.compressly.ui.screens.QueueScreen
import com.compressly.ui.theme.CompresslyTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompresslyRoot() {
    val context = LocalContext.current
    val thumbnails = (context.applicationContext as CompresslyApp).thumbnails
    val viewModel: CompresslyViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val videoPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onVideoPermissionResult,
    )

    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        // Progress is still visible in-app; notifications are a convenience, not a gate.
        onResult = { granted -> viewModel.onNotificationPermissionResult(granted) },
    )

    // Ask for notifications once the video permission is already in hand, so the system does
    // not stack two dialogs on first launch.
    LaunchedEffect(state.hasVideoPermission) {
        if (state.hasVideoPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val requestVideoPermission = {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        videoPermission.launch(permission)
    }

    if (!state.hasVideoPermission) {
        PermissionGate(onRequest = requestVideoPermission)
        return
    }

    CompresslyTheme {
        when (state.screen) {
            Screen.HOME -> HomeScreen(
                state = state,
                thumbnails = thumbnails,
                onPickVideos = viewModel::openPicker,
                onOpenQueue = viewModel::goQueue,
                onClearFinished = viewModel::clearFinished,
                onDismissJob = viewModel::dismissJob,
            )

            Screen.PICKER -> PickerScreen(
                state = state,
                thumbnails = thumbnails,
                onBack = viewModel::back,
                onToggleSelection = viewModel::toggleSelection,
                onOpenEditor = viewModel::openEditor,
                onQueueSelected = viewModel::enqueueSelection,
                onClearSelection = viewModel::clearSelection,
            )

            Screen.EDITOR -> EditorScreen(
                state = state,
                thumbnails = thumbnails,
                onBack = viewModel::back,
                onTrimChange = viewModel::setTrim,
                onScrub = viewModel::setPlayhead,
                onPreset = viewModel::applyPreset,
                onSettingsChange = viewModel::updateSettings,
                onNameChange = viewModel::setOutputName,
                onTogglePlay = viewModel::togglePlay,
                onPlayingChange = viewModel::setPlaying,
                onPreviewFailed = viewModel::onPreviewFailed,
                onQueue = viewModel::enqueueEditorJob,
            )

            Screen.QUEUE -> QueueScreen(
                state = state,
                thumbnails = thumbnails,
                onBack = viewModel::back,
                onDismiss = viewModel::dismissJob,
                onClearFinished = viewModel::clearFinished,
                onShare = { job ->
                    job.result?.let { result -> shareVideo(context, viewModel.shareIntent(result.uri)) }
                },
                onDelete = { job ->
                    job.result?.let { result ->
                        viewModel.deleteResult(result.uri)
                        viewModel.dismissJob(job.id)
                    }
                },
                onDeleteMany = viewModel::deleteResults,
            )
        }
    }
}

/** Shown until the user grants access to the video library. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    CompresslyTheme {
        Scaffold(
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onRequest,
                    shape = AppShapes.pill,
                    expanded = true,
                    icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null) },
                    text = { Text("Allow access") },
                )
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = "Compressly needs access to your videos to find and shrink them",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

private fun shareVideo(context: Context, intent: Intent) {
    val chooser = Intent.createChooser(intent, "Share video")
    // The chooser is launched from a non-Activity context in some entry paths.
    if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
