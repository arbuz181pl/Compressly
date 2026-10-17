package com.compressly.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.compressly.data.JobRepository
import com.compressly.data.MediaStoreWriter
import com.compressly.data.VideoLibrary
import com.compressly.data.VideoProbe
import com.compressly.domain.CompressJob
import com.compressly.domain.CompressionPreset
import com.compressly.domain.EncoderSettings
import com.compressly.domain.JobStatus
import com.compressly.domain.OutputNaming
import com.compressly.domain.SizeEstimator
import com.compressly.domain.TrimRange
import com.compressly.domain.VideoInfo
import com.compressly.domain.presetMatching
import com.compressly.engine.FFmpegEngine
import com.compressly.service.CompressionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CompresslyViewModel(app: Application) : AndroidViewModel(app) {

    private val library = VideoLibrary(app)
    private val probe = VideoProbe(app)
    private val writer = MediaStoreWriter(app)
    private val engine = FFmpegEngine()

    private val _state = MutableStateFlow(CompresslyUiState())
    val state: StateFlow<CompresslyUiState> = _state.asStateFlow()

    init {
        // The permission may already be granted (granted earlier, or from Settings), so the
        // initial state has to come from an actual check rather than waiting for a callback.
        val granted = hasVideoPermissionNow()
        _state.update { it.copy(hasVideoPermission = granted) }
        if (granted) refreshLibrary()

        viewModelScope.launch {
            JobRepository.jobs.collect { jobs ->
                _state.update { it.copy(jobs = jobs) }
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(engineVersion = engine.version()) }
            writer.clearWorkDir()
        }
    }

    private fun hasVideoPermissionNow(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(getApplication(), permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    // region Navigation

    fun goHome() = _state.update { it.copy(screen = Screen.HOME) }

    fun goQueue() = _state.update { it.copy(screen = Screen.QUEUE) }

    fun openPicker() = _state.update {
        it.copy(screen = Screen.PICKER, selection = emptySet())
    }

    fun back() = _state.update {
        when (it.screen) {
            Screen.EDITOR -> it.copy(screen = Screen.PICKER, editorVideo = null)
            Screen.PICKER -> it.copy(screen = Screen.HOME, selection = emptySet())
            Screen.QUEUE -> it.copy(screen = Screen.HOME)
            Screen.HOME -> it
        }
    }

    // endregion

    // region Permissions & library

    fun onVideoPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasVideoPermission = granted) }
        if (granted) refreshLibrary()
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _state.update { it.copy(notificationsBlocked = !granted) }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _state.update { it.copy(libraryLoading = true) }
            val videos = runCatching { library.listVideos() }.getOrDefault(emptyList())
            _state.update { it.copy(library = videos, libraryLoading = false) }
        }
    }

    fun toggleSelection(uri: Uri) = _state.update { current ->
        val next = if (uri in current.selection) current.selection - uri else current.selection + uri
        current.copy(selection = next)
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    // endregion

    // region Editor

    fun openEditor(video: VideoInfo) {
        _state.update {
            it.copy(
                screen = Screen.EDITOR,
                editorVideo = video,
                trim = TrimRange.full(video.durationMs),
                playheadMs = 0L,
                isPlaying = false,
                settings = CompressionPreset.BALANCED.settingsFor(video),
                activePreset = CompressionPreset.BALANCED,
                editorFrameRate = 30.0,
                outputName = OutputNaming.defaultFor(video.displayName) + "-compressed",
            )
        }
        recomputeEstimate()
        viewModelScope.launch {
            val details = runCatching { probe.probe(video.uri) }.getOrNull()
            if (details != null) {
                _state.update { current ->
                    val corrected = if (current.editorVideo?.uri != video.uri) {
                        current
                    } else {
                        current.copy(
                            editorFrameRate = details.frameRate,
                            // Trust the probe over MediaStore for the audio flag.
                            editorVideo = current.editorVideo.copy(hasAudio = details.hasAudio),
                        )
                    }
                    corrected
                }
                recomputeEstimate()
            }
        }
    }

    fun setTrim(range: TrimRange) {
        _state.update { current ->
            val video = current.editorVideo ?: return@update current
            val clamped = range.clampedTo(video.durationMs)
            current.copy(
                trim = clamped,
                // Keep the playhead where it was scrubbed to, just pulled inside the new
                // range, instead of snapping it to the in-point on every handle move.
                playheadMs = current.playheadMs.coerceIn(clamped.startMs, clamped.endMs),
                isPlaying = false,
            )
        }
        recomputeEstimate()
    }

    fun setPlayhead(ms: Long) {
        _state.update { current ->
            val clamped = ms.coerceIn(current.trim.startMs, current.trim.endMs)
            current.copy(playheadMs = clamped)
        }
    }

    fun setPlaying(playing: Boolean) = _state.update { it.copy(isPlaying = playing) }

    /**
     * Play/pause. Starting from the out-point (or outside the range) rewinds to the in-point,
     * so the button always does something visible.
     */
    fun togglePlay() = _state.update { current ->
        if (current.isPlaying) {
            current.copy(isPlaying = false)
        } else {
            val restart = current.playheadMs >= current.trim.endMs ||
                current.playheadMs < current.trim.startMs
            current.copy(
                isPlaying = true,
                playheadMs = if (restart) current.trim.startMs else current.playheadMs,
            )
        }
    }

    fun onPreviewFailed() = _state.update { it.copy(previewFailed = true, isPlaying = false) }

fun applyPreset(preset: CompressionPreset) {
        val video = _state.value.editorVideo ?: return
        if (preset == CompressionPreset.CUSTOM) {
            // Custom is a state, not a recipe: mark it without touching the user's settings.
            _state.update { it.copy(activePreset = CompressionPreset.CUSTOM) }
            return
        }
        _state.update {
            it.copy(
                settings = preset.settingsFor(video),
                activePreset = preset,
            )
        }
        recomputeEstimate()
    }

    /**
     * Any manual edit re-derives the active preset: the edit can still coincide with one of
     * the presets (so the chip snaps back), otherwise the settings are genuinely custom.
     */
    fun updateSettings(transform: (EncoderSettings) -> EncoderSettings) {
        _state.update { current ->
            val video = current.editorVideo ?: return@update current
            val next = transform(current.settings)
            current.copy(
                settings = next,
                activePreset = presetMatching(video, next),
            )
        }
        recomputeEstimate()
    }

    fun setOutputName(raw: String) = _state.update { it.copy(outputName = raw) }

    fun enqueueEditorJob() {
        val current = _state.value
        val video = current.editorVideo ?: return
        if (current.editorDurationMs <= 0) return

        JobRepository.enqueue(
            video = video,
            trim = current.trim,
            settings = current.settings,
            trimmedSourceBytes = SizeEstimator.trimmedSourceBytes(video, current.trim),
            outputName = OutputNaming.sanitize(current.outputName, video.displayName),
        )
        CompressionService.start(getApplication())
        _state.update { it.copy(screen = Screen.QUEUE) }
    }

    // endregion

    // region Batch

    /** Queues every selected clip at its full length using the current editor settings. */
    fun enqueueSelection() {
        val current = _state.value
        val videos = current.selectedVideos
        if (videos.isEmpty()) return

        val settings = current.settings
        videos.forEach { video ->
            val trim = TrimRange.full(video.durationMs)
            JobRepository.enqueue(
                video = video,
                trim = trim,
                settings = settings,
                trimmedSourceBytes = video.sizeBytes,
            )
        }
        CompressionService.start(getApplication())
        _state.update { it.copy(screen = Screen.QUEUE, selection = emptySet()) }
    }

// endregion

    // region Results

    fun dismissJob(id: String) = JobRepository.dismiss(id)

    fun clearFinished() = JobRepository.clearFinished()

    fun deleteResult(uri: Uri) {
        viewModelScope.launch { writer.deleteOutput(uri) }
    }

    /**
     * Deletes the output files for [jobs] and removes their rows from the queue.
     *
     * Entries that never produced a file (failed, cancelled) are simply dropped from the
     * list. Rows are only dropped once their file deletion has been attempted, so the UI
     * never claims a file is gone while it is still on disk.
     */
    fun deleteResults(jobs: List<CompressJob>) {
        if (jobs.isEmpty()) return
        viewModelScope.launch {
            jobs.forEach { job ->
                job.result?.let { writer.deleteOutput(it.uri) }
                JobRepository.dismiss(job.id)
            }
        }
    }

    fun shareIntent(uri: Uri, mimeType: String = "video/mp4"): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    // endregion

    private fun recomputeEstimate() {
        val current = _state.value
        val video = current.editorVideo ?: return
        val bytes = SizeEstimator.estimateBytes(
            source = video,
            trim = current.trim,
            settings = current.settings,
            sourceFps = current.editorFrameRate,
        )
        _state.update { it.copy(estimatedBytes = bytes) }
    }

    /** Estimated size for a whole-clip job, used by the batch summary. */
    fun estimateFor(video: VideoInfo, settings: EncoderSettings): Long =
        SizeEstimator.estimateBytes(
            source = video,
            trim = TrimRange.full(video.durationMs),
            settings = settings,
        )

    val activeJobId: String?
        get() = _state.value.jobs.firstOrNull { it.status == JobStatus.RUNNING }?.id
}
